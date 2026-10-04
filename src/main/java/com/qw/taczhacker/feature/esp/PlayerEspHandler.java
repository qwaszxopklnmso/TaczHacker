package com.qw.taczhacker.feature.esp;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.render.ScreenProjector;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
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
 * 功能8：ESP（准心连线 / 方框 / 骨骼）
 *
 * 三种画法挂在同一个开关（默认 J）下，各自在配置里可以单独开关：
 *   - 连线：从屏幕正中心的准心连到目标头顶的投影位置
 *   - 方框：包围盒 8 个角投影到屏幕后取 min/max
 *   - 骨骼：按包围盒估出关节点再连线（棍状人）
 *
 * 全部是纯客户端渲染，不发包、不改服务端状态 —— 服务端装不装本 mod 都一样能用。
 *
 * 投影那套（矩阵抓取 + 相机空间分段处理）在 {@link ScreenProjector} 里，和 NameTags 共用；
 * 具体怎么画在 {@link EspRenderer} 里。
 *
 * 所有参数走 Cloth Config，键位默认 J，可在原版按键设置里改。
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

        if (!HackConfig.globalEnabled) {
            espActive = false;
            return;
        }

        boolean keyDown = KeyBindings.ESP_KEY.isDown();
        if (keyDown && !wasKeyDown) {
            if (!HackConfig.espEnabled) {
                // 配置关着时按键也要有反应：按一次直接打开配置并显示
                HackConfig.espEnabled = true;
                HackConfig.save();
                espActive = true;
            } else {
                espActive = !espActive;
            }
        }
        wasKeyDown = keyDown;

        // 配置被外部关掉时强制复位
        if (!HackConfig.espEnabled) {
            espActive = false;
        }
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
                // 开了「也给生物画」才画
            } else {
                continue;
            }

            if (self.distanceToSqr(entity) > maxDistanceSq) continue;

            if (HackConfig.espDrawLine) {
                // 瞄 bounding box 顶端（正好头顶）。以前多加了 0.2，线看着会浮在头上
                Vec3 head = new Vec3(entity.getX(), entity.getBoundingBox().maxY, entity.getZ());
                float[] screen = ScreenProjector.project(head);
                if (screen != null) {
                    EspRenderer.drawLine(graphics, centerX, centerY, screen[0], screen[1], color, lineWidth);
                }
            }

            // 方框和骨骼要求位置准确，相机平面之后的目标投影只剩方向是对的，直接跳过
            boolean inFront = ScreenProjector.isInFront(entity.getBoundingBox().getCenter());

            if (HackConfig.espDrawBox && inFront) {
                EspRenderer.drawBox(graphics, entity.getBoundingBox(), color, lineWidth);
            }

            if (HackConfig.espDrawSkeleton && inFront && entity instanceof LivingEntity living) {
                EspRenderer.drawSkeleton(graphics, living, color, lineWidth);
            }
        }
    }
}
