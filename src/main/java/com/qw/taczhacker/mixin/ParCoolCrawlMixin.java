package com.qw.taczhacker.mixin;

import com.alrex.parcool.common.Parkourability;
import com.alrex.parcool.common.action.ParCoolActions;
import com.alrex.parcool.common.action.impl.Crawl;
import com.alrex.parcool.common.action.impl.Slide;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 功能7：ParCool 长滑铲 —— Crawl（爬行）MixIn（ParCool 4.0.1.0）
 *
 * 滑铲（Slide）的父动作是爬行（Crawl）：Slide 的 isPossible() 要求父动作正在进行，
 * 只要 Crawl 结束，滑铲也会被结束。而 Crawl 的持续条件依赖爬行键是否按住
 * （input.isActive()），所以要让滑铲一直进行，必须同时把爬行钉住。
 *
 * 注意：
 *   - 只在「滑铲正在进行」时钉住爬行，普通爬行行为不受影响
 *   - 与 Slide 一样只在客户端执行（Crawl 的 triggeredSide 也是默认 CLIENT），
 *     服务端跟随客户端同步过来的状态
 */
@Mixin(value = Crawl.class, remap = false)
public class ParCoolCrawlMixin {

    @Inject(
            method = "canContinue",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void taczhacker$keepCrawlingWhileSliding(CallbackInfoReturnable<Boolean> cir) {
        if (!ParCoolLongSlide.isEnabled()) return;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        Slide slide = Parkourability.get(player).get(ParCoolActions.SLIDE);
        if (slide != null && slide.isDoing()) {
            cir.setReturnValue(true);
        }
    }
}
