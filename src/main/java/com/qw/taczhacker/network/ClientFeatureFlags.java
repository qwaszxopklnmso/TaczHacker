package com.qw.taczhacker.network;

import com.qw.taczhacker.config.HackConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端侧：记录「每个玩家的客户端当前开着哪些子弹端功能」
 *
 * 数据来源是客户端登录时发的 {@link C2SHandshakePacket}（那三项就是客户端当时的
 * 真实状态，功能1 用的还是运行时开关 {@code AimHandler#isActive()}，不是光看配置），
 * 之后客户端每次改动这三项（功能1 的 N 键、配置界面保存）会再补一包。
 *
 * 为什么需要它 —— 这三项功能都作用在**服务端**那颗子弹实体上，但「要不要生效」应该是
 * 双端与门：服务端配置开着 **且** 该玩家客户端也开着。只按服务端配置来的话，
 * 连原版客户端的人也会被你服务器的配置一起改弹道；只按客户端来又管不到服务端。
 *
 * 集成服务器（单机/局域网）不走握手：客户端和服务端同进程、共用同一份 COMMON 配置，
 * 所以那边直接读配置即可（见 {@link #wantsAim} 等方法的回退分支）。
 *
 * 本类不引用任何客户端专属类，服务端可以安全加载。
 */
public final class ClientFeatureFlags {

    /** 客户端功能1（开火静默自瞄）当前是否生效 */
    private static final Map<UUID, Boolean> AIM = new ConcurrentHashMap<>();
    /** 客户端追踪弹开关 */
    private static final Map<UUID, Boolean> HOMING = new ConcurrentHashMap<>();
    /** 客户端穿墙子弹开关 */
    private static final Map<UUID, Boolean> PENETRATION = new ConcurrentHashMap<>();

    private ClientFeatureFlags() {
    }

    /** 收到客户端上报（同时覆盖上一次的值） */
    public static void update(UUID playerId, boolean aim, boolean homing, boolean penetration) {
        AIM.put(playerId, aim);
        HOMING.put(playerId, homing);
        PENETRATION.put(playerId, penetration);
    }

    /** 玩家退出：清掉它的记录，避免 UUID 状态残留 */
    public static void remove(UUID playerId) {
        AIM.remove(playerId);
        HOMING.remove(playerId);
        PENETRATION.remove(playerId);
    }

    /** 服务器关停：整表清空 */
    public static void clear() {
        AIM.clear();
        HOMING.clear();
        PENETRATION.clear();
    }

    // ============================================================
    // 与门查询（供 TimelessBulletEntityMixin 使用）
    // ============================================================

    /** 该射手（子弹主人）的客户端是否在用功能1（决定服务端要不要做「零散布」方向覆盖） */
    public static boolean wantsAim(LivingEntity shooter) {
        Boolean reported = reported(shooter, AIM);
        if (reported != null) return reported;
        // 集成服务器：同进程，直接读这份配置（含 N 键的运行时开关）
        return integrated() && HackConfig.globalEnabled && HackConfig.aimEnabled && HackConfig.runtimeAimActive;
    }

    /** 该射手的客户端是否开着追踪弹 */
    public static boolean wantsHoming(LivingEntity shooter) {
        Boolean reported = reported(shooter, HOMING);
        if (reported != null) return reported;
        return integrated() && HackConfig.globalEnabled && HackConfig.aimSinglePlayerHomingBullet;
    }

    /** 该射手的客户端是否开着穿墙子弹 */
    public static boolean wantsPenetration(LivingEntity shooter) {
        Boolean reported = reported(shooter, PENETRATION);
        if (reported != null) return reported;
        return integrated() && HackConfig.globalEnabled && HackConfig.aimSinglePlayerBulletPenetration;
    }

    /** 专用服务器上射手是 ServerPlayer 时取它客户端报上来的值；取不到返回 null */
    private static Boolean reported(LivingEntity shooter, Map<UUID, Boolean> map) {
        if (shooter instanceof ServerPlayer serverPlayer) {
            return map.get(serverPlayer.getUUID());
        }
        return null;
    }

    /** 是否为集成服务器（单机/局域网）——那边客户端与服务端同进程、共用同一份配置 */
    private static boolean integrated() {
        return ServerDetector.isIntegratedServer();
    }
}
