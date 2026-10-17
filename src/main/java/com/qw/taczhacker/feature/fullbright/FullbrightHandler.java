package com.qw.taczhacker.feature.fullbright;

import com.mojang.logging.LogUtils;
import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 功能6：全亮（Fullbright）—— 靠 gamma 把整张光照贴图拉满
 *
 * 按 B 键切换。开启时把 {@code options.gamma} 设成「全亮强度 × }{@link GammaAccess#MAX_GAMMA}，
 * 关闭时把开全亮之前的亮度还回去。
 *
 * ⚠️ 2026-10-11 重写（作者说「参考一下 FDP 那个调 gamma 的」时发现旧实现是错的）：
 * 旧代码把 gamma 锁在 0.0~1.0，注释还写「gamma>=1.0 时 lerp 因子饱和，所以 1.0 就是最大亮度」。
 * 实际上原版 {@code LightTexture#updateLightTexture} 里那个 lerp 因子**不钳制**，
 * gamma > 1 是向外插值、最后才 clamp 到 1.0 → gamma 越大越亮；
 * 原版「明亮」档（gamma=1.0）在没光源的洞穴里只有约 0.11 的亮度，所以旧实现体感就是「没全亮」。
 * 数学推导与实测数值见 {@link GammaAccess} 的类注释。
 *
 * 亮度记录：开全亮前的那份 gamma 会存进 {@code runtime.savedGamma}（持久化），
 * 关掉时精确还回去 —— 旧实现是「进世界无条件把 gamma 重置成 0.5」，
 * 既会覆盖玩家自己调的亮度，关掉全亮后也回不到原值。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class FullbrightHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 全亮开关状态 */
    private static boolean fullbrightActive = false;

    /** 上次按键状态（用于检测上升沿） */
    private static boolean wasKeyDown = false;

    /** 开启全亮之前的伽马值，关闭时恢复（进世界时从配置里恢复） */
    private static double savedGamma = 0.5;

    /** 玩家是否刚加入世界（用于首次 tick 检测） */
    private static boolean playerJustJoined = true;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            // 玩家离开世界，重置标记
            playerJustJoined = true;
            return;
        }

        // 玩家刚加入世界时，检测并收拾上一局留下的 gamma
        if (playerJustJoined) {
            playerJustJoined = false;
            onPlayerJoinWorld(mc);
        }

        // 总开关检查
        if (!HackConfig.globalEnabled || !HackConfig.fullbrightEnabled) {
            if (fullbrightActive) {
                disableFullbright(mc);
            }
            return;
        }

        // 按键检测（上升沿触发切换）
        boolean isKeyDown = KeyBindings.FULLBRIGHT_KEY.isDown();
        if (isKeyDown && !wasKeyDown) {
            fullbrightActive = !fullbrightActive;
            // 立刻存盘，重进游戏保持这个状态
            HackConfig.runtimeFullbrightActive = fullbrightActive;
            HackConfig.saveRuntime();
            if (fullbrightActive) {
                enableFullbright(mc);
            } else {
                disableFullbright(mc);
            }
        }
        wasKeyDown = isKeyDown;

        // 全亮激活时每 tick 兜底一次（防止被别的代码/其它 mod 改回去）
        if (fullbrightActive) {
            double target = effectiveGamma();
            if (Math.abs(mc.options.gamma().get() - target) > 0.01) {
                applyGamma(mc, target);
            }
        }
    }

    /**
     * 配置里的「全亮强度」（0.0~1.0）换算成实际写入的 gamma
     *
     * 1.0 → {@link GammaAccess#MAX_GAMMA}（100），这就是 FDP 那类客户端的做法；
     * 调小它就得到一个「没那么亮」的全亮（比如 0.2 → gamma 20，洞里仍然全白，只是阴影过渡更明显）。
     */
    public static double effectiveGamma() {
        double strength = HackConfig.fullbrightGamma;
        if (strength <= 0.0) {
            return 0.0;
        }
        if (strength >= 1.0) {
            return GammaAccess.MAX_GAMMA;
        }
        return strength * GammaAccess.MAX_GAMMA;
    }

    /**
     * 写 gamma
     *
     * 顺序有讲究：{@code OptionInstance#set} 在**校验不过**时会把值回落成 {@code initialValue}
     * （gamma 的 initialValue 是 0.5，不是旧值！见 GammaAccess 类注释引的反编译片段），
     * 所以
     * - 放宽成功（{@link GammaAccess#isWideRangeInstalled()}）→ 正常走 {@code set()}，
     *   这样 option 自己的记账/回调都照旧跑
     * - 放宽失败 → 干脆不调 {@code set()}（否则亮度会被打回 0.5 + 刷一条 error 日志），
     *   直接写 {@code value} 字段
     * 两条路最后都会再校验一次实际值，兜底写一次，保证不会退化成「静默失效」。
     */
    private static void applyGamma(Minecraft mc, double target) {
        net.minecraft.client.OptionInstance<Double> gamma = mc.options.gamma();
        if (GammaAccess.isWideRangeInstalled()) {
            // ⚠️ set() 会回调 ValueSet#validateValue，而那个 ValueSet 是我们用动态代理顶替的
            // （GammaAccess.GammaValueSetHandler）。代理一旦返回 null，原版 OptionInstance#set
            // 里 `validateValue(v).orElseGet(...)` 直接 NPE → 崩游戏（2026-10-17 实测按 B 闪退）。
            // 所以这里必须兜住：代理以后若再因映射/版本变化失效，最多退化成直接写字段，不许闪退。
            try {
                gamma.set(target);
            } catch (Throwable t) {
                LOGGER.warn("[TaczHacker][全亮] gamma.set() 抛异常，改为直接写字段兜底", t);
            }
            if (Math.abs(gamma.get() - target) <= 0.05) {
                return;
            }
        }
        if (GammaAccess.forceSet(gamma, target)) {
            LOGGER.debug("[TaczHacker][全亮] 已直接写入 gamma 字段：{}（放宽范围{}）",
                    target, GammaAccess.isWideRangeInstalled() ? "成功但值没生效" : "失败");
        }
    }

    /**
     * 玩家刚加入世界时调用：收拾上一局可能留下的「全亮残留值」
     *
     * 上一局开着全亮退出时，options.txt 里的 gamma 会是 &gt;1 的值（例如 100）。
     * 这里把它收回配置里记着的那份原始亮度；**不要去动玩家自己调的 0.0~1.0** ——
     * 旧实现无条件重置成 0.5，等于每次进世界都覆盖玩家的亮度设置。
     */
    private static void onPlayerJoinWorld(Minecraft mc) {
        wasKeyDown = false;
        savedGamma = clampToVanillaRange(HackConfig.runtimeSavedGamma);

        double current = mc.options.gamma().get();
        if (current > 1.0) {
            applyGamma(mc, savedGamma);
        }

        // 恢复上次按 B 切出来的状态：以前这里无条件置 false，
        // 结果每次重进游戏全亮都被重置掉，得重新按一遍
        fullbrightActive = HackConfig.runtimeFullbrightActive;
        if (fullbrightActive) {
            enableFullbright(mc);
        }
        LOGGER.debug("[TaczHacker][全亮] 进世界：gamma={}，记录亮度={}，全亮状态={}",
                mc.options.gamma().get(), savedGamma, fullbrightActive);
    }

    /**
     * 启用全亮
     */
    private static void enableFullbright(Minecraft mc) {
        double current = mc.options.gamma().get();
        // 只记录「原版范围内」的值当恢复点：>1 的是上一局的残留，记下来没意义
        if (current <= 1.0) {
            savedGamma = current;
            HackConfig.runtimeSavedGamma = current;
            HackConfig.saveRuntime();
        }
        applyGamma(mc, effectiveGamma());
        LOGGER.debug("[TaczHacker][全亮] 已启用：gamma {} → {}（恢复点 {}）",
                current, mc.options.gamma().get(), savedGamma);
    }

    /**
     * 禁用全亮，恢复开启前的伽马值
     */
    private static void disableFullbright(Minecraft mc) {
        applyGamma(mc, savedGamma);
        fullbrightActive = false;
        LOGGER.debug("[TaczHacker][全亮] 已禁用，恢复伽马={}", mc.options.gamma().get());
    }

    private static double clampToVanillaRange(double value) {
        if (value < 0.0) return 0.0;
        return value > 1.0 ? 1.0 : value;
    }

    /**
     * 获取当前全亮状态（供其他类查询）
     */
    public static boolean isFullbrightActive() {
        return fullbrightActive;
    }
}
