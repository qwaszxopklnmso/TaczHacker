package com.qw.taczhacker.mixin;

import com.alrex.parcool.api.action.ActionEntry;
import com.alrex.parcool.common.action.ActionCapabilities;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 功能7附属：解锁 ParCool 全部动作（跳过 4.0 技能树的学习要求）
 *
 * ParCool 4.0 的判定链（反编译 4.0.1.0）：
 *   Action#isPossible()            —— Action.java:127-147
 *     └ Parkourability#permit()    —— Parkourability.java:183-190
 *         learned = !entry.option().needLearning()
 *                || !config.enableSkillTree.get()
 *                || this.capabilities.can(entry)
 *         return config.get(entry).permit().get() && learned && enabledActionStates.can(entry)
 *
 * enable_skill_tree 默认 true（ParCoolConfig.java:156），needLearning 默认 true
 * （ActionOption.java:37，4.0 里只有抓钩写了 needLearning(false)），
 * 而 capabilities 在 Parkourability.java:70 用 new ActionCapabilities(registry, false) 构造，
 * **默认全部未学习**（learned 全靠花经验等级在技能树里解锁：快跑/爬行各 1 级、滑铲 10 级…）。
 * 于是 permit() 恒为 false → isPossible() 恒为 false → isReadyToStart() 恒为 false
 * → 所有 ParCool 动作都起不了手（表现：按滑铲键毫无反应，跟长滑铲开关无关）。
 *
 * 这里只拦 ActionCapabilities#can 这一个纯查询方法，让它对被问到的动作一律回答「会」：
 *   - permit() 里的 learned 项因此恒为 true，学习要求被绕过；
 *   - 不动 Parkourability#active（CTRL+P 的「Enable/Disable ParCool」仍然有效）；
 *   - 不动 config.get(entry).permit()（服务端配置里 available = false 的动作仍然被禁）。
 * 副作用只有 enabledActionStates.can 也会恒真 —— 也就是服务端同步过来的
 * 「按玩家禁用某动作」会被忽略，对作弊 mod 来说是预期行为。
 *
 * 不需要服务端也装：ParCool 的动作起手判定由 ActionOption.triggeredSide 决定执行侧，
 * 默认 CLIENT 的动作（含滑铲/爬行/快跑）只在本地客户端跑 isPossible()，
 * 服务端只跟随客户端同步过去的开始/结束状态（ActionProcessor.processAction 的 needSync）。
 * triggeredSide = SERVER 的动作（如 Breakfall）由服务端判定，那种要服务端也装本 mod。
 *
 * 本 Mixin 只在安装了 ParCool 4.0+ 时才会被应用（taczhacker.parcool.mixins.json，
 * required=false、defaultRequire=0），ParCool 不存在时不会加载本类，也不会崩溃。
 */
@Mixin(value = ActionCapabilities.class, remap = false)
public abstract class ParCoolCapabilitiesMixin {

    @Inject(
            method = "can",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void taczhacker$unlockAllActions(ActionEntry<?> action, CallbackInfoReturnable<Boolean> cir) {
        if (ParCoolLongSlide.shouldUnlockAllActions()) {
            cir.setReturnValue(true);
        }
    }
}
