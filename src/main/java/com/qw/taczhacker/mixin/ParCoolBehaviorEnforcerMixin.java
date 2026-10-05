package com.qw.taczhacker.mixin;

import com.alrex.parcool.common.action.BehaviorEnforcer;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 功能7：ParCool 长滑铲 —— 滑铲期间放开跳跃键
 *
 * ParCool 的 Slide#onStartInLocalClient 会往 BehaviorEnforcer 注册一个
 * addMarkerCancellingJump 标记（ID_JUMP_CANCEL），标记的 remain 条件是
 * 「动作还在进行」。只要滑铲还在，BehaviorEnforcer#cancelJump() 就返回 true，
 * ParCool 在输入处理里据此屏蔽跳跃 —— 这就是原版滑铲期间按空格没反应的原因。
 *
 * 这里在 cancelJump(HEAD) 拦截：正在长滑铲时直接返回 false（不屏蔽），
 * 于是滑铲中可以正常起跳。不在滑铲时一律走原生逻辑，
 * 别的动作该屏蔽跳跃还是照旧屏蔽。
 *
 * 起跳后滑铲不会断：Slide#canContinue 那边被本 mod 强制为 true，
 * 而 Slide#onWorkingTickInLocalClient 设水平速度时保留了 deltaMovement 的 y 分量
 * （`slidingVec.scale(speed).add(0, motion.y, 0)`），所以跳跃的垂直速度不会被覆盖，
 * 落地接着滑。关掉 parcool.jumpWhileSliding 就恢复原版行为。
 *
 * 只在装了 ParCool 时加载（taczhacker.parcool.mixins.json，required=false）。
 */
@Mixin(value = BehaviorEnforcer.class, remap = false)
public class ParCoolBehaviorEnforcerMixin {

    @Inject(
            method = "cancelJump",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void taczhacker$allowJumpWhileSliding(CallbackInfoReturnable<Boolean> cir) {
        if (ParCoolLongSlide.shouldAllowJump()) {
            cir.setReturnValue(false);
        }
    }
}
