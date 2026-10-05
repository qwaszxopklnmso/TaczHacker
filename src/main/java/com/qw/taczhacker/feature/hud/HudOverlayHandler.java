package com.qw.taczhacker.feature.hud;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.aim.AimHandler;
import com.qw.taczhacker.feature.aim.FakeAimHandler;
import com.qw.taczhacker.feature.aimbot.AimbotHandler;
import com.qw.taczhacker.feature.esp.PlayerEspHandler;
import com.qw.taczhacker.feature.fakerot.FakeRotationHandler;
import com.qw.taczhacker.feature.flight.FlightHandler;
import com.qw.taczhacker.feature.fullbright.FullbrightHandler;
import com.qw.taczhacker.feature.nametags.NameTagsHandler;
import com.qw.taczhacker.feature.parcool.ParCoolLongSlide;
import com.qw.taczhacker.feature.xray.XrayHandler;
import com.qw.taczhacker.network.ServerDetector;
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

    /** 功能条目：名称、配置开关、运行时状态、配置关闭时是否也显示 */
    private static class Entry {
        final String name;
        final java.util.function.BooleanSupplier configEnabled;
        final java.util.function.BooleanSupplier runtimeActive;
        final boolean alwaysShow;

        Entry(String name, java.util.function.BooleanSupplier configEnabled,
              java.util.function.BooleanSupplier runtimeActive) {
            this(name, configEnabled, runtimeActive, false);
        }

        Entry(String name, java.util.function.BooleanSupplier configEnabled,
              java.util.function.BooleanSupplier runtimeActive, boolean alwaysShow) {
            this.name = name;
            this.configEnabled = configEnabled;
            this.runtimeActive = runtimeActive;
            this.alwaysShow = alwaysShow;
        }
    }

    private static final List<Entry> entries = new ArrayList<>();

    static {
        // 功能1：开火静默自瞄（无运行时状态，配置即开关）
        entries.add(new Entry("自瞄", () -> HackConfig.aimEnabled, () -> AimHandler.isActive()));
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
        // alwaysShow：曾经因为「长滑铲」配置开关被关着，HUD 里这一行不出现，
        // 于是滑铲恢复原生行为（滑 1 秒就停）被当成功能失效，所以这一行改成常驻显示。
        entries.add(new Entry("长滑铲", () -> HackConfig.parcoolLongSlideEnabled,
                () -> ParCoolLongSlide.isForcing(), true));
        // 功能8：ESP（连线/方框/骨骼，按 J 切换）
        entries.add(new Entry("ESP", () -> HackConfig.espEnabled,
                () -> PlayerEspHandler.isEspActive()));
        // 功能9：实体信息牌（按 K 切换）
        entries.add(new Entry("信息牌", () -> HackConfig.nameTagsEnabled,
                () -> NameTagsHandler.isActive()));
        // 单机专属的两个「真·」：没有按键，纯配置项，所以 alwaysShow。
        // 显示的是「实际生效」而不是「本地配置开着」—— 联机时服务端没开就是 OFF。
        // 不 alwaysShow 的话，默认关着时这两行根本不出现，等于没法在 HUD 里看到它们
        entries.add(new Entry("追踪弹", () -> HackConfig.aimSinglePlayerHomingBullet,
                () -> ServerDetector.isServerHomingEnabled(), true));
        entries.add(new Entry("穿墙子弹", () -> HackConfig.aimSinglePlayerBulletPenetration,
                () -> ServerDetector.isServerPenetrationEnabled(), true));
        // 附加：Tacz 假开镜（服务端认为你在开镜，客户端 FOV / 灵敏度 / 移速全不变）
        entries.add(new Entry("假开镜", () -> HackConfig.taczFakeAim,
                () -> FakeAimHandler.isFakeAiming(), true));
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
            boolean active = entry.runtimeActive.getAsBoolean();

            // 配置关闭时跳过该行（标记了 alwaysShow 的除外）
            if (!entry.alwaysShow && !entry.configEnabled.getAsBoolean()) continue;

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