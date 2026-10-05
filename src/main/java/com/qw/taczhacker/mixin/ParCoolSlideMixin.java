package com.qw.taczhacker.mixin;

import com.alrex.parcool.common.Parkourability;
import com.alrex.parcool.common.action.ParCoolActions;
import com.alrex.parcool.common.action.impl.Slide;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
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
                Vec3 look = current.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0.0, look.z);
                if (flat.lengthSqr() > 1.0E-4) {
                    // property 里存的是速度向量（含大小），转向时只换方向、保留速度大小
                    direction = flat.normalize().scale(direction.length());
                }
            }

            double progress = Math.min(slide.getDoingTick(), 20) / 20.0;
            double scale = 1.0 + progress * (0.7 - 1.0);
            // y 分量保留玩家当前垂直速度，所以滑铲中起跳不会被压掉
            return new Vec3(direction.x * scale, current.getDeltaMovement().y, direction.z * scale);
        });
    }
}
