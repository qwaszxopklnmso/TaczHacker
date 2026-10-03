package com.qw.taczhacker.feature.aim;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.config.HackConfig.TargetMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * 目标过滤（功能1 静默自瞄 / 功能3 视角锁定 共用）
 *
 * 纯服务端安全类：只依赖原版通用类与 COMMON 配置，不引用任何客户端类。
 *
 * 过滤规则（全部可在 Cloth Config 中配置）：
 * - 创造/旁观模式玩家：永远排除
 * - 目标类型：ALL / PLAYERS_ONLY / HOSTILE_ONLY
 * - 已驯服生物：可选排除
 * - 同队伍玩家：可选排除
 * - 盔甲架：可选排除
 */
public final class TargetFilter {

    private TargetFilter() {
    }

    /**
     * 目标是否可以被选为攻击对象
     *
     * @param self   攻击者（可以是玩家或任意生物）
     * @param target 候选目标
     */
    public static boolean isValid(Entity self, LivingEntity target) {
        if (target == null || target == self) return false;
        if (!target.isAlive()) return false;

        // 玩家：排除创造/旁观，可选排除队友
        if (target instanceof Player targetPlayer) {
            if (targetPlayer.isCreative() || targetPlayer.isSpectator()) return false;
            if (HackConfig.targetIgnoreTeammates
                    && self instanceof Player selfPlayer
                    && selfPlayer.isAlliedTo(targetPlayer)) {
                return false;
            }
        }

        // 已驯服的宠物
        if (HackConfig.targetIgnoreTamed
                && target instanceof TamableAnimal tameable
                && tameable.isTame()) {
            return false;
        }

        // 盔甲架
        if (HackConfig.targetIgnoreArmorStands && target instanceof ArmorStand) {
            return false;
        }

        TargetMode mode = HackConfig.targetMode == null ? TargetMode.ALL : HackConfig.targetMode;
        return switch (mode) {
            case PLAYERS_ONLY -> target instanceof Player;
            case HOSTILE_ONLY -> target instanceof Enemy;
            case ALL -> true;
        };
    }
}
