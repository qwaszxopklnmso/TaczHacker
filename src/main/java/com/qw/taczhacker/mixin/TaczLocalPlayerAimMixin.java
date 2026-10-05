package com.qw.taczhacker.mixin;

import com.qw.taczhacker.feature.aim.FakeAimHandler;
import com.tacz.guns.client.gameplay.LocalPlayerAim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 假开镜的客户端部分：把本地开镜整个摘掉
 *
 * Tacz 的 LocalPlayerAim#aim(boolean) 干两件事：
 *   1. data.clientIsAiming = isAim   ← 本地状态：驱动开镜动画、相机、模型 FOV、右键灵敏度
 *   2. 发 ClientMessagePlayerAim      ← 告诉服务端：服务端据此同步 aimingProgress / isAiming
 *
 * 开镜键（AimKey）按下、按住、松手，最后都走到这个方法，
 * 所以在这里拦一刀就能同时挡住「本地状态」和「原版开镜包」两件事。
 *
 * 假开镜模式下我们只发自己的 C2SFakeAimPacket（服务端拿它算散布档位），
 * 本地 clientIsAiming 保持 false —— 于是：
 *   - 相机 FOV 不缩（CameraSetupEvent 本地分支读 clientAimingProgress）
 *   - 鼠标灵敏度不变（MouseHandlerMixin 读的是同步值，服务端从没被通知过）
 *   - 移速惩罚不出现（LivingEntitySpeedModifier 读服务端 dataHolder.isAiming）
 *   - 不出瞄准镜画面
 *
 * 返回 true 表示「这次 aim() 由本功能接管，原方法不要执行」。
 */
@Mixin(value = LocalPlayerAim.class, remap = false)
public class TaczLocalPlayerAimMixin {

    @Inject(method = "aim", at = @At("HEAD"), cancellable = true, remap = false)
    private void taczhacker$fakeAim(boolean isAim, CallbackInfo ci) {
        if (FakeAimHandler.interceptAim(isAim)) {
            ci.cancel();
        }
    }
}
