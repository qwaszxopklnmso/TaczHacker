package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import com.qw.taczhacker.config.HackConfig;

/**
 * 服务端检测器（纯标记类，无客户端依赖）
 *
 * 存储服务端是否安装了 TaczHacker mod，以及服务端实际启用了哪些子弹端功能。
 * 此类不引用任何客户端专属类，可在服务端安全加载。
 *
 * 检测逻辑由 ServerDetectorClientHandler（客户端专属）负责：
 * - 单人游戏/局域网 → 直接按本地配置填充能力位
 * - 远程 Forge 服务器 → 通过握手包获取
 *
 * 自动切换逻辑（供 LocalPlayerShootMixin 使用）：
 * - isServerAimAvailable() == true  → 服务端会改子弹方向，客户端不再发假旋转包
 * - isServerAimAvailable() == false → 使用转视角静默自瞄
 */
public class ServerDetector {

    /** 服务端是否安装了 TaczHacker */
    private static volatile boolean serverHasTaczHacker = false;

    /** 握手是否已完成 */
    private static volatile boolean handshakeCompleted = false;

    /** 是否为单人游戏/局域网（集成服务器） */
    private static volatile boolean isSinglePlayer = false;

    /** 服务端是否启用了子弹方向自瞄 */
    private static volatile boolean serverAimEnabled = false;

    /** 服务端是否启用了追踪弹 */
    private static volatile boolean serverHomingEnabled = false;

    /** 服务端是否启用了穿墙子弹 */
    private static volatile boolean serverPenetrationEnabled = false;

    /**
     * 当前是否跑在集成服务器上（单人游戏 / 局域网）
     *
     * 这种情况下客户端和"服务端"共处同一个进程，静态字段可以直接互相传递，
     * 服务端那边的兜底逻辑就没必要再凭空挑目标改弹道了。
     */
    public static boolean isIntegratedServer() {
        return isSinglePlayer;
    }

    /**
     * 服务端是否安装了 TaczHacker mod
     *
     * - 单人游戏/局域网（集成服务器）：永远返回 true
     * - 远程服务器：返回握手检测结果
     */
    public static boolean isServerHasTaczHacker() {
        if (isSinglePlayer) {
            return true;
        }
        if (!handshakeCompleted) {
            // 握手尚未完成，保守起见返回 false（使用转视角自瞄）
            return false;
        }
        return serverHasTaczHacker;
    }

    /**
     * 服务端是否真的会用子弹方向修改来实现自瞄
     *
     * 只有这个为 true 时，客户端才可以跳过发送假旋转包。
     * 服务端装了 mod 但把 aim.enabled 关掉时返回 false，客户端仍然走转视角方案。
     *
     * 单机/局域网：服务端就是本进程，直接读实时配置（改了配置立刻生效，不用重进世界）。
     */
    public static boolean isServerAimAvailable() {
        if (isSinglePlayer) {
            return HackConfig.aimEnabled;
        }
        return isServerHasTaczHacker() && serverAimEnabled;
    }

    public static boolean isServerHomingEnabled() {
        if (isSinglePlayer) {
            return HackConfig.aimSinglePlayerHomingBullet;
        }
        return isServerHasTaczHacker() && serverHomingEnabled;
    }

    public static boolean isServerPenetrationEnabled() {
        if (isSinglePlayer) {
            return HackConfig.aimSinglePlayerBulletPenetration;
        }
        return isServerHasTaczHacker() && serverPenetrationEnabled;
    }

    /**
     * 设置是否为单人游戏/局域网
     */
    public static void setSinglePlayer(boolean value) {
        isSinglePlayer = value;
    }

    /**
     * 写入服务端能力位（握手应答 / 单机本地配置）
     */
    public static void setServerCapabilities(boolean hasMod, boolean aim, boolean homing, boolean penetration) {
        serverHasTaczHacker = hasMod;
        serverAimEnabled = aim;
        serverHomingEnabled = homing;
        serverPenetrationEnabled = penetration;
        handshakeCompleted = true;
        Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 服务端能力：mod={}, aim={}, homing={}, penetration={}",
                hasMod, aim, homing, penetration);
    }

    /**
     * 兼容旧调用：仅设置"服务端有本 mod"
     */
    public static void setServerHasTaczHacker(boolean value) {
        setServerCapabilities(value, false, false, false);
    }

    /**
     * 重置所有状态（客户端断开连接时调用）
     */
    public static void reset() {
        serverHasTaczHacker = false;
        handshakeCompleted = false;
        isSinglePlayer = false;
        serverAimEnabled = false;
        serverHomingEnabled = false;
        serverPenetrationEnabled = false;
    }
}
