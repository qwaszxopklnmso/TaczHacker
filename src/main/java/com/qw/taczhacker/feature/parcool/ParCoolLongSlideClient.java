package com.qw.taczhacker.feature.parcool;

import com.qw.taczhacker.Taczhacker;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

/**
 * 功能7：ParCool 长滑铲 —— 客户端按键处理
 *
 * 此类的存在意义是「不引用任何 ParCool 类」：
 * 真正的滑铲逻辑由 ParCoolSlideMixin / ParCoolCrawlMixin 完成，
 * 它们只在 ParCool 存在时才会被加载，因此这里不需要考虑 ParCool 缺失。
 *
 * 每 tick 在 START 阶段刷新按键状态（必须早于玩家 tick 里的 ParCool 动作判定）：
 *   - 本 mod 的「取消长滑铲」键（默认 Z）
 *   - ParCool 的滑铲键（爬行键，默认 C）—— 通过 ParCoolKeyAccess 反射式读取，
 *     它会吞掉 ClassNotFound，所以没装 ParCool 也不会崩
 * 在 END 阶段收尾滑铲状态（供 HUD 显示）。
 */
@Mod.EventBusSubscriber(modid = Taczhacker.MODID, value = Dist.CLIENT)
public class ParCoolLongSlideClient {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();

        if (event.phase == TickEvent.Phase.END) {
            // 收尾：如果本 tick 没有被 Slide 注入标记，说明滑铲已经结束
            ParCoolLongSlide.finishClientTick();
            return;
        }

        if (mc.player == null) {
            // 离开世界：复位状态，避免下次进世界残留
            ParCoolKeyAccess.reset();
            ParCoolLongSlide.reset();
            return;
        }

        if (!ParCoolLongSlide.isEnabled() || !ModList.get().isLoaded("parcool")) {
            ParCoolLongSlide.reset();
            return;
        }

        // 取消键（每 tick 刷新，不再用 consumeClick，避免和自动重复的按键语义混淆）
        ParCoolLongSlide.setCancelKeyHeld(KeyBindings.LONG_SLIDE_CANCEL_KEY.isDown());

        // 滑铲键（ParCool 的爬行键）：松开后再按一次 = 退出滑铲
        ParCoolLongSlide.updateSlideKey(ParCoolKeyAccess.isSlideKeyDown(mc));
    }
}
