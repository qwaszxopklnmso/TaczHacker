package com.qw.taczhacker.mixin;

import com.mojang.logging.LogUtils;
import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.aim.AimHandler;
import com.qw.taczhacker.feature.aim.AimHandler.AimAngles;
import com.qw.taczhacker.network.ServerDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LocalPlayerShoot Mixin
 *
 * 功能1：开火静默自瞄（Silent Aim）——仅客户端转视角方案
 *
 * 策略说明：
 * 经反编译确认，ClientMessagePlayerShoot 数据包只包含 timestamp 和 chargeProgress，
 * 不包含旋转数据。服务器在收到数据包后通过
 *   IGunOperator.fromLivingEntity(sender).shoot(
 *       () -> sender.getYRot(), () -> sender.getXRot(),
 *       message.timestamp, message.chargeProgress)
 * 从 sender（ServerPlayer）实时读取旋转值。
 *
 * 因此，实现转视角自瞄的关键是：
 * 1. 在 LocalPlayerShoot.shoot() 执行前，发送 ServerboundMovePlayerPacket.Rot 假旋转包
 * 2. 这会更新服务器端的 ServerPlayer.rotationYaw/rotationPitch
 * 3. 当服务器处理 shoot 包时，读取到的是我们设定的瞄准角度
 * 4. 射击完成后，恢复本地玩家的旋转（不影响本地视角）
 *
 * 由于 TCP 保证了包顺序，假旋转包一定在 shoot 包之前到达服务器。
 */
@Mixin(value = com.tacz.guns.client.gameplay.LocalPlayerShoot.class, remap = false)
public class LocalPlayerShootMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        LOGGER.info("[TaczHacker][Mixin] LocalPlayerShootMixin 类已加载！");
    }

    /**
     * 注入到 shoot() 方法开头（HEAD）。
     *
     * 在 Tacz 的 LocalPlayerShoot.shoot() 执行前：
     * 1. 自动切换：如果服务端安装了本mod，由真·自瞄（子弹方向修改）处理，关闭转视角
     * 2. 选择目标
     * 3. 计算瞄准角度
     * 4. 发送假旋转包到服务器（更新 ServerPlayer 的旋转）
     * 5. 保存并修改本地玩家的旋转（确保本地计算一致）
     *
     * 自动切换逻辑：
     * - 单人游戏/局域网：服务端在同一进程，ServerDetector 返回 true → 跳过转视角，但设置 pendingAngles
     * - Forge 服务器（有本mod）：握手成功 → ServerDetector 返回 true → 跳过转视角，但设置 pendingAngles
     * - Forge 服务器（无本mod）：握手超时 → ServerDetector 返回 false → 使用转视角
     * - 纯客户端安装：握手失败 → ServerDetector 返回 false → 使用转视角
     *
     * 关键修复：即使服务端有本mod跳过了转视角，仍然要设置 pendingAngles，
     * 让 TimelessBulletEntityMixin 能消费客户端选中的目标角度，而不是走服务器 fallback 选最近目标。
     */
    @Inject(
            method = "shoot",
            at = @At("HEAD"),
            cancellable = false,
            remap = false
    )
    private void onShootBefore(CallbackInfoReturnable<?> cir) {
        // 检查总开关和功能1开关
        if (!HackConfig.globalEnabled || !HackConfig.aimEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        // 检查"仅持枪时生效"选项
        if (HackConfig.aimRequireGunEquipped) {
            // 检查主手或副手是否持枪
            // Tacz 的枪械在主手时，item 是 GunItem 类型
            boolean hasGun = false;
            var mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof com.tacz.guns.item.ModernKineticGunItem) {
                hasGun = true;
            }
            if (!hasGun) {
                var offHandItem = player.getOffhandItem();
                if (offHandItem.getItem() instanceof com.tacz.guns.item.ModernKineticGunItem) {
                    hasGun = true;
                }
            }
            if (!hasGun) {
                return;
            }
        }

        // ========== 目标选择（无论是否转视角，都需要） ==========
        // 选择目标
        LivingEntity target = AimHandler.selectTarget(player);
        if (target == null) {
            return;
        }

        // 计算瞄准角度（含提前量预测）
        AimAngles angles = AimHandler.calculateAimAngles(player, target);
        if (angles == null) {
            return;
        }

        // 始终设置 pendingAngles，供 TimelessBulletEntityMixin 消费
        // 无论服务端是否有本mod，客户端选中的目标角度都应该传递给子弹方向修改器
        // 使用 volatile 单字段而非队列，避免 Forge 类加载机制导致队列实例不一致
        AimHandler.pendingAngles = angles;

        // ========== 自动切换 ==========
        // 如果服务端安装了本mod，由真·自瞄（子弹方向修改）处理，关闭转视角
        // 单人游戏/局域网/Forge服务器（有本mod）都走这里
        if (ServerDetector.isServerHasTaczHacker()) {
            LOGGER.info("[TaczHacker][功能1-转视角] 服务端已安装 TaczHacker，由真·自瞄处理，跳过转视角（已设置 pendingAngles）");
            return;
        }

        LOGGER.info("[TaczHacker][功能1-转视角] onShootBefore 被调用：目标={}, 距离={}",
                target.getName().getString(), String.format("%.1f", player.distanceTo(target)));

        // 保存原始旋转（使用标记位确保即使旋转为 (0,0) 也能正确恢复）
        AimHandler.originalYaw = player.getYRot();
        AimHandler.originalPitch = player.getXRot();
        AimHandler.hasOriginalRotation = true;

        // 发送假旋转包到服务器（更新 ServerPlayer 的旋转）
        // 这个包一定在 shoot 包之前到达（TCP 保序）
        AimHandler.sendFakeRotationPacket(player, angles.yaw, angles.pitch);

        // 修改本地玩家的旋转（确保 LocalPlayerShoot.shoot() 内的本地计算使用正确角度）
        player.setYRot(angles.yaw);
        player.setXRot(angles.pitch);

        LOGGER.info("[TaczHacker][功能1-转视角] 已发送假旋转包并修改本地旋转：yaw={}, pitch={}",
                String.format("%.2f", angles.yaw), String.format("%.2f", angles.pitch));
    }

    /**
     * 注入到 shoot() 方法末尾（RETURN）。
     *
     * 在 Tacz 的 LocalPlayerShoot.shoot() 执行完后，恢复本地玩家的原始旋转。
     * 注意：不发送恢复旋转包，让下一个 tick 的常规旋转包覆盖服务器的旋转。
     * 这样可以避免额外的网络包。
     */
    @Inject(
            method = "shoot",
            at = @At("RETURN"),
            cancellable = false,
            remap = false
    )
    private void onShootAfter(CallbackInfoReturnable<?> cir) {
        // 只在设置了原始旋转标记的情况下恢复
        // 使用 hasOriginalRotation 标记位而非检查 yaw/pitch 是否为 0，
        // 修复了玩家原始旋转恰好为 (0, 0) 时无法恢复的 bug
        if (AimHandler.hasOriginalRotation) {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null) {
                // 恢复本地玩家的原始旋转
                player.setYRot(AimHandler.originalYaw);
                player.setXRot(AimHandler.originalPitch);
                LOGGER.info("[TaczHacker][功能1-转视角] 已恢复本地旋转：yaw={}, pitch={}",
                        String.format("%.2f", AimHandler.originalYaw),
                        String.format("%.2f", AimHandler.originalPitch));

                // 重置原始旋转标记
                AimHandler.hasOriginalRotation = false;
                AimHandler.originalYaw = 0;
                AimHandler.originalPitch = 0;

                // 注意：不发送恢复旋转包到服务器！
                // 下一个 tick 的常规 ServerboundMovePlayerPacket 会自动覆盖服务器的旋转值。
                // 对于单机/局域网，服务器会立即处理恢复包，但 shoot 包已经处理完了。
                // 对于联机服务器，假旋转包比 shoot 包先到，shoot 包处理完后，
                // 下一个 tick 的常规旋转包会自然恢复服务器的旋转。
            }
        }
    }
}