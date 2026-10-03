package com.qw.taczhacker.mixin;

import com.alrex.parcool.common.action.impl.Crawl;
import com.alrex.parcool.common.action.impl.Slide;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 功能7：ParCool 长滑铲 —— Crawl（爬行）MixIn
 *
 * 滑铲（Slide）的父动作是爬行（Crawl）：只要 Crawl 结束，滑铲也会被结束。
 * 而 Crawl 的原生持续条件依赖按键（按住 C 或 toggle 状态），
 * 所以要让滑铲一直进行，必须同时把爬行钉住。
 *
 * 注意：
 *   - 只在「滑铲正在进行」时钉住爬行，普通爬行行为不受影响
 *   - 只在客户端钉住（和 Slide 一致），服务端跟随客户端同步过来的状态
 */
@Mixin(value = Crawl.class, remap = false)
public class ParCoolCrawlMixin {

    @Inject(
            method = "canContinue",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void taczhacker$keepCrawlingWhileSliding(Player player, Parkourability parkourability,
                                                     IStamina stamina, CallbackInfoReturnable<Boolean> cir) {
        if (!player.level().isClientSide()) return;
        if (!ParCoolLongSlide.shouldForceContinue()) {
            return;
        }
        Slide slide = parkourability.get(Slide.class);
        if (slide != null && slide.isDoing()) {
            cir.setReturnValue(true);
        }
    }
}
