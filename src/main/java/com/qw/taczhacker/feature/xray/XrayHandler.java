package com.qw.taczhacker.feature.xray;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 功能4：透视（X-ray）
 *
 * 实现原理：
 * 1. BlockRenderMixin 在 ModelBlockRenderer#tesselateBlock 中拦截，跳过非目标方块渲染
 * 2. 切换 X-ray 时，全量刷新区块（forceReloadChunks），确保所有区块一致更新
 * 3. 覆盖色（TRANSLUCENT 模式）在 AFTER_LEVEL 阶段渲染，提供半透明背景防掉坑
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class XrayHandler {

    /** 透视开关状态（volatile 确保多线程可见性） */
    private static volatile boolean xrayActive = false;

    /** 上次按键状态 */
    private static boolean wasKeyDown = false;

    /**
     * 自定义 RenderType：无深度测试的半透明渲染，用于覆盖色
     */
    private static final RenderType OVERLAY_RENDER_TYPE = RenderType.create(
            "taczhacker_overlay",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderType.ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(new RenderType.TransparencyStateShard("translucent_transparency",
                            () -> {
                                RenderSystem.enableBlend();
                                RenderSystem.defaultBlendFunc();
                            },
                            () -> RenderSystem.disableBlend()
                    ))
                    .setDepthTestState(new RenderType.DepthTestStateShard("always", 519))
                    .setCullState(new RenderType.CullStateShard(false))
                    .setLightmapState(new RenderType.LightmapStateShard(false))
                    .setOverlayState(new RenderType.OverlayStateShard(false))
                    .setWriteMaskState(new RenderType.WriteMaskStateShard(true, true))
                    .createCompositeState(false)
    );

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 全局开关检查
        if (!HackConfig.globalEnabled || !HackConfig.xrayEnabled) {
            if (xrayActive) {
                xrayActive = false;
            }
            return;
        }

        // 按键切换
        boolean keyDown = KeyBindings.XRAY_KEY.isDown();
        if (keyDown && !wasKeyDown) {
            xrayActive = !xrayActive;
            // 切换时全量刷新所有可见区块，确保 X-ray 效果一致
            if (mc.level != null && mc.levelRenderer != null) {
                forceReloadChunks(mc.level, mc.levelRenderer, mc.player.blockPosition());
            }
        }
        wasKeyDown = keyDown;
    }

    /**
     * 强制刷新所有可见区块
     * 遍历玩家视距内的所有区块 Section，标记为脏数据
     */
    private static void forceReloadChunks(Level level, LevelRenderer levelRenderer, BlockPos center) {
        try {
            // 获取渲染视距（格）
            int renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
            // 区块 Section 是 16x16x16 的立方体，计算视距内的 Section 范围
            int sectionRadius = renderDistance + 2; // 多冗余 2 个 Section 确保覆盖

            int centerSectionX = SectionPos.blockToSectionCoord(center.getX());
            int centerSectionY = SectionPos.blockToSectionCoord(center.getY());
            int centerSectionZ = SectionPos.blockToSectionCoord(center.getZ());

            int minSectionY = SectionPos.blockToSectionCoord(level.getMinBuildHeight());
            int maxSectionY = SectionPos.blockToSectionCoord(level.getMaxBuildHeight());

            for (int x = centerSectionX - sectionRadius; x <= centerSectionX + sectionRadius; x++) {
                for (int z = centerSectionZ - sectionRadius; z <= centerSectionZ + sectionRadius; z++) {
                    for (int y = minSectionY; y <= maxSectionY; y++) {
                        levelRenderer.setSectionDirty(x, y, z);
                    }
                }
            }
        } catch (Exception e) {
            // 静默处理，不影响玩家体验
        }
    }

    /**
     * 渲染覆盖色
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;

        if (!xrayActive) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // 渲染半透明覆盖色（TRANSLUCENT 模式）
        if (HackConfig.xrayMode == HackConfig.XrayMode.TRANSLUCENT) {
            renderOverlay(event);
        }
    }

    /**
     * 渲染半透明覆盖色（围绕相机的彩色立方体）
     * 提供背景色，让玩家在透视时仍能看到地形轮廓，防止掉坑
     */
    private static void renderOverlay(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();

        int color = HackConfig.xrayOverlayColor;
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        if (a == 0) return;

        VertexConsumer buffer = mc.renderBuffers().bufferSource().getBuffer(OVERLAY_RENDER_TYPE);

        poseStack.pushPose();
        // RenderLevelStageEvent 的 PoseStack 是相机相对坐标，相机在原点
        Matrix4f matrix = poseStack.last().pose();

        float size = 256.0f;

        // 围绕相机(原点)的立方体，6个面
        // 顶面
        addQuad(matrix, buffer, -size, size, -size, size, size, size, r, g, b, a);
        // 底面
        addQuad(matrix, buffer, -size, -size, -size, size, -size, size, r, g, b, a);
        // 前面 (z正方向)
        addQuadVertical(matrix, buffer, -size, -size, size, size, size, size, r, g, b, a, 0, 0, 1);
        // 后面 (z负方向)
        addQuadVertical(matrix, buffer, -size, -size, -size, size, size, -size, r, g, b, a, 0, 0, -1);
        // 左面 (x负方向)
        addQuadLeftRight(matrix, buffer, -size, -size, -size, -size, size, size, r, g, b, a, -1, 0, 0);
        // 右面 (x正方向)
        addQuadLeftRight(matrix, buffer, size, -size, -size, size, size, size, r, g, b, a, 1, 0, 0);

        poseStack.popPose();
        mc.renderBuffers().bufferSource().endBatch(OVERLAY_RENDER_TYPE);
    }

    private static void addQuad(Matrix4f matrix, VertexConsumer buffer,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                int r, int g, int b, int a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y1, z2).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2, y1, z2).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a).endVertex();
    }

    private static void addQuadVertical(Matrix4f matrix, VertexConsumer buffer,
                                        float x1, float y1, float z1,
                                        float x2, float y2, float z2,
                                        int r, int g, int b, int a,
                                        float nx, float ny, float nz) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x2, y2, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x1, y2, z1).color(r, g, b, a).endVertex();
    }

    private static void addQuadLeftRight(Matrix4f matrix, VertexConsumer buffer,
                                         float x, float y1, float z1,
                                         float x_, float y2, float z2,
                                         int r, int g, int b, int a,
                                         float nx, float ny, float nz) {
        buffer.vertex(matrix, x, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x, y1, z2).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x, y2, z2).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, x, y2, z1).color(r, g, b, a).endVertex();
    }

    public static boolean isXrayActive() {
        return xrayActive;
    }

    public static boolean isTargetBlock(ResourceLocation blockId) {
        if (HackConfig.xrayTargetBlocks == null) return false;
        return HackConfig.xrayTargetBlocks.contains(blockId.toString());
    }
}