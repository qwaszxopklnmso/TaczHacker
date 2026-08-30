package com.qw.taczhacker.mixin;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;

/**
 * LivingEntityShoot Mixin
 *
 * 此 Mixin 已废弃，不再需要修改 LivingEntityShoot 的行为。
 *
 * 功能1 的子弹方向覆盖完全由 TimelessBulletEntityMixin 在
 * EntityKineticBullet.shoot() 中完成，无需在此处修改 shooter 旋转。
 *
 * 保留空 Mixin 类以兼容之前的数据包注册逻辑，但不再注入任何方法。
 */
@Mixin(value = com.tacz.guns.entity.shooter.LivingEntityShoot.class, remap = false)
public class LivingEntityShootMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        LOGGER.info("[TaczHacker][Mixin] LivingEntityShootMixin 类已加载（已废弃，无注入）");
    }
}