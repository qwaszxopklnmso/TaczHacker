package com.qw.taczhacker.mixin;

import com.mojang.logging.LogUtils;
import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.aim.AimHandler;
import com.qw.taczhacker.feature.aim.AimHandler.AimAngles;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注意：此 Mixin 在 mixins.json 中配置为 "mixins"（通用列表），
 * 会在服务端和客户端都加载。绝对不能引用任何客户端专属类（如 Minecraft、AimbotHandler）！
 * 如需引用客户端类，必须使用反射 + Class.forName() + catch(Throwable)。
 */

/**
 * EntityKineticBullet Mixin
 *
 * 功能1 双端增强——真·自瞄 + 真·追踪弹 + 穿墙子弹
 *
 * 通过 Mixin 修改 Tacz 子弹实体 EntityKineticBullet 的行为。
 * 注意：此 Mixin 需要在服务端和客户端都安装本 Mod 才能生效。
 *
 * 1. 真·自瞄（true auto-aim）：在 shoot() 中直接覆盖子弹方向，无视散布
 * 2. 真·追踪弹（homing bullet）：在 onBulletTick() 中持续将子弹 deltaMovement 转向目标
 * 3. 穿墙子弹（bullet penetration）：在 onHitBlock() 中跳过方块碰撞处理
 *
 * 反编译确认的方法签名：
 *   - public void shoot(double, double, float, Vector2d)                                     // 子弹初始方向设置
 *   - protected void onBulletTick()                                                          // 子弹每 tick 运动逻辑
 *   - protected void onHitEntity(TacHitResult, Vec3, Vec3)                                   // 击中实体
 *   - protected void onHitBlock(BlockHitResult, Vec3, Vec3)                                  // 击中方块
 */
@Mixin(value = com.tacz.guns.entity.EntityKineticBullet.class, remap = false)
public class TimelessBulletEntityMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        LOGGER.info("[TaczHacker][子弹Mixin] TimelessBulletEntityMixin 类已加载！");
    }

    // ============================================================
    // 真·自瞄：在 shoot() 中直接覆盖子弹方向
    // ============================================================

    /**
     * 注入到 shoot(double, double, float, Vector2d) 方法末尾（RETURN）。
     *
     * 反编译确认 shoot() 逻辑：
     *   1. 从 (spread.x, spread.y, 8.0) 构建 Vector3d
     *   2. 按 yaw 旋转（绕X轴）
     *   3. 按 -pitch 旋转（绕Y轴）
     *   4. 归一化 → 乘以 velocity → 设成 deltaMovement
     *
     * 我们的注入在 RETURN 时运行，此时 Tacz 已经设置了带散布的方向。
     * 我们读取 pendingAngles，用目标方向覆盖 deltaMovement，实现"零散布"自瞄。
     *
     * 如果 pendingAngles 为 null（客户端选目标失败），
     * 直接在服务器线程选目标作为 fallback（无视玩家视角和墙体遮挡）。
     *
     * 方法签名：shoot(double yaw, double pitch, float velocity, Vector2d spread)
     * 描述符：shoot(DDFLorg/joml/Vector2d;)V
     */
    @Inject(
            method = "shoot(DDFLorg/joml/Vector2d;)V",
            at = @At("RETURN"),
            remap = false
    )
    private void onShoot(CallbackInfo ci) {
        // 检查是否启用了真·自瞄
        if (!HackConfig.globalEnabled || !HackConfig.aimEnabled) {
            return;
        }

        // 获取当前子弹实体
        Projectile self = (Projectile) (Object) this;

        // 获取当前速度（保持子弹速度不变，只改方向）
        Vec3 currentMotion = self.getDeltaMovement();
        double speed = currentMotion.length();
        if (speed < 0.01) {
            LOGGER.info("[TaczHacker][真·自瞄] 速度太小，跳过");
            return;
        }

        // 方案1：读取 pendingAngles（由 LocalPlayerShootMixin 在客户端设置）
        // 使用 volatile 单字段而非队列，避免 Forge 类加载机制导致队列实例不一致
        // 注意：此方法在服务端和客户端都会调用。
        // - 集成服务器（单机/局域网）：pendingAngles 在同一 JVM 中由客户端线程设置，可读取
        // - 专用服务器（Forge 服务器）：pendingAngles 在不同 JVM，始终为 null，走 fallback
        // 不能使用 Minecraft.getInstance() 检查所有者，因为服务端不存在 Minecraft 类！
        AimAngles angles = AimHandler.pendingAngles;
        if (angles != null) {
            // 消费 pendingAngles，防止重复使用
            AimHandler.pendingAngles = null;

            // 从 yaw/pitch 计算方向向量（无客户端依赖）
            overrideDirectionFromAngles(self, speed, angles);
            LOGGER.info("[TaczHacker][真·自瞄] 使用 pendingAngles 覆盖方向，角度=({}, {})",
                    String.format("%.2f", angles.yaw), String.format("%.2f", angles.pitch));
            return;
        }

        // 方案2（fallback）：pendingAngles 为 null 或不是本地玩家子弹
        // 直接在服务器线程选目标，无视玩家视角和墙体
        if (self.getOwner() instanceof LivingEntity shooter) {
            LivingEntity target = AimHandler.selectTargetServerSide(shooter);
            if (target != null && target.isAlive()) {
                // 计算方向：从子弹位置指向目标
                Vec3 toTarget = target.getEyePosition(1.0f).subtract(self.position()).normalize();
                self.setDeltaMovement(toTarget.scale(speed));
                LOGGER.info("[TaczHacker][真·自瞄] 服务器端 fallback：目标={}, 距离={}",
                        target.getName().getString(), String.format("%.1f", shooter.distanceTo(target)));
            }
        }
    }

    /**
     * 从瞄准角度覆盖子弹方向
     */
    private static void overrideDirectionFromAngles(Projectile bullet, double speed, AimAngles angles) {
        float yawRad = (float) Math.toRadians(angles.yaw);
        float pitchRad = (float) Math.toRadians(angles.pitch);
        float dirX = -Mth.sin(yawRad) * Mth.cos(pitchRad);
        float dirY = -Mth.sin(pitchRad);
        float dirZ = Mth.cos(yawRad) * Mth.cos(pitchRad);
        bullet.setDeltaMovement(new Vec3(dirX, dirY, dirZ).scale(speed));
    }

    // ============================================================
    // 真·自瞄 + 真·追踪弹：在 onBulletTick() 中每 tick 转向目标
    // ============================================================

    /**
     * 注入到 onBulletTick() 方法开头，用于真·自瞄和追踪弹逻辑。
     *
     * 反编译确认 onBulletTick 流程：
     *   1. 检查 level.isClientSide()，如果是客户端则跳过
     *   2. 记录 oldPos = this.position()
     *   3. 计算 newPos = oldPos + this.getDeltaMovement()
     *   4. 做 rayTraceBlocks（方块碰撞检测）
     *   5. 如果命中方块，截断 newPos 为碰撞点
     *   6. 用 oldPos 和 newPos 做实体检测（EntityUtil.findEntityOnPath）
     *   7. 如果命中实体，调用 onHitEntity() 处理伤害
     *   8. 如果命中方块但没命中实体，调用 onHitBlock()
     *
     * 我们的注入在 HEAD 运行，此时 super.tick() 已经移动了子弹。
     * 我们在此处将子弹的 deltaMovement 转向目标方向，确保实体检测能找到目标。
     *
     * 关键修复：即使子弹因为穿墙、散布等原因偏离了目标，
     * 每 tick 强制转向也能保证子弹最终命中目标，解决"视觉命中但无伤害"的问题。
     */
    @Inject(
            method = "onBulletTick",
            at = @At("HEAD"),
            cancellable = false,
            remap = false
    )
    private void onBulletTick(CallbackInfo ci) {
        // 检查总开关
        if (!HackConfig.globalEnabled || !HackConfig.aimEnabled) {
            return;
        }

        // 获取当前子弹实体
        Projectile self = (Projectile) (Object) this;

        // 获取子弹的 shooter
        if (!(self.getOwner() instanceof LivingEntity shooter)) {
            return;
        }

        // 检查子弹是否还有效
        if (!self.isAlive()) {
            return;
        }

        // 记录当前子弹位置和速度
        Vec3 bulletPos = self.position();
        Vec3 currentMotion = self.getDeltaMovement();
        double speed = currentMotion.length();
        if (speed < 0.01) {
            return;
        }

        // ----- 选择目标 -----
        LivingEntity target = null;

        // 优先使用 Aimbot（功能3）的锁定目标
        try {
            Class<?> aimbotClass = Class.forName("com.qw.taczhacker.feature.aimbot.AimbotHandler");
            java.lang.reflect.Method getTarget = aimbotClass.getMethod("getCurrentTarget");
            Object result = getTarget.invoke(null);
            if (result instanceof LivingEntity) {
                target = (LivingEntity) result;
            }
        } catch (Throwable t) {
            // AimbotHandler 在服务端不存在，忽略
        }

        if (target == null) {
            // 使用子弹位置为中心的搜索框（而不是以 shooter 为中心）
            // 这样子弹飞远后不会因为 shooter 距离太远而丢失目标
            target = AimHandler.selectTargetNear(bulletPos, self.level(), shooter);
        }

        if (target == null || !target.isAlive()) {
            return;
        }

        // ----- 计算目标方向和距离 -----
        //
        // 爆头率优化：瞄准头顶中心（碰撞箱顶部略微偏下），确保子弹从任意角度
        // 接近目标时，射线进入碰撞箱的入口点都在头部区域（y+1.62~y+1.80）。
        //
        // 原理：Tacz 的实体检测（EntityUtil.findEntityOnPath）对射线进行碰撞箱
        // 相交检测，返回第一个交点作为命中位置。为了确保该交点在头部区域，
        // 需要让子弹指向头部区域内的某个点，这样射线从上方/侧面/下方进入碰撞箱时，
        // 入口点都在头部区域。
        //
        // 为什么统一瞄准头顶（maxY - 0.04 = y+1.76）而非头部中心（y+1.70）：
        // - 子弹从上方射入时，瞄准头顶让射线从顶部表面进入，入口点在头顶（y+1.80）
        //   视为爆头；瞄准头部中心时，入口点不变但射线路径更长，可能被判定为身体
        // - 子弹从侧面射入时，头顶 vs 头部中心对入口点影响不大，都在头部区域
        // - 子弹从下方射入时（极少见），瞄准头顶让射线从下巴/颈部进入，仍算头部
        //
        // 注意：玩家碰撞箱 0.6x1.8，眼部在 y+1.62，头顶在 y+1.80。
        // 头部区域 y+1.62~y+1.80，取 maxY - 0.04 = y+1.76 确保：
        // 1. 在头部区域内，不会因太靠近顶部边界而被判定为"未命中头部"
        // 2. 留出 0.04 的容差，防止因浮点精度导致射线刚好擦过顶部表面
        //
        AABB bb = target.getBoundingBox();
        Vec3 targetPos = new Vec3(bb.getCenter().x, bb.maxY - 0.04, bb.getCenter().z);
        Vec3 toTarget = targetPos.subtract(bulletPos);
        double distToTarget = toTarget.length();

        // ----- 子弹转向逻辑 -----
        //
        // 核心原则：确保子弹一直指向目标，让 onBulletTick 的实体检测（EntityUtil.findEntityOnPath）
        // 能检测到目标碰撞箱，从而造成伤害。
        //
        // 修复"子弹在敌人周围来回窜"问题：
        // 之前当 distToTarget < 1.5 时，代码把速度提升到 closeSpeed * 1.5，
        // 导致子弹加速冲过头 → 调头 → 又冲过头 → 造成"来回窜"死循环。
        // 同时实体检测射线（oldPos→newPos）因为位置在目标两侧快速变化而错过碰撞箱。
        //
        // 修复方案：
        // 1. 移除近距离加速逻辑，保持正常速度，防止冲过头
        // 2. 近距离（< 1.5）直接瞄准目标碰撞箱中心，确保射线穿过碰撞箱
        // 3. 原速度保持不变，让子弹自然穿过目标
        //
        // 修复"抬头飞行时脚下生物自瞄打不到"问题：
        // 当玩家抬头时，子弹被射向天空，目标在脚下。子弹需要在第一 tick 急转弯（180°）。
        // 检查 currentDir · targetDir 的 dot product，如果为负说明子弹正在远离目标，
        // 此时必须直接指向目标（急转弯），不能使用平滑插值。
        //
        Vec3 targetDir = toTarget.normalize();
        Vec3 currentDir = currentMotion.normalize();
        double dotProduct = currentDir.dot(targetDir); // >0=朝目标前进, <0=远离目标

        if (distToTarget < 1.5) {
            // 子弹非常接近目标：直接指向目标碰撞箱中心，保持原速，绝不加速
            // 让子弹自然穿过目标碰撞箱，确保实体检测射线能命中
            self.setDeltaMovement(targetDir.scale(speed));
            return;
        }

        // 直接转向目标方向（保证伤害判定）
        boolean isHoming = HackConfig.aimSinglePlayerHomingBullet;
        if (isHoming && distToTarget < 10.0 && dotProduct > 0.3) {
            // 追踪弹模式：子弹正在朝目标方向前进，30% 平滑插值
            Vec3 newMotion = currentMotion.add(
                    targetDir.scale(speed).subtract(currentMotion).scale(0.3)
            );
            self.setDeltaMovement(newMotion);
        } else {
            // 真·自瞄模式：直接指向目标
            // 当 dotProduct <= 0.3 时（子弹远离目标或横向偏移），也走此分支实现急转弯
            self.setDeltaMovement(targetDir.scale(speed));
        }
    }

    // ============================================================
    // 穿墙子弹：在 onHitBlock() 中跳过方块碰撞
    // ============================================================

    /**
     * 注入到 onHitBlock() 方法开头，用于穿墙子弹。
     *
     * 当启用了穿墙子弹时，跳过方块碰撞处理，并根据碰撞法线方向
     * 把子弹位置往前推 1.5 格，让它"穿过"墙壁继续飞行。
     *
     * 关键：不仅要推子弹位置，还要恢复 deltaMovement（因为 onBulletTick
     * 的 rayTrace 截断后，后续的碰撞检测已经影响了子弹的运动）。
     * 我们取子弹的 deltaMovement 长度，沿原方向继续飞行。
     *
     * 方法签名：onHitBlock(BlockHitResult, Vec3, Vec3)
     *   - 参数1：BlockHitResult — 方块碰撞结果
     *   - 参数2：Vec3 — 碰撞点位置
     *   - 参数3：Vec3 — 碰撞法线方向
     *
     * 注意：即使跳过方块碰撞，子弹仍会因为 tick 中的速度衰减和距离限制
     * 而最终消失，不会无限飞行。
     */
    @Inject(
            method = "onHitBlock",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onHitBlock(BlockHitResult hitResult, Vec3 vec3_1, Vec3 vec3_2, CallbackInfo ci) {
        // 检查是否启用了穿墙子弹（仅单机有效）
        if (HackConfig.globalEnabled && HackConfig.aimSinglePlayerBulletPenetration) {
            Projectile self = (Projectile) (Object) this;
            // 根据碰撞法线方向把子弹位置往前推 1.5 格
            Vec3 normal = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal());
            Vec3 newPos = self.position().add(normal.scale(1.5));
            self.setPos(newPos);
            // 恢复 deltaMovement 沿原方向继续飞行（保持速度不变）
            Vec3 currentMotion = self.getDeltaMovement();
            double speed = currentMotion.length();
            if (speed < 0.01) {
                // 速度太小，沿碰撞法线方向给一个默认速度
                self.setDeltaMovement(normal.scale(1.0));
            }
            LOGGER.info("[TaczHacker][穿墙] 子弹穿过墙壁，新位置={}, 速度={}",
                    newPos, String.format("%.2f", speed));
            // 跳过方块碰撞，让子弹继续飞行
            ci.cancel();
        }
    }
}