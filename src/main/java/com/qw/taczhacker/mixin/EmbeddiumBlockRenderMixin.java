package com.qw.taczhacker.mixin;

import com.qw.taczhacker.feature.xray.XrayHandler;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Embeddium X-ray 兼容 Mixin
 *
 * 注入到 Embeddium 的 BlockRenderer.renderModel 方法，跳过非目标方块的渲染。
 * 直接使用 Embeddium 的 API 类型，无需反射。
 *
 * 此 Mixin 在单独的配置文件中（taczhacker.embeddium.mixins.json），
 * required=false，当 Embeddium 不存在时不会加载，不影响游戏启动。
 */
@Mixin(value = BlockRenderer.class, remap = false)
public class EmbeddiumBlockRenderMixin {

    @Dynamic("Embeddium compatibility: skip non-target blocks for X-ray")
    @Inject(
            method = "renderModel",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onRenderModel(BlockRenderContext context, ChunkBuildBuffers buffers, CallbackInfo ci) {
        try {
            if (!XrayHandler.isXrayActive()) return;

            BlockState state = context.state();

            if (state.getBlock() instanceof AirBlock) return;

            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blockId == null) return;

            if (!XrayHandler.isTargetBlock(blockId)) {
                ci.cancel();
            }
        } catch (Exception e) {
            // 异常保护，不阻止区块重建
        }
    }
}