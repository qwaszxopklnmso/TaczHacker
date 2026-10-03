package com.qw.taczhacker.mixin;

import com.alrex.parcool.common.capability.stamina.HungerStamina;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 功能7附属：不消耗 ParCool 体力 —— 饥饿值体力模式（HungerStamina）
 *
 * 该模式下 ParCool 用饥饿值当体力，consume 会扣饥饿度（并在服务端也扣）。
 * 拦掉后跑酷不再消耗饥饿值。
 */
@Mixin(value = HungerStamina.class, remap = false)
public class ParCoolHungerStaminaMixin {

    @Inject(
            method = "consume",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void taczhacker$infiniteStamina(int value, CallbackInfo ci) {
        if (ParCoolLongSlide.shouldIgnoreStamina()) {
            ci.cancel();
        }
    }
}
