package com.qw.taczhacker.feature.parcool;

import com.qw.taczhacker.config.HackConfig;

/**
 * 功能7：ParCool 长滑铲 —— 跨端共享状态（纯逻辑，不引用任何 ParCool 类）
 *
 * 原理：
 * ParCool 每 tick 通过 ActionProcessor 调用每个动作的 canContinue()，
 * 返回 false 时结束该动作。滑铲（Slide）的原生持续条件是
 *   getDoingTick() < min(客户端 SlidingContinuableTick, 服务端 MaxSlidingContinuableTick)
 * 即最多约 3 秒（服务端配置上限 60 tick），且要求父动作 Crawl 仍在进行。
 *
 * 因此本功能由两个 Mixin 实现（都只在 ParCool 存在时才加载）：
 *   - ParCoolSlideMixin  ：让 Slide#canContinue 恒为 true（滑铲不结束）
 *   - ParCoolCrawlMixin  ：滑铲期间让 Crawl#canContinue 恒为 true（父动作不掉）
 *
 * 结束滑铲（三种方式，都由客户端输入决定）：
 *   1. 按跳跃键 —— ParCool 原版就是靠跳跃退出滑铲。因为滑铲期间
 *      Slide 注册了「屏蔽跳跃」的 BehaviorEnforcer 标记，而标记的存活条件
 *      正是「动作还在进行」，所以只要不再强制持续，跳跃立刻生效。
 *   2. 松开后再按一次滑铲键（ParCool 的爬行键）
 *   3. 按本 mod 的「取消长滑铲」键（默认 Z）
 *
 * 客户端与服务端都会执行这段判定，所以两端都要装 ParCool + 本 mod 才有效。
 * 按键只有客户端读得到，因此强制持续的判定加了 isClientSide 守卫：
 * 客户端一旦不再强制，canContinue 返回 false，ParCool 自己会把
 * 结束状态同步给服务端（服务端跟随客户端，不会出现脱节）。
 */
public final class ParCoolLongSlide {

    /** 取消键是否被按下（每 tick 由客户端处理器刷新） */
    private static volatile boolean cancelKeyHeld = false;

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
     * 是否应该强制保持当前动作（供 Slide / Crawl 的 canContinue 注入使用）
     *
     * 只有客户端会返回 true：服务端不读按键，
     * 它跟随客户端同步过来的开始/结束状态即可。
     */
    public static boolean shouldForceContinue() {
        return isEnabled() && !cancelKeyHeld;
    }

    /**
     * 是否应该忽略 ParCool 体力消耗（供体力实现的 consume 注入使用）
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
     */
    public static boolean isForcing() {
        return slideActiveLastTick && isEnabled();
    }

    // ============================================================
    // 客户端 API
    // ============================================================

    /**
     * 刷新取消键状态（由客户端按键处理器调用）
     */
    public static void setCancelKeyHeld(boolean held) {
        cancelKeyHeld = held;
    }

    /**
     * 本 mod 的「取消长滑铲」键当前是否按下
     */
    public static boolean isCancelKeyHeld() {
        return cancelKeyHeld;
    }

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
        cancelKeyHeld = false;
        slideActive = false;
        slideActiveLastTick = false;
        slideKeyPressed = false;
        slideKeyJustPressed = false;
    }
}
