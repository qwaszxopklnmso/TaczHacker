package com.qw.taczhacker.mixin;

import com.mojang.logging.LogUtils;
import com.qw.taczhacker.feature.fullbright.GammaAccess;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 功能6：全亮（Fullbright）—— 让 gamma 能取到大于 1.0 的值
 *
 * 背景（2026-10-11 参考 FDP 的 gamma 全亮后重写）：旧版本这里把 gamma 的取值范围
 * 锁死在 0.0~1.0，理由是「gamma>=1.0 时渲染公式饱和」—— **那个理由是错的**，
 * 原版 {@code LightTexture#updateLightTexture} 里的 lerp 因子并不钳制，
 * gamma > 1 会向外插值、最后才 clamp 到 1.0，所以 gamma 越大越亮。
 * 详见 {@link GammaAccess} 的类注释（含实测数值：无光洞穴大约要 gamma≥11.5 才纯白）。
 *
 * ⚠️ 不用 {@code @Shadow} 访问 gamma 字段（refmap 没加载时会失败），统一走
 * {@link GammaAccess#gammaOption(Options)} 的反射；该反射生产环境用 SRG 名（见 GammaAccess）。
 *
 * ⚠️ 有两个注入点，缺一不可：
 * <ol>
 *   <li>{@code load()} HEAD（SRG {@code m_92140_}）：**必须在读 options.txt 之前**把 codec 放宽，
 *       否则上一局全亮退出时写下的 {@code gamma:100.0} 会被原版 {@code Codec.doubleRange(0.0,1.0)}
 *       判为解析失败 → 值停在 initialValue(0.5) + 刷一条 "Error parsing option value"，
 *       玩家的真实亮度记录随后被覆盖丢失。</li>
 *   <li>{@code <init>} RETURN：兜底（dev 环境没有 SRG 名时 load 注入点 require=0 会跳过，
 *       由这里补齐；生产环境此时早已放宽，installWideRange 幂等直接返回）。</li>
 * </ol>
 *
 * 参考实现：GJEB (Gamma Just Extreme Bright) by U_Team（原版滑块上限思路）
 * + FDPClient 的 gamma 全亮（直接用远大于 1 的 gamma）。
 */
@Mixin(Options.class)
public abstract class OptionsGammaMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 读 options.txt 之前放宽 gamma 取值范围
     *
     * ⚠️ 用 SRG 名 + remap=false（重混淆环境里方法名是 SRG，且本项目不带 refmap）；
     * dev 环境匹配不到，靠 {@code require = 0} 静默跳过，由下面的 RETURN 兜底。
     */
    @Inject(method = "m_92140_", at = @At("HEAD"), require = 0, remap = false)
    private void taczhacker_widenGammaBeforeLoad(CallbackInfo ci) {
        install(this, "load() HEAD（读 options.txt 之前）");
    }

    /**
     * 兜底：Options 构造结束（dev 环境 / load 注入点没匹配上时）
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void taczhacker_widenGammaRange(CallbackInfo ci) {
        install(this, "<init> RETURN（兜底）");
    }

    private static void install(Object options, String from) {
        try {
            OptionInstance<Double> gamma = GammaAccess.gammaOption((Options) options);
            if (gamma == null) {
                LOGGER.warn("[TaczHacker][全亮] 拿不到 gamma 选项，跳过取值范围放宽（来源：{}）", from);
                return;
            }
            boolean alreadyInstalled = GammaAccess.isWideRangeInstalled();
            GammaAccess.installWideRange(gamma);
            if (!alreadyInstalled && GammaAccess.isWideRangeInstalled()) {
                // 这条日志能直接说明「读 options.txt 之前就放宽了」有没有生效（Bug：重启丢亮度）
                LOGGER.info("[TaczHacker][全亮] 取值范围放宽成功（来源：{}）", from);
            }
        } catch (Throwable t) {
            // 失败也不致命：FullbrightHandler 里还有直接写字段的兜底
            LOGGER.warn("[TaczHacker][全亮] 放宽 gamma 范围时出错（来源：{}）", from, t);
        }
    }
}
