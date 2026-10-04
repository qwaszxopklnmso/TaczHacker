package com.qw.taczhacker.mixin;

import com.qw.taczhacker.config.HackConfig;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 附加功能：无后坐力（Tacz）
 *
 * Tacz 的后坐力是**纯渲染**的：{@code CameraSetupEvent.applyCameraRecoil} 挂在
 * {@code ViewportEvent.ComputeCameraAngles} 上，每帧把摄像机的 pitch/yaw 顶一下。
 * 它改的是摄像机角度（画面），不改玩家的实际 xRot/yRot。
 *
 * 所以直接把整个方法取消掉，画面就不跳了 —— 纯客户端，服务端毫无察觉。
 *
 * ⚠️ 分清两件事：
 *   - **无后坐力**（这个）＝ 画面不抖。可以在客户端单方面做掉
 *   - **无扩散** ＝ 子弹散布。Tacz 的散布在**服务端生成子弹时**算
 *     （{@code ModernKineticGunItem.doBulletSpread}，还会跑枪自带的 Lua calcSpread），
 *     客户端发出去的 {@code ClientMessagePlayerShoot} 里只有 timestamp 和 chargeProgress，
 *     连方向都不带，所以客户端**改不了**，必须服务端也装本 mod
 *
 * 目标类是 Tacz 的类，所以整个 mixin 放在 required=false 的配置里：
 * 玩家没装 Tacz 时这份配置整体跳过，不会崩。
 */
@Mixin(value = CameraSetupEvent.class, remap = false)
public class TaczCameraRecoilMixin {

    @Inject(method = "applyCameraRecoil", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczhacker$noRecoil(ViewportEvent.ComputeCameraAngles event, CallbackInfo ci) {
        if (HackConfig.taczNoRecoil) {
            ci.cancel();
        }
    }
}
