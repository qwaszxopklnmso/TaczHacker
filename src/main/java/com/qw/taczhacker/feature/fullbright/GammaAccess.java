package com.qw.taczhacker.feature.fullbright;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
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

    // ============================================================
    // 字段名：生产环境是 SRG 名，dev（官方映射）是官方名。
    //
    // ⚠️ 2026-10-12 修（生产环境全亮完全失效 + 每 tick 刷 ERROR 的根因）：
    // Forge 生产运行时 Minecraft 的字段名是 **SRG 名**（重混淆产物里
    // FlightHandler 引用的是 {@code Minecraft.f_91074_}，实锤），而
    // {@code Class.getDeclaredField("gamma")} 里的字符串不会被 reobf 重写
    // → 运行时必然抛 NoSuchFieldException。以前 OptionsGammaMixin 里那个空 catch
    // 把它吞了，于是 installWideRange 从没跑过、forceSet 也一直炸。
    //
    // 映射表（官方名 → SRG 名，来自 srg_to_official_1.20.1.tsrg）：
    //   Options.gamma          → f_92071_
    //   OptionInstance.values  → f_231476_
    //   OptionInstance.codec   → f_231477_
    //   OptionInstance.toString→ f_231475_（字段，不是方法！）
    //   OptionInstance.caption → f_231480_
    //   OptionInstance.value   → f_231481_
    // ============================================================
    private static final String SRG_GAMMA = "f_92071_";
    private static final String SRG_VALUES = "f_231476_";
    private static final String SRG_CODEC = "f_231477_";
    private static final String SRG_TO_STRING = "f_231475_";
    private static final String SRG_CAPTION = "f_231480_";
    private static final String SRG_VALUE = "f_231481_";

    private GammaAccess() {
    }

    /**
     * 取 {@code Options.gamma} 这个 OptionInstance（SRG/官方名都试）
     *
     * 供 {@code OptionsGammaMixin} 复用，避免每个类各写一份裸 {@code getDeclaredField}。
     * 拿不到时返回 {@code null}（调用方自己决定兜底）。
     */
    public static OptionInstance<Double> gammaOption(Options options) {
        if (options == null) {
            return null;
        }
        try {
            Field field = findField(Options.class, "gamma", SRG_GAMMA);
            @SuppressWarnings("unchecked")
            OptionInstance<Double> gamma = (OptionInstance<Double>) field.get(options);
            return gamma;
        } catch (Throwable t) {
            LOGGER.warn("[TaczHacker][全亮] 获取 gamma 选项失败", t);
            return null;
        }
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
     * 放宽 gamma 的取值范围（{@code Options} 构造里 gamma 赋值之后、{@code load()} 之前调用一次）
     *
     * 幂等：已经成功放宽过就直接返回（{@code OptionsGammaMixin} 会在 {@code load()} HEAD
     * 和 {@code <init>} RETURN 各调一次，后者只是兜底）。
     */
    public static void installWideRange(OptionInstance<Double> gamma) {
        if (gamma == null || wideRangeInstalled) {
            return;
        }
        try {
            Class<?> valueSetClass = Class.forName(SLIDERABLE_VALUE_SET_CLASS);
            Object wideValueSet = Proxy.newProxyInstance(
                    valueSetClass.getClassLoader(),
                    new Class<?>[]{valueSetClass},
                    new GammaValueSetHandler());
            setField(gamma, OptionInstance.class, "values", SRG_VALUES, wideValueSet);
            setField(gamma, OptionInstance.class, "codec", SRG_CODEC, WIDE_CODEC);
        } catch (Throwable t) {
            // 放宽失败也只是 set() 会被拒 → applyFullbright 里还有 forceSet 兜底
            LOGGER.warn("[TaczHacker][全亮] 放宽 gamma 范围失败，将退化为直接写字段（功能不受影响）", t);
            return;
        }
        // 显示标签是锦上添花，失败不能连累 range 放宽
        try {
            installClampedLabel(gamma);
        } catch (Throwable t) {
            LOGGER.warn("[TaczHacker][全亮] 替换 gamma 显示标签失败（不影响全亮功能）", t);
        }
        wideRangeInstalled = true;
        LOGGER.info("[TaczHacker][全亮] gamma 取值范围已放宽到 0.0~{}", MAX_GAMMA);
    }

    /**
     * 直接把值写进 {@code OptionInstance.value} 字段，绕过 ValueSet 校验
     *
     * @return 是否写入成功
     */
    public static boolean forceSet(OptionInstance<Double> gamma, double value) {
        try {
            setField(gamma, OptionInstance.class, "value", SRG_VALUE, value);
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
        Field captionField = findField(OptionInstance.class, "caption", SRG_CAPTION);
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
        setField(gamma, OptionInstance.class, "toString", SRG_TO_STRING, label);
    }

    /**
     * 按名字找字段：**先试 SRG 名（生产环境），再回退官方名（dev 环境）**
     *
     * 两个名字都找不到才抛 {@link NoSuchFieldException}。
     */
    private static Field findField(Class<?> owner, String official, String srg) throws NoSuchFieldException {
        try {
            Field field = owner.getDeclaredField(srg);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException ignored) {
            // dev（官方映射）环境没有 SRG 名，走下面的官方名
        }
        Field field = owner.getDeclaredField(official);
        field.setAccessible(true);
        return field;
    }

    /** 反射写字段（含 private final 的实例字段，setAccessible 后可写） */
    private static void setField(Object target, Class<?> owner, String official, String srg, Object value)
            throws ReflectiveOperationException {
        findField(owner, official, srg).set(target, value);
    }

    /**
     * 动态代理实现的 ValueSet：validate 接受 0~{@link #MAX_GAMMA}，
     * 但滑块位置仍然按 0~1 映射（原版「亮度」界面照旧能用）
     *
     * ⚠️ **绝不能按方法名分派**（2026-10-17 崩服教训）：
     * 运行时接口方法名是 SRG 名，不是官方名 ——
     * {@code validateValue} 在生产里叫 {@code m_214064_}、{@code toSliderValue} 叫 {@code m_213640_}、
     * {@code fromSliderValue} 叫 {@code m_213729_}、{@code codec} 叫 {@code m_213664_}、
     * {@code createButton} 叫 {@code m_213823_}。
     * 旧实现 `switch (method.getName())` 全都落进 default → 返回 null →
     * 原版 {@code OptionInstance#set} 里
     * {@code this.values.validateValue(v).orElseGet(...)} 直接 NPE 崩游戏（实测按 B 闪退）。
     * 现在改成**按「返回类型 + 参数个数 + 参数类型」分派**，与命名空间无关，dev/生产同一套：
     * <ul>
     *   <li>{@code (Object)Optional} → validateValue</li>
     *   <li>{@code (Object)double}   → toSliderValue</li>
     *   <li>{@code (double)Object}   → fromSliderValue</li>
     *   <li>{@code ()Codec}          → codec</li>
     *   <li>其余走接口默认实现（createButton 等），最后才认 Object 的 toString/hashCode/equals</li>
     * </ul>
     */
    private static class GammaValueSetHandler implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Class<?> returnType = method.getReturnType();
            Class<?>[] parameterTypes = method.getParameterTypes();
            int paramCount = parameterTypes.length;
            Object arg0 = (args != null && args.length > 0) ? args[0] : null;

            // validateValue(T) -> Optional
            if (Optional.class.equals(returnType) && paramCount == 1) {
                Double value = arg0 instanceof Double d ? d : null;
                return value != null && value >= 0.0 && value <= MAX_GAMMA
                        ? Optional.of(value)
                        : Optional.empty();
            }
            // toSliderValue(T) -> double（0~MAX_GAMMA 映射到滑块 0~1，夹住以免滑块拿到超范围初值）
            if (returnType == double.class && paramCount == 1) {
                Double value = arg0 instanceof Double d ? d : null;
                return value == null ? 0.0 : Math.max(0.0, Math.min(1.0, value));
            }
            // fromSliderValue(double) -> T（滑块 0~1 原样写入，与原版一致）
            if (Object.class.equals(returnType) && paramCount == 1
                    && parameterTypes[0] == double.class) {
                return arg0;
            }
            // codec() -> Codec
            if (Codec.class.isAssignableFrom(returnType) && paramCount == 0) {
                return WIDE_CODEC;
            }
            // 接口默认实现（createButton 等）：它会再回调本代理的 toSliderValue/fromSliderValue
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            // 代理自己的 Object 方法（只在打日志/调试时可能被调到），给个不炸的返回
            switch (method.getName()) {
                case "toString":
                    return "TaczHackerGammaValueSet[0.." + MAX_GAMMA + "]";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == arg0;
                default:
                    // 走到这里说明 ValueSet 以后新增了方法 —— 打日志并返回 null，
                    // 别再像旧实现那样静默（就是它让 NPE 变得莫名其妙）
                    LOGGER.warn("[TaczHacker][全亮] 未处理的 ValueSet 方法：{}（返回 null）", method);
                    return null;
            }
        }
    }
}
