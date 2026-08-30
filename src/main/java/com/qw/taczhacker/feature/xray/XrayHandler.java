package com.qw.taczhacker.feature.xray;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Feature 4: X-ray (Wallhack)
 *
 * 开启后跳过所有非空气方块的渲染，达到透视效果。
 * 因面剔除（face culling）问题，无法实现选择性显示特定方块，
 * 故移除了目标方块列表配置。
 *
 * Implementation:
 * 1. BlockRenderMixin intercepts ModelBlockRenderer#tesselateBlock, skips all non-air blocks
 * 2. On X-ray toggle, force reload all visible chunks
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class XrayHandler {

    private static volatile boolean xrayActive = false;
    private static boolean wasKeyDown = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (!HackConfig.globalEnabled || !HackConfig.xrayEnabled) {
            if (xrayActive) {
                xrayActive = false;
            }
            return;
        }

        boolean keyDown = KeyBindings.XRAY_KEY.isDown();
        if (keyDown && !wasKeyDown) {
            xrayActive = !xrayActive;
            if (mc.level != null && mc.levelRenderer != null) {
                forceReloadChunks(mc.level, mc.levelRenderer, mc.player.blockPosition());
            }
        }
        wasKeyDown = keyDown;
    }

    private static void forceReloadChunks(Level level, LevelRenderer levelRenderer, BlockPos center) {
        try {
            int renderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
            int sectionRadius = renderDistance + 2;

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
            // silent
        }
    }

    public static boolean isXrayActive() {
        return xrayActive;
    }
}