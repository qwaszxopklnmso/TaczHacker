package com.qw.taczhacker.feature.parcool;

import com.qw.taczhacker.config.HackConfig;

/**
 * 功能7：ParCool 长滑铲 —— 跨端共享状态（纯逻辑，不引用任何 ParCool 类）
 *
 * 原理（针对 ParCool 4.0.1.0）：
 * ParCool 每 tick 通过 ActionProcessor 调用每个动作的 canContinue()，
 * 返回 false 时结束该动作。滑铲（Slide）的原生持续条件只有一条：
 *   getDoingTick() < 20（Slide.MAX_TICK，即 1 秒）
 * 并且要求父动作 Crawl 仍在进行（Crawl 的持续条件依赖爬行键是否按住）。
 *
 * 所以本功能由两个 Mixin 实现（都只在 ParCool 存在时才加载）：
 *   - ParCoolSlideMixin ：让 Slide#canContinue 恒为 true（滑铲不结束），
 *                        并在起滑时重挂 BehaviorEnforcer 的移动强制，
 *                        把速度衰减进度钳在 20 tick（否则 ParCool 会线性外推到负数）
 *   - ParCoolCrawlMixin ：滑铲期间让 Crawl#canContinue 恒为 true（父动作不掉）
 *
 * 结束滑铲只有一种方式（由客户端输入决定）：
 *   松开后再按一次滑铲键（ParCool 的爬行键）
 *
 * 跳跃键不参与结束滑铲：ParCool 4.0.1.0 原版滑铲期间已经不再屏蔽跳跃
 * （BehaviorEnforcer 里没有任何动作往 noJumpMarks 注册标记），所以按空格就是正常起跳。
 *
 * 这些判定（canStart / canContinue）在 ParCool 4.0 里由 ActionOption.triggeredSide 决定执行侧，
 * 滑铲与爬行都用默认值 LogicalSide.CLIENT，配合 ActionProcessor 里的
 *   needSync = triggeredSide.isClient() && player.isLocalPlayer()
 * 意味着**只有本地客户端会跑 canContinue**；服务端跟随客户端同步过来的开始/结束状态。
 * 按键只有客户端读得到，所以强制持续的判定只在客户端生效，不会出现两端脱节。
 * （ParCool 本体因为 mods.toml 没写 displayTest，联机时两端仍都必须装。）
 */
public final class ParCoolLongSlide {

    /** 本 tick 的滑铲状态（由 Slide 注入每 tick 标记） */
    private static volatile boolean slideActive = false;

    /** 上一 tick 的滑铲状态（供 HUD 显示，因为标记会在每 tick 结束时清掉） */
    private static volatile boolean slideActiveLastTick = false;

    /** 滑铲键（ParCool 爬行键）当前是否按下 */
    private static boolean slideKeyPressed = false;

    /** 滑铲键是否本 tick 刚刚按下（松开后再按 = 退出滑铲） */
    private static volatile boolean slideKeyJustPressed = false;

    private ParCoolLongSlide() {
    }

    /**
     * 功能是否开启（需要全局开关 + 本功能开关）
     */
    public static boolean isEnabled() {
        return HackConfig.globalEnabled && HackConfig.parcoolLongSlideEnabled;
    }

    /**
     * 是否应该忽略 ParCool 体力消耗（供 Action#takeCost 注入使用）
     */
    public static boolean shouldIgnoreStamina() {
        return HackConfig.globalEnabled && HackConfig.parcoolInfiniteStamina;
    }

    /**
     * 滑铲方向是否跟随视角（供 Slide 注入使用）
     */
    public static boolean isSteerableSlide() {
        return isEnabled() && HackConfig.parcoolSteerableSlide;
    }

    /**
     * 当前是否真的在滑铲（供 HUD 显示）
     *
     * 两个标记取或：slideActive 是本 tick 被 Slide 注入标上的，
     * slideActiveLastTick 是上一 tick 的。
     */
    public static boolean isForcing() {
        return (slideActive || slideActiveLastTick) && isEnabled();
    }

    // ============================================================
    // 客户端 API
    // ============================================================

    /**
     * 刷新滑铲键状态（由客户端按键处理器每 tick 调用）
     *
     * @param pressed 本 tick 滑铲键（ParCool 爬行键）是否按下
     */
    public static void updateSlideKey(boolean pressed) {
        slideKeyJustPressed = pressed && !slideKeyPressed;
        slideKeyPressed = pressed;
    }

    /**
     * 滑铲键是否刚刚被按下（松开后再按一次 = 退出滑铲）
     */
    public static boolean isSlideKeyJustPressed() {
        return slideKeyJustPressed;
    }

    /**
     * 记录「本地玩家当前正在滑铲」（由 Slide 注入在滑铲进行中每 tick 调用）
     */
    public static void markSliding() {
        slideActive = true;
    }

    /**
     * 本 tick 结束时调用（客户端按键处理器的 ClientTickEvent.END）：
     * 若上一 tick 还在滑铲、这一 tick 没有被标记，说明滑铲已经结束。
     */
    public static void finishClientTick() {
        slideActiveLastTick = slideActive;
        slideActive = false;
    }

    /**
     * 退出世界 / 功能关闭时复位
     */
    public static void reset() {
        slideActive = false;
        slideActiveLastTick = false;
        slideKeyPressed = false;
        slideKeyJustPressed = false;
    }
}
