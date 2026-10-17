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
 * 自动切换逻辑（**2026-10-11 起已取消**）：
 * 曾经：isServerAimAvailable() == true → 客户端跳过发假旋转包，交给服务端改弹道；
 *        == false → 客户端自己发瞄准旋转包。
 * 现在：客户端**无条件**自己发瞄准旋转包（见 LocalPlayerShootMixin 里的说明），
 *       因为服务端的兜底在专用服务器上锁不住目标，反而让功能1 在联机时完全失效。
 *       能力位仅用于显示/调试，以及单机专用的追踪弹/穿墙子弹开关判断。
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
     * 服务端是否报了「会用子弹方向修改来自瞄」的能力位
     *
     * ⚠️ **不要再用它来决定是否跳过客户端自瞄**（2026-10-11 的教训）：
     * 专用服务器上服务端只有「自己挑半径内最近的生物 + 子弹方向与目标夹角在锥角内才修正」
     * 这条兜底，转圈开着时子弹先朝地下（夹角≈90°）根本不修正 → 打地板；转圈关掉也锁不准。
     * 曾经客户端一旦看到 aim=true 就整个跳过自己的瞄准（连旋转包都不发），
     * 联机于是「功能1 完全没用」，而单机因为 isSinglePlayer 分支恒为 false 所以正常。
     * 现在客户端无条件自己瞄（见 LocalPlayerShootMixin），这个能力位只用于显示/调试。
     */
    public static boolean isServerAimAvailable() {
        if (isSinglePlayer) {
            return false;
        }
        return isServerHasTaczHacker() && serverAimEnabled;
    }

    public static boolean isServerHomingEnabled() {
        if (isSinglePlayer) {
            return HackConfig.aimSinglePlayerHomingBullet;
        }
        return isServerHasTaczHacker() && serverHomingEnabled;
    }

    // ============================================================
    // 双端与门（作者要求：服务端当前功能=true && 客户端当前功能=true 才生效）
    // ============================================================

    /**
     * 「真·自瞄」是否真正生效：客户端功能1 开着 **且** 服务端也开着子弹自瞄
     *
     * 单机/局域网（集成服务器）就是同一份配置，直接看客户端这份即可。
     */
    public static boolean isAimEffective() {
        if (!HackConfig.globalEnabled || !HackConfig.aimEnabled || !HackConfig.runtimeAimActive) {
            return false;
        }
        if (isSinglePlayer) {
            return true;
        }
        return isServerAimAvailable();
    }

    /** 「追踪弹」是否真正生效：客户端开关 **且** 服务端也开着（都要功能1 总开关开着） */
    public static boolean isHomingEffective() {
        return HackConfig.globalEnabled
                && HackConfig.aimEnabled
                && HackConfig.aimSinglePlayerHomingBullet
                && isServerHomingEnabled();
    }

    /** 「穿墙子弹」是否真正生效：客户端开关 **且** 服务端也开着（都要功能1 总开关开着） */
    public static boolean isPenetrationEffective() {
        return HackConfig.globalEnabled
                && HackConfig.aimEnabled
                && HackConfig.aimSinglePlayerBulletPenetration
                && isServerPenetrationEnabled();
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
