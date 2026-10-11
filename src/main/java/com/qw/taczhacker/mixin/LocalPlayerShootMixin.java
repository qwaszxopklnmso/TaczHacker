package com.qw.taczhacker.mixin;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.aim.AimHandler;
import com.qw.taczhacker.feature.aim.AimHandler.AimAngles;
import com.qw.taczhacker.feature.fakerot.FakeRotationHandler;
import com.qw.taczhacker.network.ServerDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
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
 *
 * 2026-10-11 追加修复（功能2 低头转圈把子弹带到脚底）：
 * 上面这条「先发旋转包」的逻辑原先只在这一 tick **选中了目标**时才跑，
 * 于是功能1 关掉、或者功能1 开着但范围内没目标时直接 return，
 * 服务器手里仍是低头转圈同步过去的「低头 90°」→ 子弹打地板，爆炸弹在脚底炸开。
 * 现在无论有没有目标，只要转圈开着，开火前都会把**真实**朝向发给服务器
 * （见 {@code FakeRotationHandler#revealRealRotationForShot}）。
 */
@Mixin(value = com.tacz.guns.client.gameplay.LocalPlayerShoot.class, remap = false)
public class LocalPlayerShootMixin {

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
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        // ========== 功能1：选目标 + 算瞄准角 ==========
        AimAngles angles = null;
        if (AimHandler.isActive() && isGunEquipped(player)) {
            LivingEntity target = AimHandler.selectTarget(player);
            if (target != null) {
                angles = AimHandler.calculateAimAngles(player, target);
            }
        }

        if (angles != null) {
            // 始终设置 pendingAngles，供 TimelessBulletEntityMixin 消费
            // 无论服务端是否有本mod，客户端选中的目标角度都应该传递给子弹方向修改器
            // 使用 volatile 单字段而非队列，避免 Forge 类加载机制导致队列实例不一致
            AimHandler.pendingAngles = angles;

            // 只有服务端「装了本mod」且「服务端自己开启了子弹自瞄」时，才跳过转视角。
            // 只看前者会导致：服务端默认配置 aim.enabled=false，客户端不再发旋转包，
            // 服务端也不改子弹方向 → 功能1 静默失效。
            if (ServerDetector.isServerAimAvailable()) {
                return;
            }

            // 开火瞬间临时暂停低头转圈：保证这一 tick 发给服务器的是瞄准旋转，
            // 而不是被 LocalPlayerMixin 替换掉的假旋转（功能1 > 功能2）
            FakeRotationHandler.suspendFor(FakeRotationHandler.SUSPEND_TICKS);

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
            return;
        }

        // ========== 功能2 修复：没在自瞄也要让服务端看到真实朝向 ==========
        // 2026-10-11 修「低头转圈时子弹打地板、爆炸弹把自己炸飞」：
        // 老实现只在这一 tick 选中了目标时才处理旋转，于是「功能1 关掉」或者
        // 「功能1 开着但范围内没目标」时直接 return，服务器手里仍是低头转圈同步过去的
        // 「低头 90°」→ Tacz 服务端按这个朝向生成子弹（见文件头的说明：shoot 包不带方向）
        // → 子弹直接打脚底。这里补上：只要转圈开着，开火前先把真实朝向发给服务器，
        // 并暂停转圈几 tick（TCP 保序，本包在 shoot 包之前到）。
        if (FakeRotationHandler.shouldRevealRealRotationOnShoot()) {
            FakeRotationHandler.revealRealRotationForShot(player);
        }
    }

    /**
     * 「仅持枪时生效」检查：主手或副手拿着 Tacz 枪械
     */
    private boolean isGunEquipped(LocalPlayer player) {
        if (!HackConfig.aimRequireGunEquipped) {
            return true;
        }
        if (player.getMainHandItem().getItem() instanceof com.tacz.guns.item.ModernKineticGunItem) {
            return true;
        }
        return player.getOffhandItem().getItem() instanceof com.tacz.guns.item.ModernKineticGunItem;
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