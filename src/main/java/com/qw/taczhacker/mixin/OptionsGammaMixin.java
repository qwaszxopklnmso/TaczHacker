package com.qw.taczhacker.mixin;

import com.qw.taczhacker.feature.fullbright.GammaAccess;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * 功能6：全亮（Fullbright）—— 让 gamma 能取到大于 1.0 的值
 *
 * 背景（2026-10-11 参考 FDP 的 gamma 全亮后重写）：旧版本这里把 gamma 的取值范围
 * 锁死在 0.0~1.0，理由是「gamma>=1.0 时渲染公式饱和」—— **那个理由是错的**，
 * 原版 {@code LightTexture#updateLightTexture} 里的 lerp 因子并不钳制，
 * gamma > 1 会向外插值、最后才 clamp 到 1.0，所以 gamma 越大越亮。
 * 详见 {@link GammaAccess} 的类注释（含实测数值：无光洞穴大约要 gamma≥11.5 才纯白）。
 *
 * ⚠️ 不使用 {@code @Shadow} 访问 gamma 字段（refmap 没加载时会失败），
 * 仍然用反射拿；真正干活的部分都在 {@link GammaAccess} 里（那样 {@code FullbrightHandler}
 * 也能复用同一套反射，出问题时只需要看一个地方）。
 *
 * 参考实现：GJEB (Gamma Just Extreme Bright) by U_Team（原版滑块上限思路）
 * + FDPClient 的 gamma 全亮（直接用远大于 1 的 gamma）。
 */
@Mixin(Options.class)
public abstract class OptionsGammaMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void taczhacker_widenGammaRange(CallbackInfo ci) {
        try {
            Field gammaField = Options.class.getDeclaredField("gamma");
            gammaField.setAccessible(true);
            @SuppressWarnings("unchecked")
            OptionInstance<Double> gamma = (OptionInstance<Double>) gammaField.get(this);
            if (gamma == null) {
                return;
            }
            GammaAccess.installWideRange(gamma);
        } catch (Throwable t) {
            // 失败也不致命：FullbrightHandler 里还有直接写字段的兜底
        }
    }
}
