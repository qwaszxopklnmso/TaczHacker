package com.qw.taczhacker.mixin;

import com.qw.taczhacker.feature.fakerot.FakeRotationHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * LocalPlayer Mixin
 *
 * 功能2（低头转圈）的核心实现：
 * 修改 LocalPlayer#sendPosition() 中发送的旋转包，将 yaw/pitch 替换为假旋转值。
 * 本地视角完全正常不受影响，因为 mixin 只修改了包内的旋转值，不修改 player 字段。
 *
 * 功能1（开火静默自瞄）也通过此 mixin 在开火时临时覆盖旋转。
 *
 * 使用 SRG 方法名 m_108640_（即 sendPosition 的 Searge 映射名），
 * remap = false 避免编译期查找混淆映射，运行时直接用 SRG 名定位方法。
 */
@Mixin(value = LocalPlayer.class, remap = false)
public class LocalPlayerMixin {

    /**
     * 修改 ServerboundMovePlayerPacket.Rot 中的 yaw
     */
    @ModifyArg(
            method = "m_108640_", //sendPosition
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$Rot;<init>(FFZ)V"
            ),
            index = 0,
            remap = false
    )
    private float modifyYawInRot(float yaw) {
        if (FakeRotationHandler.isEnabled()) {
            return FakeRotationHandler.getFakeYaw();
        }
        return yaw;
    }

    /**
     * 修改 ServerboundMovePlayerPacket.Rot 中的 pitch
     */
    @ModifyArg(
            method = "m_108640_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$Rot;<init>(FFZ)V"
            ),
            index = 1,
            remap = false
    )
    private float modifyPitchInRot(float pitch) {
        if (FakeRotationHandler.isEnabled()) {
            return FakeRotationHandler.getFakePitch();
        }
        return pitch;
    }

    /**
     * 修改 ServerboundMovePlayerPacket.PosRot 中的 yaw
     * 当玩家位置变化时，原版会发送 PosRot 包（同时包含位置和旋转）
     */
    @ModifyArg(
            method = "m_108640_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$PosRot;<init>(DDDFFZ)V"
            ),
            index = 3,
            remap = false
    )
    private float modifyYawInPosRot(float yaw) {
        if (FakeRotationHandler.isEnabled()) {
            return FakeRotationHandler.getFakeYaw();
        }
        return yaw;
    }

    /**
     * 修改 ServerboundMovePlayerPacket.PosRot 中的 pitch
     */
    @ModifyArg(
            method = "m_108640_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$PosRot;<init>(DDDFFZ)V"
            ),
            index = 4,
            remap = false
    )
    private float modifyPitchInPosRot(float pitch) {
        if (FakeRotationHandler.isEnabled()) {
            return FakeRotationHandler.getFakePitch();
        }
        return pitch;
    }
}