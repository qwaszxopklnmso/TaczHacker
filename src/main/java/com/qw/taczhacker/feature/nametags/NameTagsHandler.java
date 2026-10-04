package com.qw.taczhacker.feature.nametags;

import com.mojang.blaze3d.vertex.PoseStack;
import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.render.ScreenProjector;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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

import java.util.Locale;

/**
 * 功能9：实体信息牌（NameTags）
 *
 * 在目标头顶显示一块信息牌，内容是竖排的几行：
 *
 *     玩家名 / 生物种类名
 *          18/20
 *      [==========      ]      ← 血条
 *           12m                ← 距离（可选）
 *
 * 参考 FDPClient 的 visual/NameTags.kt，但砍掉了那边依懒记分板的部分：
 * 它的 HealthFromScoreboard 只对玩家生效（原版的 health criteria 也只追踪玩家），
 * 对生物没用，所以这里一律读实体血量。
 *
 * 读不到血量（服务器不同步）时显示 ?，而不是误导性的 0。
 *
 * 纯客户端渲染，不发包、不改游戏状态。投影走 {@link ScreenProjector}，和 ESP 共用。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class NameTagsHandler {

    /** 血条高度（像素） */
    private static final int BAR_HEIGHT = 3;

    /** 运行时开关（按 K 切换；配置总开关关掉时强制复位） */
    private static volatile boolean active = false;

    /** 上一 tick 按键是否按下（边缘检测） */
    private static boolean wasKeyDown = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!HackConfig.globalEnabled || !HackConfig.nameTagsEnabled) {
            active = false;
            return;
        }

        boolean keyDown = KeyBindings.NAMETAGS_KEY.isDown();
        if (keyDown && !wasKeyDown) {
            active = !active;
        }
        wasKeyDown = keyDown;
    }

    public static boolean isActive() {
        return active;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!HackConfig.globalEnabled || !HackConfig.nameTagsEnabled || !active) return;
        if (!ScreenProjector.isReady()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        double maxDistanceSq = HackConfig.nameTagsMaxDistance * HackConfig.nameTagsMaxDistance;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == self || !entity.isAlive()) continue;
            if (!(entity instanceof LivingEntity living)) continue;

            // 默认玩家 + 生物都显示，关掉 includeMobs 就只看玩家
            if (!(entity instanceof Player) && !HackConfig.nameTagsIncludeMobs) continue;

            if (self.distanceToSqr(entity) > maxDistanceSq) continue;

            // 牌子挂在 bounding box 顶端（正好头顶）
            Vec3 top = new Vec3(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
            float[] screen = ScreenProjector.project(top);
            if (screen == null) continue;

            renderTag(graphics, mc, screen[0], screen[1], entity, living, self);
        }
    }

    private static void renderTag(GuiGraphics graphics, Minecraft mc, float screenX, float screenY,
                                  Entity entity, LivingEntity living, LocalPlayer self) {
        Font font = mc.font;

        // ===== 血量 =====
        float rawHealth = living.getHealth();
        float health = rawHealth;
        if (HackConfig.nameTagsHealthAbsorption) {
            health += living.getAbsorptionAmount();
        }
        float maxHealth = Math.max(1.0F, living.getMaxHealth());

        // 活着的实体血量读到 0，基本就是服务器没同步，别显示 0 骗人
        boolean healthKnown = rawHealth > 0.0F;
        float ratio = healthKnown ? Mth.clamp(health / maxHealth, 0.0F, 1.0F) : 0.0F;

        String nameLine = HackConfig.nameTagsShowName ? entity.getName().getString() : null;
        String healthLine = HackConfig.nameTagsShowHealth
                ? formatHealth(health, maxHealth, healthKnown) : null;
        String distanceLine = HackConfig.nameTagsShowDistance
                ? Math.round(Mth.sqrt((float) self.distanceToSqr(entity))) + "m" : null;

        float scale = (float) HackConfig.nameTagsScale;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(screenX, screenY, 0.0F);
        pose.scale(scale, scale, 1.0F);

        // 先量出整块牌子的总高度：以头顶为底边**往上长**，不然会糊住实体
        int lineStep = font.lineHeight + 1;
        int totalHeight = 0;
        if (nameLine != null && !nameLine.isEmpty()) totalHeight += lineStep;
        if (healthLine != null) totalHeight += lineStep;
        if (HackConfig.nameTagsShowBar && healthKnown) totalHeight += BAR_HEIGHT + 4;
        if (distanceLine != null) totalHeight += lineStep;

        int y = -totalHeight;

        if (nameLine != null && !nameLine.isEmpty()) {
            y = drawCenteredLine(graphics, font, nameLine, y, 0xFFFFFFFF);
        }
        if (healthLine != null) {
            y = drawCenteredLine(graphics, font, healthLine, y, healthColor(ratio, healthKnown));
        }
        if (HackConfig.nameTagsShowBar && healthKnown) {
            y = drawBar(graphics, y, ratio);
        }
        if (distanceLine != null) {
            drawCenteredLine(graphics, font, distanceLine, y, 0xFFAAAAAA);
        }

        pose.popPose();
    }

    /**
     * 画一行居中文字（相对标签原点），返回下一行的 y
     */
    private static int drawCenteredLine(GuiGraphics graphics, Font font, String text, int y, int color) {
        int width = font.width(text);
        if (HackConfig.nameTagsBackground) {
            graphics.fill(-width / 2 - 2, y - 1, width / 2 + 2, y + font.lineHeight, 0x80000000);
        }
        graphics.drawString(font, text, -width / 2, y, color, true);
        return y + font.lineHeight + 1;
    }

    /**
     * 画血条（相对标签原点），返回下一行的 y
     */
    private static int drawBar(GuiGraphics graphics, int y, float ratio) {
        int barWidth = HackConfig.nameTagsBarWidth;
        int left = -barWidth / 2;

        // 底：半透明黑，比前景宽一圈当描边
        graphics.fill(left - 1, y, left + barWidth + 1, y + BAR_HEIGHT + 2, 0xC0000000);

        int filled = Mth.clamp((int) (barWidth * ratio), 0, barWidth);
        if (filled > 0) {
            graphics.fill(left, y + 1, left + filled, y + BAR_HEIGHT + 1, healthColor(ratio, true));
        }

        return y + BAR_HEIGHT + 4;
    }

    private static String formatHealth(float health, float maxHealth, boolean known) {
        if (!known) {
            return "?";
        }
        if (HackConfig.nameTagsHealthRounded) {
            return Mth.ceil(health) + "/" + Mth.ceil(maxHealth);
        }
        // 固定 Locale，免得某些系统区域设置把小数点写成逗号
        return String.format(Locale.ROOT, "%.1f/%.1f", health, maxHealth);
    }

    private static int healthColor(float ratio, boolean known) {
        if (!known) {
            return 0xFFAAAAAA;   // 灰：读不到
        }
        if (ratio > 0.66F) {
            return 0xFF55FF55;   // 绿
        }
        if (ratio > 0.33F) {
            return 0xFFFFFF55;   // 黄
        }
        return 0xFFFF5555;       // 红
    }
}
