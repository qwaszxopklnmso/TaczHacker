package com.qw.taczhacker.mixin;

import com.alrex.parcool.common.action.impl.Slide;
import com.alrex.parcool.common.capability.IStamina;
import com.alrex.parcool.common.capability.Parkourability;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

/**
 * 功能7：ParCool 长滑铲 —— Slide（滑铲）MixIn
 *
 * ParCool 3.4.x 的 Slide#canContinue 原生逻辑是：
 *   getDoingTick() < min(客户端 SlidingContinuableTick, 服务端 MaxSlidingContinuableTick)
 *   && Crawl.isDoing()
 *
 * 服务端配置上限只有 60 tick（3 秒），所以必须注入才能做到「一直滑」。
 *
 * 三个注入点：
 *   1. canContinue(HEAD)                —— 滑铲不自动结束；检测到取消输入时放行原生判定
 *   2. onWorkingTickInLocalClient(HEAD) —— 刷新滑行方向，转视角即可转向
 *   3. onStartInLocalClient(TAIL)       —— 起滑瞬间刷新一次方向
 *
 * 结束滑铲只有一种方式：松开后再按一次滑铲键（ParCool 的爬行键，默认 C）。
 * 跳跃键不参与结束滑铲 —— 滑铲期间跳跃由 ParCoolBehaviorEnforcerMixin 放开，
 * 所以按空格是起跳，不是退出滑铲。
 *
 * 为什么取消是「放行原生判定」而不是两端都直接返回 false：
 * 按键状态只有客户端有，所以强制持续的判定带 isClientSide 守卫，
 * 服务端永远跟随客户端同步过来的开始/结束状态，不会脱节。
 *
 * 此 Mixin 只在安装了 ParCool 时才会被应用（配置在 taczhacker.parcool.mixins.json，
 * required=false），ParCool 不存在时不会加载本类，也不会因为找不到目标类而崩溃。
 */
@Mixin(value = Slide.class, remap = false)
public abstract class ParCoolSlideMixin {

    /** Slide 内部缓存的滑行方向（水平单位向量） */
    @Shadow
    private Vec3 slidingVec;

    @Inject(
            method = "canContinue",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void taczhacker$longSlide(Player player, Parkourability parkourability, IStamina stamina,
                                      CallbackInfoReturnable<Boolean> cir) {
        // 只有本地玩家才读得到按键，也只有本地玩家需要强制持续
        if (!player.level().isClientSide()) return;
        if (!ParCoolLongSlide.isEnabled()) return;

        if (taczhacker$isCancelRequested()) {
            return; // 放行 ParCool 原生判定 → 滑铲正常结束并同步给服务端
        }

        ParCoolLongSlide.markSliding();
        cir.setReturnValue(true);
    }

    /**
     * 滑铲进行中每 tick 刷新滑行方向：转视角 = 转向
     */
    @Inject(
            method = "onWorkingTickInLocalClient",
            at = @At("HEAD"),
            remap = false
    )
    private void taczhacker$steerSlide(Player player, Parkourability parkourability, IStamina stamina,
                                       CallbackInfo ci) {
        if (!ParCoolLongSlide.isSteerableSlide()) return;
        taczhacker$refreshDirection(player);
    }

    /**
     * 起滑瞬间也刷新一次，避免第一 tick 沿用旧方向
     */
    @Inject(
            method = "onStartInLocalClient",
            at = @At("TAIL"),
            remap = false
    )
    private void taczhacker$steerOnStart(Player player, Parkourability parkourability, IStamina stamina,
                                         ByteBuffer buffer, CallbackInfo ci) {
        if (!ParCoolLongSlide.isSteerableSlide()) return;
        taczhacker$refreshDirection(player);
    }

    private void taczhacker$refreshDirection(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-4) return;
        this.slidingVec = horizontal.normalize();
    }

    /**
     * 是否请求结束滑铲
     *
     * 只有一种方式：松开后再按一次滑铲键。
     * 跳跃键不再参与取消 —— 滑铲期间跳跃由 {@link ParCoolBehaviorEnforcerMixin}
     * 放开（ParCool 原版是屏蔽掉跳跃键的）。
     */
    private boolean taczhacker$isCancelRequested() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        // 松开后再按一次滑铲键（ParCool 的爬行键，默认 C）
        //    getDoingTick() <= 1 时忽略：起滑那一两 tick 按键必然还按着，不能自己把自己取消掉
        int doingTick = ((com.alrex.parcool.common.action.Action) (Object) this).getDoingTick();
        return doingTick > 1 && ParCoolLongSlide.isSlideKeyJustPressed();
    }
}
