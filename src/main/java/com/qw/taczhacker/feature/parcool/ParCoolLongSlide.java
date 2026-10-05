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
 * 结束滑铲只有一种方式（由客户端输入决定）：
 *   松开后再按一次滑铲键（ParCool 的爬行键）
 *
 * 跳跃键不参与结束滑铲。ParCool 原版在起滑时会往 BehaviorEnforcer 注册
 * 一个「屏蔽跳跃」的标记（加它的条件是「动作还在进行」，也就是只要还在滑就一直屏蔽），
 * 所以原版滑铲期间按空格没反应。要能在滑铲中起跳就得把 cancelJump 放开，
 * 这个由 ParCoolBehaviorEnforcerMixin 做（配置项 parcool.jumpWhileSliding）。
 * 放开之后跳跃和滑铲可以并存：Slide.onWorkingTickInLocalClient 设置水平速度时
 * 保留 deltaMovement 的 y 分量（`slidingVec.scale(speed).add(0, motion.y, 0)`），
 * 所以起跳的垂直速度不会被滑铲覆盖掉。
 *
 * 这些判定（canStart / canContinue / 体力 consume）在 ParCool 里全部包在
 * player.isLocalPlayer() 分支里，**只有客户端会跑**；服务端玩家 isLocalPlayer()
 * 恒为 false。所以本 mod 只要客户端装就够了，ParCool 自己会把动作状态同步过去。
 * （ParCool 本体因为 mods.toml 没写 displayTest，联机时两端仍都必须装。）
 * 按键只有客户端读得到，因此强制持续的判定加了 isClientSide 守卫：
 * 客户端一旦不再强制，canContinue 返回 false，ParCool 自己会把
 * 结束状态同步给服务端（服务端跟随客户端，不会出现脱节）。
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
     * 是否应该强制保持当前动作（供 Slide / Crawl 的 canContinue 注入使用）
     *
     * 只有客户端会返回 true：服务端不读按键，
     * 它跟随客户端同步过来的开始/结束状态即可。
     */
    public static boolean shouldForceContinue() {
        return isEnabled();
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
     * 滑铲期间是否要放开跳跃键（供 BehaviorEnforcer#cancelJump 注入使用）
     *
     * 只有「正在滑铲」时才放开：不在滑铲时一律走 ParCool 原生判定，
     * 别的动作（爬行之类）该屏蔽跳跃还是照旧屏蔽。
     */
    public static boolean shouldAllowJump() {
        return isEnabled() && HackConfig.parcoolJumpWhileSliding && isForcing();
    }

    /**
     * 当前是否真的在滑铲（供 HUD 显示、以及放开跳跃时判断）
     *
     * 两个标记取或：slideActive 是本 tick 被 Slide 注入标上的，
     * slideActiveLastTick 是上一 tick 的。起滑的第一个 tick 里
     * 跳跃判定可能跑在 canContinue 之前，只看上一 tick 的话那一下跳跃会被漏掉。
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
