package com.qw.taczhacker.mixin;

import com.alrex.parcool.common.capability.stamina.ParCoolStamina;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 功能7附属：不消耗 ParCool 体力 —— 默认体力实现（ParCool 自己的体力条）
 *
 * ParCool 的体力是 capability（IStamina），动作通过 stamina.consume(n) 扣减。
 * 这里直接拦掉扣减，体力条不会下降，也就不会力竭。
 *
 * 此 Mixin 只在安装了 ParCool 时应用（taczhacker.parcool.mixins.json，required=false）。
 */
@Mixin(value = ParCoolStamina.class, remap = false)
public class ParCoolStaminaMixin {

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
