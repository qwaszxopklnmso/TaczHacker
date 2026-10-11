package com.qw.taczhacker.feature.fullbright;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.function.Function;

/**
 * 全亮（功能6）用到的 gamma 反射工具
 *
 * 为什么需要它 —— 2026-10-11 作者说「参考一下 FDP 的那个调 gamma 的」时暴露的旧实现错误：
 * 旧代码把 gamma 的取值范围锁死在 0.0~1.0，注释还写着「gamma>=1.0 时 lerp 因子饱和，
 * 所以 1.0 就是最大亮度」。**这个结论是错的**，看原版 {@code LightTexture#updateLightTexture}：
 *
 * <pre>
 *   float f14 = this.minecraft.options.gamma().get().floatValue();
 *   Vector3f v = new Vector3f(notGamma(x), notGamma(y), notGamma(z));
 *   vector3f1.lerp(v, Math.max(0.0f, f14 - f3));      // ← 因子没有 clamp
 *   vector3f1.lerp(new Vector3f(0.75f, 0.75f, 0.75f), 0.04f);
 *   clampColor(vector3f1);                            // ← 最后才 clamp 到 0~1
 * </pre>
 *
 * JOML 的 {@code Vector3f.lerp(target, t)} 是 {@code this + (target - this) * t}，**t 不钳制**
 * → gamma > 1 时是「向外插值」：值会超过 notGamma(...) 甚至超过 1.0，最后被 clampColor 夹到 1.0。
 * 换个说法：gamma 越大越亮，直到某一点整张光照贴图都是纯白。
 *
 * 实测数值（无光源的洞穴：光照贴图原始值约 0.03）：
 * <pre>
 *   notGamma(0.03) = 1 - 0.97^4 = 0.1147
 *   结果 = 0.03 + (0.1147 - 0.03) * gamma = 0.03 + 0.0847 * gamma
 *   → gamma ≈ 11.5 才刚好到 1.0（纯白），所以 gamma=1.0（原版「明亮」档）在洞里只有 0.11，依旧很黑
 * </pre>
 * 这就是「全亮开了还是黑」的根因。{@link #MAX_GAMMA} 取 100：远超过 11.5 那个阈值，
 * 留足余量（FDP 之类的客户端在 1.8.9 上直接用 {@code gammaSetting = 100f}，就是这个道理）。
 *
 * 但是 1.20.1 的 gamma 不能直接设 100：{@code Options.gamma} 是
 * {@code OptionInstance<Double>}，原版 ValueSet（{@code UnitDouble}）只 validate 0.0~1.0，
 * 而 {@code OptionInstance#set} 校验不过时的行为**比"没反应"更糟**（反编译 `OptionInstance.java:129-133` 确认）：
 *
 * <pre>
 *   public void set(T value) {
 *       T t = this.values.validateValue(value).orElseGet(() -> {
 *           LOGGER.error("Illegal option value " + value + " for " + this.caption);
 *           return this.initialValue;        // ← 回落的是 initialValue，不是旧值！
 *       });
 *       ...
 *   }
 * </pre>
 *
 * gamma 的 initialValue 是 **0.5** ⇒ 直接 {@code set(100)} 会把亮度打回默认档 + 刷一条 error 日志。
 * 所以 {@link FullbrightHandler} 的兜底顺序必须是「先 set() 再写字段」，或者干脆别调 set()
 * （见 {@link #isWideRangeInstalled()}：放宽成功才用 set()，否则直接 {@link #forceSet}）。
 * 所以这里做三件事：
 * <ol>
 *   <li>换掉 {@code values} 字段 → validateValue 接受 0.0~100.0（滑块仍然映射 0.0~1.0，
 *       免得原版「亮度」滑条被撑到 100 而对不上号）</li>
 *   <li>换掉 {@code codec} 字段 → options.txt 里也能存 &gt;1 的值</li>
 *   <li>换掉 {@code toString} 字段 → 原版标签是 {@code (int)(value*100)}，
 *       gamma=100 时视频设置里会显示「亮度: 10000」，这里改成按 0~1 显示</li>
 * </ol>
 * 全部失败也不影响功能：{@link #forceSet} 会直接写 {@code value} 字段兜底。
 *
 * ⚠️ {@code OptionInstance.ValueSet} / {@code SliderableValueSet} 是包内可见的
 * （反编译确认：{@code static interface ValueSet<T>}），只能用 {@link Proxy} 动态实现。
 * ⚠️ 本类引用客户端专属类，只能被客户端代码调用（调用方 {@code OptionsGammaMixin} 挂在
 * {@code taczhacker.mixins.json} 的 {@code client} 列表里）。
 */
public final class GammaAccess {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** {@code OptionInstance$SliderableValueSet} 的完整类名（包内可见，编译期引用不到） */
    private static final String SLIDERABLE_VALUE_SET_CLASS =
            "net.minecraft.client.OptionInstance$SliderableValueSet";

    /**
     * 允许写入的最大实际 gamma。
     *
     * 100 不是随便取的：无光洞穴大概需要 11.5 就到纯白，取 100 是照抄 FDP 那套客户端的老经验
     * （它们 1.8.9 直接 {@code gammaSetting = 100f}），留足余量、也避免以后有人调小阈值时不够亮。
     */
    public static final double MAX_GAMMA = 100.0;

    /** 配置项的「全亮强度」0~1 就按这个系数换算成实际 gamma */
    private static final Codec<Double> WIDE_CODEC = Codec.either(
            Codec.doubleRange(0.0, MAX_GAMMA), Codec.BOOL
    ).xmap(either -> either.map(v -> v, b -> b ? MAX_GAMMA : 0.0), Either::left);

    /** {@link #installWideRange} 是否成功放宽过（成功才敢用 {@code set()} 写 >1 的值） */
    private static volatile boolean wideRangeInstalled = false;

    private GammaAccess() {
    }

    /**
     * 取值范围是否已经放宽
     *
     * 为 false 时**不要**调 {@code OptionInstance#set(>1)}：那会回落成 initialValue（gamma 是 0.5）
     * 并刷一条 error 日志，等于把亮度打回默认档。这种情况直接 {@link #forceSet}。
     */
    public static boolean isWideRangeInstalled() {
        return wideRangeInstalled;
    }

    /**
     * 放宽 gamma 的取值范围（在 {@code Options} 构造完成后调用一次）
     */
    public static void installWideRange(OptionInstance<Double> gamma) {
        try {
            Class<?> valueSetClass = Class.forName(SLIDERABLE_VALUE_SET_CLASS);
            Object wideValueSet = Proxy.newProxyInstance(
                    valueSetClass.getClassLoader(),
                    new Class<?>[]{valueSetClass},
                    new GammaValueSetHandler());
            setField(gamma, "values", wideValueSet);
            setField(gamma, "codec", WIDE_CODEC);
            installClampedLabel(gamma);
            wideRangeInstalled = true;
            LOGGER.debug("[TaczHacker][全亮] gamma 取值范围已放宽到 0.0~{}", MAX_GAMMA);
        } catch (Throwable t) {
            // 放宽失败也只是 set() 会被拒 → applyFullbright 里还有 forceSet 兜底
            LOGGER.warn("[TaczHacker][全亮] 放宽 gamma 范围失败，将退化为直接写字段（功能不受影响）", t);
        }
    }

    /**
     * 直接把值写进 {@code OptionInstance.value} 字段，绕过 ValueSet 校验
     *
     * @return 是否写入成功
     */
    public static boolean forceSet(OptionInstance<Double> gamma, double value) {
        try {
            setField(gamma, "value", value);
            return true;
        } catch (Throwable t) {
            LOGGER.error("[TaczHacker][全亮] 直接写入 gamma 失败", t);
            return false;
        }
    }

    /**
     * 把「亮度」那一行的显示文字换成按 0~1 百分比显示的版本
     *
     * 原版：{@code int i = (int)(value * 100.0)} → gamma=100 时显示「亮度: 10000」，看起来像坏了。
     */
    private static void installClampedLabel(OptionInstance<Double> gamma) throws ReflectiveOperationException {
        Field captionField = OptionInstance.class.getDeclaredField("caption");
        captionField.setAccessible(true);
        Component caption = (Component) captionField.get(gamma);

        Function<Double, Component> label = value -> {
            double shown = Math.max(0.0, Math.min(1.0, value == null ? 0.0 : value));
            Component amount;
            if (shown <= 0.0) {
                amount = Component.translatable("options.gamma.min");
            } else if (shown >= 1.0) {
                amount = Component.translatable("options.gamma.max");
            } else {
                amount = Component.literal(Integer.toString((int) (shown * 100.0)));
            }
            return Component.translatable("options.generic_value", caption, amount);
        };
        setField(gamma, "toString", label);
    }

    /** 反射写字段（含 private final 的实例字段，setAccessible 后可写） */
    private static void setField(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = OptionInstance.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /**
     * 动态代理实现的 ValueSet：validate 接受 0~{@link #MAX_GAMMA}，
     * 但滑块位置仍然按 0~1 映射（原版「亮度」界面照旧能用）
     */
    private static class GammaValueSetHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "validateValue": {
                    Double value = (Double) args[0];
                    return value != null && value >= 0.0 && value <= MAX_GAMMA
                            ? Optional.of(value)
                            : Optional.empty();
                }
                // 0~MAX_GAMMA → 滑块 0~1（夹住，否则滑块控件会拿到超范围的初值）
                case "toSliderValue": {
                    Double value = (Double) args[0];
                    return value == null ? 0.0 : Math.max(0.0, Math.min(1.0, value));
                }
                // 滑块 0~1 → 值原样（拖动滑条只会得到 0~1，与原版一致）
                case "fromSliderValue":
                    return args[0];

                case "codec":
                    return WIDE_CODEC;

                default:
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    // 代理对象自己的 Object 方法（打日志/调试时才可能被调到），给个不炸的返回
                    switch (method.getName()) {
                        case "toString":
                            return "TaczHackerGammaValueSet[0.." + MAX_GAMMA + "]";
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        default:
                            return null;
                    }
            }
        }
    }
}
