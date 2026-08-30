package com.qw.taczhacker.mixin;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;

/**
 * 功能6：伽马值修改（Fullbright）—— Mixin 支持
 *
 * 在 Options 构造函数完成后，通过反射替换 gamma 的 OptionInstance 内部的
 * values 字段（ValueSet 类型）和 codec 字段。
 *
 * 由于 OptionInstance.SliderableValueSet 是包内可见的（package-private），
 * 无法在编译时直接实现。因此使用 java.lang.reflect.Proxy 在运行时动态创建
 * 一个代理对象，实现 SliderableValueSet 接口，支持 0.0~1.0 的伽马值范围。
 *
 * 注意：Minecraft 的 LightTexture 渲染公式为
 * pixel.lerp(notGamma(pixel), Math.max(0, gamma - darknessScale))，
 * 当 gamma >= 1.0 时 lerp 因子饱和，因此上限设为 1.0 即可获得最大亮度。
 *
 * 参考实现：GJEB (Gamma Just Extreme Bright) by U_Team
 */
@Mixin(Options.class)
public abstract class OptionsGammaMixin {

    /** SliderableValueSet 接口的完整类名（用于反射加载） */
    private static final String SLIDERABLE_VALUE_SET_CLASS =
            "net.minecraft.client.OptionInstance$SliderableValueSet";

    /**
     * 在 Options 构造函数的末尾（RETURN），修改 gamma 的 OptionInstance 内部字段，
     * 使其支持 0.0~1.0 的伽马值范围（与 vanilla 一致，但保留此 Mixin 以兼容后续扩展）。
     *
     * 注意：不使用 @Shadow 访问 gamma 字段，因为 refmap 未加载时 @Shadow 会失败。
     * 改用 java.lang.reflect.Field 反射获取 gamma 字段。
     *
     * 使用 java.lang.reflect.Proxy 动态创建 SliderableValueSet 接口的实现，
     * 将 validateValue / toSliderValue / fromSliderValue 映射到 0.0~1.0 范围。
     * 默认方法（如 createButton）委托给接口的默认实现。
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void taczhacker_replaceGammaOption(CallbackInfo ci) {
        try {
            // 1. 通过反射获取 Options 的 gamma 字段（不使用 @Shadow，避免 refmap 问题）
            Field gammaField = Options.class.getDeclaredField("gamma");
            gammaField.setAccessible(true);
            OptionInstance<Double> gamma = (OptionInstance<Double>) gammaField.get(this);

            // 2. 通过反射加载 SliderableValueSet 接口（package-private，编译时不可见）
            Class<?> sliderableValueSetClass = Class.forName(SLIDERABLE_VALUE_SET_CLASS);

            // 3. 使用动态代理创建 SliderableValueSet 的实现
            //    支持 0.0~1.0 的伽马值范围
            Object customValueSet = Proxy.newProxyInstance(
                    sliderableValueSetClass.getClassLoader(),
                    new Class<?>[]{sliderableValueSetClass},
                    new GammaValueSetInvocationHandler()
            );

            // 4. 通过反射替换 gamma 的 OptionInstance 内部的 values 字段
            Field valuesField = OptionInstance.class.getDeclaredField("values");
            valuesField.setAccessible(true);
            valuesField.set(gamma, customValueSet);

            // 5. 通过反射替换 codec 字段，支持 0.0~1.0 范围
            Field codecField = OptionInstance.class.getDeclaredField("codec");
            codecField.setAccessible(true);
            Codec<Double> customCodec = Codec.either(
                    Codec.doubleRange(0.0, 1.0), Codec.BOOL
            ).xmap(
                    either -> either.map(v -> v, b -> b ? 1.0 : 0.0),
                    Either::left
            );
            codecField.set(gamma, customCodec);

        } catch (Exception e) {
            // 反射失败时静默处理，gamma 仍使用原版范围
        }
    }

    /**
     * 动态代理的 InvocationHandler，处理 SliderableValueSet 接口的方法调用。
     *
     * 处理的方法：
     * - validateValue:  接受 0.0~1.0 范围的值
     * - toSliderValue:  将 0.0~1.0 映射到 0.0~1.0（滑块位置，线性映射）
     * - fromSliderValue: 将 0.0~1.0（滑块位置）映射到 0.0~1.0
     * - codec:          返回支持 0.0~1.0 的 Codec
     * - 其他方法（如 createButton 默认方法）：委托给接口的默认实现
     */
    private static class GammaValueSetInvocationHandler implements InvocationHandler {

        /** 自定义 Codec，支持 0.0~1.0 范围 */
        private static final Codec<Double> CUSTOM_CODEC = Codec.either(
                Codec.doubleRange(0.0, 1.0), Codec.BOOL
        ).xmap(
                either -> either.map(v -> v, b -> b ? 1.0 : 0.0),
                Either::left
        );

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "validateValue":
                    // 接受 0.0~1.0 范围的值
                    Double value = (Double) args[0];
                    return value >= 0.0 && value <= 1.0
                            ? Optional.of(value)
                            : Optional.empty();

                case "toSliderValue":
                    // 0.0~1.0 -> 0.0~1.0（线性映射）
                    return (Double) args[0];

                case "fromSliderValue":
                    // 0.0~1.0 -> 0.0~1.0（线性映射）
                    return (Double) args[0];

                case "codec":
                    return CUSTOM_CODEC;

                default:
                    // 处理默认方法（如 createButton）
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    return null;
            }
        }
    }
}