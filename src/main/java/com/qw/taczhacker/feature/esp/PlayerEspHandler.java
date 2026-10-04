package com.qw.taczhacker.feature.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.render.ScreenProjector;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 功能8：玩家 ESP（准心连线）
 *
 * 从屏幕正中心的准心向外画一条线，连到每个目标头顶在屏幕上的投影位置。
 * 纯客户端渲染，不发任何包，也不改任何游戏状态 —— 服务端装不装本 mod 都一样能用。
 *
 * 投影那套（矩阵抓取 + 相机空间分段处理）在 {@link ScreenProjector} 里，和 NameTags 共用。
 *
 * 画斜线：GuiGraphics 只能画轴对齐矩形，所以 push 一个「平移到起点 + 绕 Z 轴旋转到线的方向」
 * 的 PoseStack，在局部坐标里画一个「长度 x 线宽」的矩形。
 *
 * 全部参数（开关 / 距离 / 颜色 / 线宽 / 是否画生物 / 血量）走 Cloth Config，
 * 键位默认 J，可在原版按键设置里改。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class PlayerEspHandler {

    /** 运行时开关（按 J 切换；配置总开关关掉时强制复位） */
    private static volatile boolean espActive = false;

    /** 上一 tick 按键是否按下（做边缘检测，避免按住时疯狂切换） */
    private static boolean wasKeyDown = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!HackConfig.globalEnabled || !HackConfig.espEnabled) {
            espActive = false;
            return;
        }

        boolean keyDown = KeyBindings.ESP_KEY.isDown();
        if (keyDown && !wasKeyDown) {
            espActive = !espActive;
        }
        wasKeyDown = keyDown;
    }

    public static boolean isEspActive() {
        return espActive;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        // 挂在 hotbar 那一层之后，和 HUD 状态显示同一时机
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!HackConfig.globalEnabled || !HackConfig.espEnabled || !espActive) return;
        if (!ScreenProjector.isReady()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        float centerX = ScreenProjector.screenWidth() / 2.0F;
        float centerY = ScreenProjector.screenHeight() / 2.0F;

        // 兜底：不管配置里存的是什么，线条永远不透明（alpha=0 会整条线看不见）
        int color = HackConfig.espColor | 0xFF000000;
        float lineWidth = (float) HackConfig.espLineWidth;
        double maxDistanceSq = HackConfig.espMaxDistance * HackConfig.espMaxDistance;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == self || !entity.isAlive()) continue;

            if (entity instanceof Player) {
                // 玩家一律画
            } else if (HackConfig.espIncludeMobs && entity instanceof LivingEntity) {
                // 开了「也给生物画线」才画
            } else {
                continue;
            }

            if (self.distanceToSqr(entity) > maxDistanceSq) continue;

            // 瞄 bounding box 顶端（正好头顶）。以前多加了 0.2，线看着会浮在头上
            Vec3 head = new Vec3(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
            float[] screen = ScreenProjector.project(head);
            if (screen == null) continue;

            drawLine(graphics, centerX, centerY, screen[0], screen[1], color, lineWidth);

            if (HackConfig.espShowHealth && entity instanceof LivingEntity living) {
                drawHealth(graphics, mc, screen[0], screen[1], living);
            }
        }
    }

    /**
     * 在目标头顶（线的末端）画血量文字
     *
     * 数据来源是客户端拿到的实体血量。参考 FDPClient 的 NameTags：
     * 有些服务器根本不把实体血量同步给客户端，那边会退化成读记分板的 health objective
     * （而且只对玩家有效）。这里先用最直接的方式。
     */
    private static void drawHealth(GuiGraphics graphics, Minecraft mc, float screenX, float screenY,
                                    LivingEntity living) {
        int maxHealth = Mth.ceil(living.getMaxHealth());
        if (maxHealth <= 0) return;

        int health = Mth.ceil(living.getHealth());
        String text = health + "/" + maxHealth;

        float ratio = (float) health / (float) maxHealth;
        int color;
        if (ratio > 0.66F) {
            color = 0xFF55FF55;   // 绿
        } else if (ratio > 0.33F) {
            color = 0xFFFFFF55;   // 黄
        } else {
            color = 0xFFFF5555;   // 红
        }

        int textX = Math.round(screenX) - mc.font.width(text) / 2;
        int textY = Math.round(screenY) - mc.font.lineHeight - 1;
        graphics.drawString(mc.font, text, textX, textY, color, true);
    }

    /**
     * 在 GUI 上画一条任意方向的线
     *
     * GuiGraphics#fill 只能画轴对齐矩形，所以先把坐标系平移到起点、旋转到线的方向，
     * 再画一个「长度 x 线宽」的矩形。
     */
    private static void drawLine(GuiGraphics graphics, float x1, float y1, float x2, float y2,
                                 int color, float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 1.0F) return;

        int len = Math.max(1, Math.round(length));
        int thick = Math.max(1, Math.round(width));

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x1, y1, 0.0F);
        pose.mulPose(Axis.ZP.rotation((float) Mth.atan2(dy, dx)));
        graphics.fill(0, 0, len, thick, color);
        pose.popPose();
    }
}
