package com.qw.taczhacker.feature.aim;

import com.qw.taczhacker.config.HackConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 功能1：开火静默自瞄（Silent Aim）
 *
 * 表现：本地视角正常，开火瞬间服务器"看到"转身朝目标开了一枪，子弹命中。
 * 每次开火仅 1 个额外旋转包。
 *
 * 实现要点：
 * 1. 通过 Mixin 拦截 Tacz 客户端射击逻辑（GunFireController 或对应开火点）
 * 2. 开火前选择目标 → 计算提前量 → 发送假旋转包 → 让 Tacz 开火包按新旋转发出
 * 3. 不修改本地 player.xRot/yRot，本地视角无感
 *
 * 注意：具体 Tacz 类的 Mixin hook 点需要反编译 tacz-1.20.1-1.1.8-hotfix.jar 确认。
 * 当前实现提供目标选择 + 角度计算工具方法，Mixin 部分以 TODO 标记。
 */
public class AimHandler {

    /**
     * 待使用的瞄准角度。
     * 由 LocalPlayerShootMixin 在客户端射击时存入，
     * 由 TimelessBulletEntityMixin 在服务器处理射击时读取。
     * 使用后立即置为 null，防止重复使用。
     *
     * volatile 关键字确保在集成服务器（本地单机）中，
     * 客户端线程写入后，服务器线程能立即看到写入的值。
     *
     * 竞态说明：自动连发时，第2发的 onShootBefore 可能会覆盖 pendingAngles，
     * 导致第1发子弹拿到第2发的角度。但第2发子弹会走 fallback（selectTargetServerSide）
     * 选择最近目标，仍能命中。此方案比队列方案更可靠，因为 Forge 环境下
     * 静态字段在客户端和服务端之间共享，而 ConcurrentLinkedQueue 可能因类加载机制
     * 导致两边队列实例不同。
     */
    public static volatile AimAngles pendingAngles = null;

    /**
     * 原始 yaw（用于在 LocalPlayerShoot.shoot() 执行完后恢复玩家旋转）
     */
    public static float originalYaw = 0;

    /**
     * 原始 pitch（用于在 LocalPlayerShoot.shoot() 执行完后恢复玩家旋转）
     */
    public static float originalPitch = 0;

    /**
     * 是否已保存原始旋转。
     *
     * 用于修复玩家原始旋转恰好为 (0, 0) 时无法恢复的 bug。
     * 在 onShootBefore 中保存原始旋转时设为 true，
     * 在 onShootAfter 中恢复旋转后设为 false。
     */
    public static boolean hasOriginalRotation = false;

    /**
     * 选择最佳目标（供 Mixin 调用）
     *
     * 锥角检查使用水平方向角度，避免俯仰角（抬头/低头）导致目标被误过滤。
     * 例如：玩家抬头看天空，目标在脚下，3D角度≈180°，但水平角度可能很小。
     */
    public static LivingEntity selectTarget(LocalPlayer player) {
        if (!HackConfig.globalEnabled || !HackConfig.aimEnabled) return null;

        double radius = HackConfig.aimLockRadius;
        double coneAngle = Math.toRadians(HackConfig.aimConeAngle);
        double predictionFactor = HackConfig.aimPredictionFactor;

        AABB searchBox = player.getBoundingBox().inflate(radius);
        List<LivingEntity> candidates = new ArrayList<>();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;

        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();

        for (var entity : mc.level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity == player) continue;
            if (!entity.isAlive()) continue;
            // 跳过创造模式玩家
            if (entity instanceof net.minecraft.world.entity.player.Player targetPlayer && targetPlayer.isCreative()) continue;

            double dist = player.distanceTo(entity);
            if (dist > radius) continue;

            // 锥角检查（使用水平方向角度，避免俯仰角导致目标被误过滤）
            Vec3 toTarget = entity.getEyePosition(1.0f).subtract(eyePos);
            Vec3 toTargetHoriz = new Vec3(toTarget.x, 0, toTarget.z);
            Vec3 lookVecHoriz = new Vec3(lookVec.x, 0, lookVec.z);

            // 修复：当目标在玩家正上方/正下方（toTargetHoriz 为零向量），
            // 或玩家正看天/看地（lookVecHoriz 为零向量）时，
            // 水平方向角度计算会得到 90°，导致目标被误过滤。
            // 此时跳过锥角检查，允许目标被选中。
            if (toTargetHoriz.lengthSqr() > 1.0E-8 && lookVecHoriz.lengthSqr() > 1.0E-8) {
                double angle = Math.acos(Math.max(-1, Math.min(1,
                        lookVecHoriz.normalize().dot(toTargetHoriz.normalize()))));
                if (angle > coneAngle) continue;
            }
            // 如果任一水平向量为零向量（目标在正上方/正下方，或玩家正看天/看地），
            // 跳过锥角检查，不限制目标选择

            // 视线检查（可选）
            if (!HackConfig.aimPassThroughWalls) {
                if (mc.level.clip(new ClipContext(
                        eyePos, entity.getEyePosition(1.0f),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, player
                )).getType() != HitResult.Type.MISS) {
                    continue;
                }
            }

            candidates.add(entity);
        }

        if (candidates.isEmpty()) return null;

        // 选最近目标
        return candidates.stream()
                .min(Comparator.comparingDouble(player::distanceTo))
                .orElse(null);
    }

    /**
     * 计算瞄准角度（含提前量预测）
     *
     * @return AimAngles 包含 yaw 和 pitch，或 null 如果目标太近
     */
    public static AimAngles calculateAimAngles(LocalPlayer player, LivingEntity target) {
        Vec3 targetPos = target.getEyePosition(1.0f);
        Vec3 playerEye = player.getEyePosition(1.0f);

        double predictionFactor = HackConfig.aimPredictionFactor;

        if (predictionFactor > 0) {
            // 提前量预测：用目标速度 × 子弹飞行时间
            Vec3 targetVelocity = target.getDeltaMovement();
            double distance = playerEye.distanceTo(targetPos);

            // 子弹飞行时间 ≈ 距离 / 弹速（从配置读取，用户可根据当前枪械调整）
            double bulletSpeed = HackConfig.aimBulletSpeed;
            if (bulletSpeed <= 0) {
                // 弹速为 0 时跳过提前量预测，直瞄当前位置
                // 仅对目标位置不加预测偏移
            } else {
                double flightTime = Math.max(0.1, distance / bulletSpeed);
                // 预测位置 = 当前位置 + 速度 × 飞行时间 × 系数
                targetPos = targetPos.add(targetVelocity.scale(flightTime * predictionFactor));
            }
        }

        double dx = targetPos.x - playerEye.x;
        double dy = targetPos.y - playerEye.y;
        double dz = targetPos.z - playerEye.z;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.01) return null;

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        // 后坐力补偿：补偿枪械后坐力对弹道的影响
        // 正数 = 向下压枪（对抗上跳后坐力），负数 = 向上补偿
        pitch += (float) HackConfig.aimRecoilCompensation;

        return new AimAngles(yaw, pitch);
    }

    /**
     * 服务器端选目标（不依赖客户端 LocalPlayer）
     *
     * 在 TimelessBulletEntityMixin 中，当 pendingAngles 为 null 时，
     * 直接在服务器线程选目标，无视玩家视角方向和墙体遮挡。
     * 这是"双端/本地"模式下真·自瞄的最终保障。
     *
     * @param shooter 子弹的发射者（ServerPlayer 或任意 LivingEntity）
     * @return 最近的可攻击目标，或 null
     */
    public static LivingEntity selectTargetServerSide(LivingEntity shooter) {
        if (!HackConfig.globalEnabled || !HackConfig.aimEnabled) return null;

        double radius = HackConfig.aimLockRadius;
        AABB searchBox = shooter.getBoundingBox().inflate(radius);
        var level = shooter.level();
        if (level == null) return null;

        return selectTargetInBox(level, searchBox, shooter.position(), shooter, radius);
    }

    /**
     * 以任意位置为中心搜索目标（子弹追踪用）
     *
     * 搜索框以 center 为中心，inflate(radius) 确保 Y 范围跨度 = 2×radius。
     * 由于 onBulletTick 每 tick 调用，子弹在第一 tick 就在玩家位置，
     * 此时 bulletPos ≈ shooterPos，搜索框自然覆盖目标。
     *
     * @param level  世界
     * @param center 搜索中心（通常为子弹位置）
     * @param shooter 发射者（排除自身用）
     * @return 最近的可攻击目标，或 null
     */
    public static LivingEntity selectTargetNear(Vec3 center, Level level, LivingEntity shooter) {
        if (!HackConfig.globalEnabled || !HackConfig.aimEnabled) return null;

        double radius = HackConfig.aimLockRadius;
        AABB searchBox = new AABB(center, center).inflate(radius);
        return selectTargetInBox(level, searchBox, center, shooter, radius);
    }

    /**
     * 在指定 AABB 内搜索目标（内部共用方法）
     *
     * 距离计算以 center 为参考点，而不是 shooter。
     * 这样当 center 是子弹位置时，能正确找到子弹附近的目标。
     *
     * @param level  世界
     * @param searchBox 搜索范围
     * @param center 距离计算参考点（shooter 位置 或 子弹位置）
     * @param shooter 发射者（排除自身用）
     * @param radius 搜索半径
     * @return 最近的可攻击目标，或 null
     */
    private static LivingEntity selectTargetInBox(Level level, AABB searchBox, Vec3 center, LivingEntity shooter, double radius) {
        List<LivingEntity> candidates = new ArrayList<>();

        for (var entity : level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (entity == shooter) continue;
            if (!entity.isAlive()) continue;

            double dist = center.distanceTo(entity.position());
            if (dist > radius) continue;

            // 无视锥角（不检查玩家视角）
            // 无视视线遮挡（穿墙模式）
            candidates.add(entity);
        }

        if (candidates.isEmpty()) return null;
        // 按距离 center 的距离排序，选最近的目标
        return candidates.stream()
                .min(Comparator.comparingDouble(e -> center.distanceTo(e.position())))
                .orElse(null);
    }

    /**
     * 发送假旋转包（供 Mixin 调用）
     */
    public static void sendFakeRotationPacket(LocalPlayer player, float yaw, float pitch) {
        if (player.connection == null) return;
        player.connection.send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, player.onGround()
        ));
    }

    /**
     * 角度容器
     */
    public static class AimAngles {
        public final float yaw;
        public final float pitch;

        public AimAngles(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}