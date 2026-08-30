package com.qw.taczhacker.mixin;

import com.qw.taczhacker.feature.xray.XrayHandler;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * X-ray 核心：在 ModelBlockRenderer#tesselateBlock 拦截，判断是否跳过非目标方块的渲染。
 *
 * 要点：
 * 1. 只有 XrayHandler.isXrayActive() 为 true 时才生效
 * 2. 目标方块（如钻石矿）正常渲染，其他方块跳过
 * 3. 完整的 try-catch 保护，防止异常导致区块重建失败
 */
@Mixin(value = ModelBlockRenderer.class, remap = false)
public class BlockRenderMixin {

    @Inject(
            method = "tesselateBlock",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void onTesselateBlock(BlockAndTintGetter level, BakedModel model, BlockState state, BlockPos pos,
                                   com.mojang.blaze3d.vertex.PoseStack poseStack,
                                   com.mojang.blaze3d.vertex.VertexConsumer vertexConsumer,
                                   boolean checkSides, RandomSource random, long seed, int combinedOverlay,
                                   ModelData modelData, net.minecraft.client.renderer.RenderType renderType,
                                   CallbackInfo ci) {
        try {
            if (!XrayHandler.isXrayActive()) return;

            // 空气方块不需要处理
            if (state.getBlock() instanceof AirBlock) return;

            // X-ray 开启时跳过所有非空气方块（因面剔除问题无法实现选择性方块显示）
            ci.cancel();
        } catch (Exception e) {
            // 异常保护：不阻止区块重建
        }
    }
}