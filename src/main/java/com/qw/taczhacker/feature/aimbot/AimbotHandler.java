package com.qw.taczhacker.feature.aimbot;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.config.HackConfig.AimPosition;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 功能3：视角锁定自瞄（Aimbot / Lock-On）
 *
 * 表现：按住指定按键时，玩家视角自动平滑锁定到附近目标头部/身体。
 * 零额外发包——本地旋转经原版移动包自然上传给服务器。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class AimbotHandler {

    private static boolean active = false;
    private static LivingEntity currentTarget = null;

    /**
     * 每 tick 检测按键状态并执行视角锁定
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 检查全局开关和功能开关
        if (!HackConfig.globalEnabled || !HackConfig.aimbotEnabled) {
            if (active) {
                active = false;
                currentTarget = null;
            }
            return;
        }

        // 检测按键
        boolean keyDown = KeyBindings.AIMBOT_KEY.isDown();

        if (!keyDown) {
            if (active) {
                active = false;
                currentTarget = null;
            }
            return;
        }

        active = true;

        // 收集候选目标
        List<LivingEntity> targets = findTargets(player, mc);

        if (targets.isEmpty()) {
            currentTarget = null;
            return;
        }

        // 选最优目标（最接近准星方向）
        LivingEntity bestTarget = selectBestTarget(player, targets);

        if (bestTarget == null) {
            currentTarget = null;
            return;
        }

        currentTarget = bestTarget;

        // 计算瞄准角度
        Vec3 targetPos = getAimPosition(bestTarget);
        Vec3 playerEye = player.getEyePosition(1.0f);

        double dx = targetPos.x - playerEye.x;
        double dy = targetPos.y - playerEye.y;
        double dz = targetPos.z - playerEye.z;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.01) return;

        // 计算 yaw 和 pitch
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        // 平滑插值
        float smoothness = (float) HackConfig.aimbotSmoothness;
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        // 处理 yaw 绕圈
        float yawDiff = targetYaw - currentYaw;
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        float lerpFactor = 1.0f - smoothness;
        if (lerpFactor < 0.01f) lerpFactor = 0.01f;

        float newYaw = currentYaw + yawDiff * lerpFactor;
        float newPitch = currentPitch + (targetPitch - currentPitch) * lerpFactor;

        player.setYRot(newYaw);
        player.setXRot(newPitch);
    }

    /**
     * 收集附近所有符合条件的 LivingEntity
     */
    private static List<LivingEntity> findTargets(LocalPlayer player, Minecraft mc) {
        List<LivingEntity> targets = new ArrayList<>();
        double range = HackConfig.aimbotRange;

        AABB searchBox = player.getBoundingBox().inflate(range);

        if (mc.level != null) {
            for (var entity : mc.level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
                if (entity == player) continue;
                if (!entity.isAlive()) continue;
                if (entity instanceof Player && entity.isSpectator()) continue;
                // 跳过创造模式玩家
                if (entity instanceof Player targetPlayer && targetPlayer.isCreative()) continue;

                // 视线检查
                if (!HackConfig.aimbotPassThroughWalls) {
                    if (mc.level != null && mc.level.clip(new ClipContext(
                            player.getEyePosition(1.0f),
                            entity.getEyePosition(1.0f),
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            player
                    )).getType() != HitResult.Type.MISS) {
                        continue;
                    }
                }

                // 距离检查
                if (player.distanceTo(entity) > range) continue;

                targets.add(entity);
            }
        }

        return targets;
    }

    /**
     * 选最优目标（最接近准星方向）
     */
    private static LivingEntity selectBestTarget(LocalPlayer player, List<LivingEntity> targets) {
        Vec3 lookVec = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition(1.0f);

        return targets.stream()
                .min(Comparator.comparingDouble(target -> {
                    Vec3 toTarget = target.getEyePosition(1.0f).subtract(eyePos).normalize();
                    double dot = lookVec.dot(toTarget);
                    // 限制范围，防止选中背后的目标
                    if (dot < 0.3) return Double.MAX_VALUE;
                    return 1.0 - dot; // 角度越小，值越小
                }))
                .orElse(null);
    }

    /**
     * 获取瞄准位置（头部或身体）
     */
    private static Vec3 getAimPosition(LivingEntity target) {
        Vec3 eyePos = target.getEyePosition(1.0f);
        if (HackConfig.aimbotAimPosition == AimPosition.HEAD) {
            // 头部：眼睛位置 + 小偏移
            return eyePos.add(0, 0.1, 0);
        } else {
            // 身体：脚部位置 + 身体高度一半
            return target.position().add(0, target.getBbHeight() * 0.5, 0);
        }
    }

    /**
     * 获取当前锁定目标（供其他模块使用）
     */
    public static LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    /**
     * 是否正在锁定
     */
    public static boolean isActive() {
        return active;
    }
}