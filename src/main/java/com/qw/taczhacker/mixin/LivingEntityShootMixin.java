package com.qw.taczhacker.mixin;

import org.spongepowered.asm.mixin.Mixin;

/**
 * LivingEntityShoot Mixin
 *
 * 此 Mixin 已废弃，不再需要修改 LivingEntityShoot 的行为。
 *
 * 功能1 的子弹方向覆盖完全由 TimelessBulletEntityMixin 在
 * EntityKineticBullet.shoot() 中完成，无需在此处修改 shooter 旋转。
 *
 * 保留空 Mixin 类仅为兼容旧配置，不注入任何方法、不产生任何日志。
 */
@Mixin(value = com.tacz.guns.entity.shooter.LivingEntityShoot.class, remap = false)
public class LivingEntityShootMixin {
}
