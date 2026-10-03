package com.qw.taczhacker.mixin;

import com.alrex.parcool.common.capability.stamina.OtherStamina;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 功能7附属：不消耗 ParCool 体力 —— 外部体力来源模式（OtherStamina）
 *
 * 该实现用于让其他 mod（如 EpicFight 系）提供体力值，同样拦掉扣减。
 */
@Mixin(value = OtherStamina.class, remap = false)
public class ParCoolOtherStaminaMixin {

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
