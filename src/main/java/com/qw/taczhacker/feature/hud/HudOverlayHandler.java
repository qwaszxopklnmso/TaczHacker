package com.qw.taczhacker.feature.hud;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.aimbot.AimbotHandler;
import com.qw.taczhacker.feature.esp.PlayerEspHandler;
import com.qw.taczhacker.feature.fakerot.FakeRotationHandler;
import com.qw.taczhacker.feature.flight.FlightHandler;
import com.qw.taczhacker.feature.fullbright.FullbrightHandler;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import com.qw.taczhacker.feature.xray.XrayHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD 开关状态显示
 *
 * 在屏幕左上角统一显示所有功能的开启/关闭状态。
 * 每个功能显示一行，格式：功能名: ON（绿色）/ OFF（灰色）
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class HudOverlayHandler {

    /** 功能条目：名称、配置开关、运行时状态 */
    private static class Entry {
        final String name;
        final java.util.function.BooleanSupplier configEnabled;
        final java.util.function.BooleanSupplier runtimeActive;

        Entry(String name, java.util.function.BooleanSupplier configEnabled, java.util.function.BooleanSupplier runtimeActive) {
            this.name = name;
            this.configEnabled = configEnabled;
            this.runtimeActive = runtimeActive;
        }
    }

    private static final List<Entry> entries = new ArrayList<>();

    static {
        // 功能1：开火静默自瞄（无运行时状态，配置即开关）
        entries.add(new Entry("自瞄", () -> HackConfig.aimEnabled, () -> HackConfig.aimEnabled));
        // 功能2：低头转圈
        entries.add(new Entry("转圈", () -> HackConfig.fakerotEnabled, () -> FakeRotationHandler.isEnabled()));
        // 功能3：视角锁定自瞄
        entries.add(new Entry("视角锁定", () -> HackConfig.aimbotEnabled, () -> AimbotHandler.isActive()));
        // 功能4：透视
        entries.add(new Entry("透视", () -> HackConfig.xrayEnabled, () -> XrayHandler.isXrayActive()));
        // 功能5：飞行挂
        entries.add(new Entry("飞行", () -> HackConfig.flightEnabled, () -> FlightHandler.isFlying()));
        // 功能6：伽马值修改
        entries.add(new Entry("全亮", () -> HackConfig.fullbrightEnabled, () -> FullbrightHandler.isFullbrightActive()));
        // 功能7：ParCool 长滑铲（运行时状态在 ParCool 内部，这里显示功能开关）
        entries.add(new Entry("长滑铲", () -> HackConfig.parcoolLongSlideEnabled,
                () -> ParCoolLongSlide.isForcing()));
        // 功能8：玩家 ESP（按 J 切换）
        entries.add(new Entry("玩家ESP", () -> HackConfig.espEnabled,
                () -> PlayerEspHandler.isEspActive()));
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) return;

        // 全局关闭时什么都不显示
        if (!HackConfig.globalEnabled) return;

        Font font = mc.font;
        int x = 4;
        int y = 4;

        for (Entry entry : entries) {
            boolean configOn = entry.configEnabled.getAsBoolean();
            boolean active = entry.runtimeActive.getAsBoolean();

            // 配置关闭时跳过该行
            if (!configOn) continue;

            String text;
            int color;
            if (active) {
                text = entry.name + ": ON";
                color = 0xFF55FF55; // 绿色
            } else {
                text = entry.name + ": OFF";
                color = 0xFFAAAAAA; // 灰色
            }

            // 半透明背景
            int w = font.width(text);
            event.getGuiGraphics().fill(x - 1, y - 1, x + w + 1, y + font.lineHeight + 1, 0x80000000);
            // 文字
            event.getGuiGraphics().drawString(font, text, x, y, color, true);

            y += font.lineHeight + 2;
        }
    }
}