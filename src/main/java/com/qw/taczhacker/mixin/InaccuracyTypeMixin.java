package com.qw.taczhacker.mixin;

import com.qw.taczhacker.network.FakeAimTracker;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 假开镜的散布部分
 *
 * Tacz 原版逻辑（InaccuracyType#getInaccuracyType）：
 *   getSynAimingProgress() == 1.0f 就跑 AIM 档，默认散布 AIM 0.15 / STAND 5.0。
 *
 * 假开镜不碰 synced data（碰了灵敏度就会变），所以这里在判定入口直接开个后门：
 * 服务端记着这个玩家在假开镜，就按 AIM 档取。
 *
 * 结果：服务端算出来的子弹散布就是开镜精度，而服务端自己的
 * isAiming / aimingProgress 仍然是 false / 0 —— 移速、冲刺、动画全不受影响。
 */
@Mixin(value = InaccuracyType.class, remap = false)
public class InaccuracyTypeMixin {

    @Inject(method = "getInaccuracyType", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczhacker$fakeAim(LivingEntity shooter, CallbackInfoReturnable<InaccuracyType> cir) {
        if (!(shooter instanceof Player player)) return;
        if (FakeAimTracker.isFakeAiming(player)) {
            cir.setReturnValue(InaccuracyType.AIM);
        }
    }
}
