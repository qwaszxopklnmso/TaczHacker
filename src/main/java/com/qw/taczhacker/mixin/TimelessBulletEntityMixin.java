package com.qw.taczhacker.mixin;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.aim.AimHandler;
import com.qw.taczhacker.feature.aim.AimHandler.AimAngles;
import com.qw.taczhacker.network.ClientFeatureFlags;
import com.tacz.guns.util.block.BlockRayTrace;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

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

    /**
     * AimbotHandler#getCurrentTarget 的反射句柄缓存。
     *
     * AimbotHandler 是客户端类，服务端不存在，只能用反射调用；
     * 缓存在静态字段里，避免每颗子弹每 tick 都做一次 Class.forName + getMethod。
     */
    private static Method aimbotGetTargetMethod = null;

    /** 是否已经尝试过解析反射句柄（失败后不再重试） */
    private static boolean aimbotMethodResolved = false;

    /**
     * 获取当前锁定目标（客户端 AimbotHandler），服务端返回 null
     */
    private static LivingEntity getAimbotTarget() {
        if (!aimbotMethodResolved) {
            aimbotMethodResolved = true;
            try {
                Class<?> aimbotClass = Class.forName("com.qw.taczhacker.feature.aimbot.AimbotHandler");
                aimbotGetTargetMethod = aimbotClass.getMethod("getCurrentTarget");
            } catch (Throwable t) {
                // 服务端没有这个类，忽略
                aimbotGetTargetMethod = null;
            }
        }
        if (aimbotGetTargetMethod == null) {
            return null;
        }
        try {
            Object result = aimbotGetTargetMethod.invoke(null);
            return result instanceof LivingEntity living ? living : null;
        } catch (Throwable t) {
            return null;
        }
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
            return;
        }

        // 方案1：读取 pendingAngles（由 LocalPlayerShootMixin 在客户端设置）
        // 使用 volatile 单字段而非队列，避免 Forge 类加载机制导致队列实例不一致
        // 注意：此方法在服务端和客户端都会调用。
        // - 集成服务器（单机/局域网）：pendingAngles 在同一 JVM 中由客户端线程设置，可读取
        // - 专用服务器（Forge 服务器）：pendingAngles 在不同 JVM，始终为 null，走方案2
        // 不能使用 Minecraft.getInstance() 检查所有者，因为服务端不存在 Minecraft 类！
        AimAngles angles = AimHandler.pendingAngles;
        if (angles != null) {
            // 消费 pendingAngles，防止重复使用
            AimHandler.pendingAngles = null;

            // 从 yaw/pitch 计算方向向量（无客户端依赖）
            overrideDirectionFromAngles(self, speed, angles);
            // 方向定下来之后，立刻为这颗子弹探测一次目标（之后全程不再重新找）
            taczhacker$probeTarget(self);
            return;
        }

        // ============================================================
        // 方案2（2026-10-11 新增）：专用服务器的「真·自瞄」= 零散布方向覆盖
        // ============================================================
        //
        // 以前这里走的是「服务端自己挑半径内最近的生物，夹角在锥角内才修正」，
        // 实测在专用服务器上根本锁不住：挑的目标跟玩家准星无关，转圈开着时子弹先朝地下
        // （夹角≈90°）一次都不修正，转圈关掉也是拽它自己挑的那个"最近生物"。
        //
        // 现在改用服务端**自己手里那份朝向**：客户端的 LocalPlayerShootMixin 在 shoot 之前
        // 已经发过 ServerboundMovePlayerPacket.Rot（瞄准角），TCP 保序保证它先到，
        // 所以此刻 ServerPlayer.getLookAngle() 就是客户端算好的瞄准方向。
        // 用它重建 deltaMovement = 把 Tacz 刚加上去的**散布去掉** → 联机也零散布。
        //
        // 与门（作者要求）：服务端 aim.enabled 开着（本方法开头的判断）**且**
        // 该玩家客户端报过"我在用功能1"（ClientFeatureFlags，来自握手/开关变化时的上报）。
        // 这样原版客户端不会因为你服务器的配置而被改弹道。
        if (self.getOwner() instanceof LivingEntity shooter && ClientFeatureFlags.wantsAim(shooter)) {
            Vec3 look = shooter.getLookAngle();
            if (look.lengthSqr() > 1.0E-6) {
                self.setDeltaMovement(look.normalize().scale(speed));
            }
        }

        // 方向定下来之后，立刻为这颗子弹探测一次目标（之后全程不再重新找）
        taczhacker$probeTarget(self);
    }

    // ============================================================
    // 目标探测：一颗子弹只探一次（作者要求，2026-10-17）
    // ============================================================

    /** 这颗子弹是否已经探测过目标（探过就不再探，哪怕当时没找到） */
    @Unique
    private boolean taczhacker$targetProbed = false;

    /** 探测到的目标实体 id；-1 = 探测过但没目标（这颗子弹之后一直直飞） */
    @Unique
    private int taczhacker$targetId = -1;

    /**
     * 在开火那一刻（子弹刚生成、方向已定）为这颗子弹挑**一次**目标
     *
     * ⚠️ 2026-10-17 作者要求：**只在第一次寻找生物，不持续寻找**。
     * 旧实现在 {@code onBulletTick} 里每 tick 重新 {@code selectTargetNear}，而那个方法
     * 既不看向、半径又是 {@code aim.lockRadius}（作者配置 128 格），于是开追踪弹后
     * 子弹每 tick 都在追「离它最近的任何活物」（牛、背后的僵尸、隔墙的怪）→ 乱飘不打人，
     * 而且把开火时算好的零散布方向整个顶掉，看起来像「真·自瞄也坏了」。
     *
     * 现在的探测顺序（作者指定）：
     * <ol>
     *   <li>按住功能3（视角锁定）时锁定的那个目标 —— 它就是"准心靠着的"</li>
     *   <li>准心/弹道方向上、锥角（{@code aim.coneAngle}）范围内最近的目标</li>
     *   <li>范围内最近的目标（不看方向，兜底）</li>
     * </ol>
     * 探到就记 id，探不到记 -1，**两种情况都置 probed**，之后这颗子弹再也不找目标。
     *
     * 排查时可在末尾临时打开日志（每颗子弹一行，别长期开着 —— 历史上刷过 2 万行）。
     */
    @Unique
    private void taczhacker$probeTarget(Projectile bullet) {
        if (taczhacker$targetProbed) {
            return;
        }
        taczhacker$targetProbed = true;
        taczhacker$targetId = -1;

        if (!HackConfig.globalEnabled || !HackConfig.aimEnabled) {
            return;
        }
        if (!(bullet.getOwner() instanceof LivingEntity shooter)) {
            return;
        }

        // 1) 功能3 按住锁定中的目标
        LivingEntity target = getAimbotTarget();
        if (target != null && !target.isAlive()) {
            target = null;
        }

        // 2) 准心/弹道锥角内最近的
        if (target == null) {
            Vec3 dir = bullet.getDeltaMovement();
            target = AimHandler.selectTargetNear(bullet.position(), bullet.level(), shooter,
                    dir, HackConfig.aimConeAngle);
        }

        // 3) 范围内最近的（不看方向）
        if (target == null) {
            target = AimHandler.selectTargetNear(bullet.position(), bullet.level(), shooter);
        }

        if (target != null) {
            taczhacker$targetId = target.getId();
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

        // 每 tick 掰弹道属于「追踪弹」的行为，没开就别插手。
        // 否则子弹飞出枪口后，路过任何生物身边都会被 selectTargetNear 拽过去 ——
        // 那个方法是以子弹位置为圆心、按距离挑最近的，完全不看玩家的准星，
        // 连发时表现为整条弹链乱飞。
        if (!HackConfig.aimSinglePlayerHomingBullet) {
            return;
        }

        // 获取当前子弹实体
        Projectile self = (Projectile) (Object) this;

        // 获取子弹的 shooter
        if (!(self.getOwner() instanceof LivingEntity shooter)) {
            return;
        }

        // 与门（作者要求）：服务端配置开着（上面两项）**且** 该玩家客户端也开着追踪弹
        if (!ClientFeatureFlags.wantsHoming(shooter)) {
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

        // ----- 目标：只用「开火那一刻探测好的」那个，绝不每 tick 重新找 -----
        // 2026-10-17 作者要求：只在第一次寻找生物，不持续寻找。
        // 旧实现在这里每 tick 调一次 selectTargetNear（不看方向 + 半径 128 格），
        // 于是子弹会不断换目标追最近的活物 → 乱飘不打人。
        if (!taczhacker$targetProbed) {
            // 兜底：极少数子弹没走 shoot()（例如直接生成后手动设速度），第一 tick 补探一次
            taczhacker$probeTarget(self);
        }
        if (taczhacker$targetId == -1) {
            // 探测过、没目标 → 这颗子弹之后一直按开火方向直飞
            return;
        }
        net.minecraft.world.entity.Entity boundEntity = self.level().getEntity(taczhacker$targetId);
        if (!(boundEntity instanceof LivingEntity target) || !target.isAlive()) {
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
     * 当启用了穿墙子弹时，跳过方块碰撞处理，并沿**子弹运动方向**把子弹
     * 往前推 1.5 格，让它"穿过"墙壁继续飞行。
     *
     * ⚠️ 2026-10-12 修：旧实现用的是 {@code hitResult.getDirection()}（命中面的**外法线**）
     * 来推子弹 —— 那是指向子弹来向的一侧：往 +X 飞撞上墙，命中面是墙的西面，
     * getDirection() == WEST，normal = (-1,0,0)，于是 {@code pos + normal*1.5}
     * 把子弹**往后**推了 1.5 格（注释里写的"往前推"是反的）。
     * 时序上更糟：Tacz 的 {@code onBulletTick} 里 rayTrace 用的是**未移动**的起点，
     * 我们注入时 {@code self.position()} 仍是起点，位置前进是之后在 {@code super.tick()}
     * 里才做的，于是每 tick 的有效位移 = 起点 − 1.5·法线 + deltaMovement。
     * 设每 tick 位移 m、命中时距墙面 d（d ≤ m），要穿出去得满足 m > 1.5 + d；
     * Tacz 多数弹药每 tick 位移只有 1~2 格 → 多数情况下穿不过去，子弹贴着墙面反复触发
     * onHitBlock（这也是历史上"一次游戏刷了 2 万+ 行碰撞日志"的来源之一）。
     *
     * 现在改成用子弹自己的运动方向推进（拿不到运动才退回 −法线）。
     * 副作用：紧贴墙后的实体可能被跳过一个 tick —— 对作弊 mod 是可接受/甚至是想要的行为。
     *
     * 方法签名：onHitBlock(BlockHitResult, Vec3, Vec3)
     *   - 参数1：BlockHitResult — 方块碰撞结果
     *   - 参数2：Vec3 — 碰撞点位置
     *   - 参数3：Vec3 — 碰撞法线方向
     *
     * 注意：即使跳过方块碰撞，子弹仍会因为 tick 中的速度衰减和距离限制
     * 而最终消失，不会无限飞行。
     */
    /**
     * 穿墙子弹的**正主**：把 Tacz 那一 tick 的方块射线结果换成 MISS
     *
     * ⚠️ 2026-10-17 修「穿墙有效果但打不出伤害」：
     * Tacz 的 {@code onBulletTick} 里是这么写的（反编译确认）：
     * <pre>
     *   Vec3 startVec = this.position();
     *   Vec3 endVec = startVec.add(this.getDeltaMovement());
     *   BlockHitResult resultB = BlockRayTrace.rayTraceBlocks(level, clip);
     *   if (resultB.getType() != MISS) endVec = resultB.getLocation();   // ← 命中方块就把终点截断在墙面
     *   ... EntityUtil.findEntityOnPath(this, startVec, endVec)          // ← 实体判定只用被截断后的线段
     *   this.onHitBlock(resultB, startVec, endVec);
     * </pre>
     * 原来我们只在 {@code onHitBlock} 里「取消删子弹 + 把子弹位置往前推」，
     * 于是**穿过墙体那一段永远不在实体判定范围内** —— 子弹确实穿过去了，
     * 但墙后的目标从来没被检测到 → 「没伤害」。而且当时那 1.5 格瞬移还会把
     * 紧贴墙后的目标整段跳过去。
     *
     * 现在改成在源头处理：命中方块时直接返回一个 MISS，
     * 这样 {@code endVec} 不会被截断，{@code startVec→endVec} 完整覆盖穿过墙体的那一段，
     * 墙后的实体照样会被 {@code findEntityOnPath} 判定命中 —— 既不瞬移也不漏判定。
     *
     * {@code require = 0}：万一以后 Tacz 改了这里的调用点，退回下面
     * {@link #onHitBlock} 的旧兜底（取消删除 + 小幅前推），不会崩游戏。
     */
    @Redirect(
            method = "onBulletTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/tacz/guns/util/block/BlockRayTrace;rayTraceBlocks(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"
            ),
            require = 0,
            remap = false
    )
    private BlockHitResult taczhacker$skipBlockRayTrace(Level level, ClipContext context) {
        BlockHitResult real = BlockRayTrace.rayTraceBlocks(level, context);
        if (real.getType() == HitResult.Type.MISS) {
            return real;
        }
        if (!taczhacker$penetrationActive()) {
            return real;
        }
        // 交还完整线段给实体判定：Tacz 见到 MISS 就不会截断 endVec
        return BlockHitResult.miss(real.getLocation(), real.getDirection(), real.getBlockPos());
    }

    /** 穿墙子弹此刻是否应当生效（服务端配置 + 功能1 总开关 + 该玩家客户端的与门） */
    @Unique
    private boolean taczhacker$penetrationActive() {
        if (!HackConfig.globalEnabled || !HackConfig.aimEnabled
                || !HackConfig.aimSinglePlayerBulletPenetration) {
            return false;
        }
        Projectile self = (Projectile) (Object) this;
        return self.getOwner() instanceof LivingEntity shooter
                && ClientFeatureFlags.wantsPenetration(shooter);
    }

    /**
     * 兜底（仅在 {@link #taczhacker$skipBlockRayTrace} 的 Redirect 没匹配上时才会执行）
     *
     * 正常情况下射线已经被换成 MISS，Tacz 自己的 onHitBlock 会在第一行 return，
     * 而本注入也会因为 {@code Type.MISS} 直接退出 —— 两条路都不会瞬移子弹。
     */
    @Inject(
            method = "onHitBlock",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onHitBlock(BlockHitResult hitResult, Vec3 vec3_1, Vec3 vec3_2, CallbackInfo ci) {
        if (hitResult.getType() == HitResult.Type.MISS) {
            return;
        }
        if (!taczhacker$penetrationActive()) {
            return;
        }
        Projectile self = (Projectile) (Object) this;
        // 只往前推 0.1 格（旧版是 1.5 格，会把紧贴墙后的目标整段跳过 → 穿墙但打不到人）
        Vec3 motion = self.getDeltaMovement();
        Vec3 dir;
        double speed = motion.length();
        if (speed > 1.0E-4) {
            dir = motion.scale(1.0 / speed);
        } else {
            dir = Vec3.atLowerCornerOf(hitResult.getDirection().getNormal()).reverse();
            self.setDeltaMovement(dir.scale(1.0));
        }
        self.setPos(self.position().add(dir.scale(0.1)));
        ci.cancel();
    }
}