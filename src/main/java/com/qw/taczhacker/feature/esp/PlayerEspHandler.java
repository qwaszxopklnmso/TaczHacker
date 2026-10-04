package com.qw.taczhacker.feature.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qw.taczhacker.config.HackConfig;
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
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 功能8：玩家 ESP（准心连线）
 *
 * 从屏幕正中心的准心向外画一条线，连到每个目标头顶在屏幕上的投影位置。
 * 纯客户端渲染，不发任何包，也不改任何游戏状态 —— 服务端装不装本 mod 都一样能用。
 *
 * 实现要点：
 * 1. 投影矩阵**从渲染管线里直接拿**（RenderLevelStageEvent 的 poseStack + projectionMatrix），
 *    不要自己用 camera.rotation().conjugate() 拼 —— 那个方向极容易搞反，
 *    一反过来就变成「视野内的目标被丢掉、视野外的反而画出来」。
 *    相机位置也一起记下来，因为那套矩阵是「相机相对」的，输入坐标要先减相机位置。
 * 2. w <= 0 表示目标在相机平面之后，必须丢弃（否则透视除法会翻到屏幕另一侧出鬼影）
 * 3. 画斜线：GuiGraphics 只能画轴对齐矩形，所以 push 一个旋转过的 PoseStack，
 *    在局部坐标里画一个「长度 x 线宽」的矩形，看起来就是一条斜线
 *
 * 全部参数（开关 / 距离 / 颜色 / 线宽 / 是否画生物）走 Cloth Config，
 * 键位默认 J，可在原版按键设置里改。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class PlayerEspHandler {

    /** 运行时开关（按 J 切换；配置总开关关掉时强制复位） */
    private static volatile boolean espActive = false;

    /** 上一 tick 按键是否按下（做边缘检测，避免按住时疯狂切换） */
    private static boolean wasKeyDown = false;

    // ============================================================
    // 每帧从世界渲染阶段抓下来的矩阵（GUI 阶段没有世界矩阵可用了）
    // ============================================================
    private static final Matrix4f FRAME_MODELVIEW = new Matrix4f();
    private static final Matrix4f FRAME_PROJECTION = new Matrix4f();
    private static double frameCamX, frameCamY, frameCamZ;
    private static boolean frameMatricesValid = false;

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

    /**
     * 在世界渲染阶段把当前的 view / projection 矩阵和相机位置存下来
     *
     * RenderGuiOverlayEvent 时世界矩阵已经被换成 GUI 的了，所以必须在这里抓。
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        FRAME_MODELVIEW.set(event.getPoseStack().last().pose());
        FRAME_PROJECTION.set(event.getProjectionMatrix());

        Vec3 cameraPos = event.getCamera().getPosition();
        frameCamX = cameraPos.x;
        frameCamY = cameraPos.y;
        frameCamZ = cameraPos.z;
        frameMatricesValid = true;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        // 挂在 hotbar 那一层之后，和 HUD 状态显示同一个时机
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!HackConfig.globalEnabled || !HackConfig.espEnabled || !espActive) return;
        if (!frameMatricesValid) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        if (self == null || mc.level == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        float centerX = mc.getWindow().getGuiScaledWidth() / 2.0F;
        float centerY = mc.getWindow().getGuiScaledHeight() / 2.0F;

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
            float[] screen = worldToScreen(mc, head);
            drawLine(graphics, centerX, centerY, screen[0], screen[1], color, lineWidth);
        }
    }

    /**
     * 世界坐标 → GUI 坐标
     *
     * 目标在相机平面之后（背对）时**不返回 null**，而是翻到屏幕外正确的一侧，
     * 这样线会一直指向那个方向，不会一转身就凭空消失。
     */
    private static float[] worldToScreen(Minecraft mc, Vec3 worldPos) {
        // 抓下来的矩阵是「相机相对」的，所以先把世界坐标平移到相机坐标系
        float relativeX = (float) (worldPos.x - frameCamX);
        float relativeY = (float) (worldPos.y - frameCamY);
        float relativeZ = (float) (worldPos.z - frameCamZ);

        Vector4f vec = new Vector4f(relativeX, relativeY, relativeZ, 1.0F);
        vec.mul(FRAME_MODELVIEW);
        vec.mul(FRAME_PROJECTION);

        float w = vec.w();

        // 用 |w| 做透视除法：w 接近 0（目标正好在视线垂直方向）时直接除会爆成无穷，
        // 用 1e-4 兜一下，结果会被后面的钳制收进屏幕外一个有限距离
        float invW = 1.0F / Math.max(1.0E-4F, Math.abs(w));
        float ndcX = vec.x() * invW;
        float ndcY = vec.y() * invW;

        // w < 0 = 目标在相机平面之后。除以负 w 已经把它翻到了镜像的一侧，
        // 再翻回来才是它「应该在」的那一边
        if (w < 0.0F) {
            ndcX = -ndcX;
            ndcY = -ndcY;
        }

        float screenW = mc.getWindow().getGuiScaledWidth();
        float screenH = mc.getWindow().getGuiScaledHeight();

        float screenX = (ndcX * 0.5F + 0.5F) * screenW;
        float screenY = (-ndcY * 0.5F + 0.5F) * screenH;

        // 背后的目标算出来可能是几百万像素，直接拿去 fill 会画一个巨长的矩形，钳一下
        float marginX = screenW * 2.0F;
        float marginY = screenH * 2.0F;
        screenX = Mth.clamp(screenX, -marginX, screenW + marginX);
        screenY = Mth.clamp(screenY, -marginY, screenH + marginY);

        return new float[]{screenX, screenY};
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
