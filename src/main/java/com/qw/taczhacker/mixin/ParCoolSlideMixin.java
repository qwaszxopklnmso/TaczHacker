package com.qw.taczhacker.mixin;

import com.alrex.parcool.api.action.SynchronizedProperty;
import com.alrex.parcool.common.Parkourability;
import com.alrex.parcool.common.action.ParCoolActions;
import com.alrex.parcool.common.action.impl.Slide;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 功能7：ParCool 长滑铲 —— Slide（滑铲）MixIn（ParCool 4.0.1.0）
 *
 * ParCool 4.0 的 Slide#canContinue() 原生逻辑只有一条：
 *   return getDoingTick() < Slide.MAX_TICK(20);
 * 也就是最多滑 1 秒。要「一直滑」就得注入。
 *
 * 两个注入点：
 *   1. canContinue(HEAD)            —— 滑铲不自动结束；检测到取消输入时放行原生判定
 *   2. onStartInLocalClient(TAIL)   —— 重挂 BehaviorEnforcer 的移动强制：
 *        ParCool 原版在 onStartInLocalClient 里注册的 supplier 用
 *        Mth.lerp(getDoingTick() / 20.0, 1.0, 0.7) 算速度倍率，超过 20 tick 后
 *        会继续线性外推（t=3 时 0.1，t≈4 时变负数），所以强制持续必须自己重挂一份
 *        把进度钳在 20（倍率固定 0.7）；顺带在 supplier 里实时读视角方向实现「转向」。
 *        用 setMarkerEnforcingDeltaMovement 直接覆盖字段，不会叠加第二份强制。
 *
 * 「转向」不只是改速度方向 —— 还要把方向**写回** Slide 的同步属性
 * propertyMovingDirection：
 *   滑铲动画的资源包 `assets/parcool/mma/groups/slide_group.json` 里带了
 *   `parcool:builtin/slide_lock_body` 组件，它的实现是
 *   `ParCoolCodedAnimationComponents#lockBody(player, Slide#getSlidingDirection(), partial)`，
 *   也就是**用这个属性决定身体朝向**（旋转 BODY 部件到该方向的 yaw）。
 *   所以只改速度不改属性的话：人会朝视角方向滑，但 F5 里模型一直朝着起滑那一瞬间的方向
 *   —— 表现就是「朝向不对、移动方向正常」。
 *   改写用 SynchronizedProperty#set + setDirty(false)：set 会把属性标脏，
 *   而 ParCool 每 tick 用脏标记决定要不要发 ActionStateSetPacket，不清掉就变成每 tick 一个同步包
 *   （违反作者的零额外发包纪律）。代价是服务器与其它玩家手里的方向仍是起滑方向。
 *
 * 结束滑铲只有一种方式：松开后再按一次滑铲键（ParCool 的爬行键，默认 C）。
 * 跳跃键不参与 —— ParCool 4.0.1.0 原版滑铲期间本来就不屏蔽跳跃。
 *
 * 为什么取消是「放行原生判定」而不是直接返回 false：
 * 按键状态只有客户端有。Slide 的 ActionOption.triggeredSide 是默认的 CLIENT，
 * ActionProcessor 里 needSync = triggeredSide.isClient() && player.isLocalPlayer()，
 * 所以本注入**只在本地客户端执行**（服务端玩家 isLocalPlayer() 恒为 false），
 * 服务端永远跟随客户端同步过来的开始/结束状态，不会脱节。
 *
 * 此 Mixin 只在安装了 ParCool 4.0+ 时才会被应用（配置在 taczhacker.parcool.mixins.json，
 * required=false、defaultRequire=0），ParCool 不存在时不会加载本类，也不会崩溃。
 */
@Mixin(value = Slide.class, remap = false)
public abstract class ParCoolSlideMixin {

    /** ParCool 存滑动方向（含速度大小）的同步属性，滑铲动画的 lock_body 读的就是它 */
    @Shadow
    @Final
    private SynchronizedProperty<Vec3> propertyMovingDirection;

    @Inject(
            method = "canContinue",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void taczhacker$longSlide(CallbackInfoReturnable<Boolean> cir) {
        if (!ParCoolLongSlide.isEnabled()) return;

        // 松开后再按一次滑铲键：放行 ParCool 原生判定（getDoingTick() < 20 → 已经超了，正常结束）
        if (ParCoolLongSlide.isSlideKeyJustPressed()) return;

        ParCoolLongSlide.markSliding();
        cir.setReturnValue(true);
    }

    /**
     * 起滑后重挂移动强制：把速度衰减进度钳在 20 tick，并支持「方向跟随视角」
     */
    @Inject(
            method = "onStartInLocalClient",
            at = @At("TAIL"),
            remap = false
    )
    private void taczhacker$enforceSlideMovement(CallbackInfo ci) {
        if (!ParCoolLongSlide.isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        Parkourability parkourability = Parkourability.get(player);
        Slide slide = parkourability.get(ParCoolActions.SLIDE);
        if (slide == null) return;
        if (slide.getSlidingDirection() == null) return;

        parkourability.getBehaviorEnforcer().setMarkerEnforcingDeltaMovement(slide::isDoing, () -> {
            Player current = mc.player;
            if (current == null) return Vec3.ZERO;

            Vec3 direction = slide.getSlidingDirection();
            if (direction == null) return current.getDeltaMovement();

            if (ParCoolLongSlide.isSteerableSlide()) {
                direction = taczhacker$steerDirection(current, direction);
            }

            double progress = Math.min(slide.getDoingTick(), 20) / 20.0;
            double scale = 1.0 + progress * (0.7 - 1.0);
            // y 分量保留玩家当前垂直速度，所以滑铲中起跳不会被压掉
            return new Vec3(direction.x * scale, current.getDeltaMovement().y, direction.z * scale);
        });
    }

    /**
     * 「方向跟随视角」：把滑铲方向换成当前视角的水平方向（模长沿用原方向），
     * 并把这个方向写回 Slide 的同步属性，让滑铲动画的 lock_body 跟着转（F5 里身体朝向才对）。
     *
     * set() 之后必须立刻 setDirty(false)：ParCool 每 tick 检查脏标记决定要不要发
     * ActionStateSetPacket，不清掉就会每 tick 一个同步包。
     * 玩家没转视角时 set() 因为值没变不会置脏，所以清标记不会吞掉别人的脏数据
     * （滑铲开始的那一包在起滑那一 tick 就发完了，那时本 supplier 还没生效）。
     *
     * @return 实际使用的滑铲方向
     */
    private Vec3 taczhacker$steerDirection(Player player, Vec3 direction) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0, look.z);
        if (flat.lengthSqr() <= 1.0E-4) {
            return direction;
        }

        Vec3 live = flat.normalize().scale(direction.length());
        SynchronizedProperty<Vec3> property = this.propertyMovingDirection;
        if (property != null) {
            property.set(live);
            property.setDirty(false);
        }
        return live;
    }
}
