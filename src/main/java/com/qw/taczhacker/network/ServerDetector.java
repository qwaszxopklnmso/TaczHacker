package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;

/**
 * 服务端检测器（纯标记类，无客户端依赖）
 *
 * 存储服务端是否安装了 TaczHacker mod 的检测结果。
 * 此类不引用任何客户端专属类，可在服务端安全加载。
 *
 * 检测逻辑由 ServerDetectorClientHandler（客户端专属）负责：
 * - 单人游戏/局域网 → setSinglePlayer(true)
 * - 远程 Forge 服务器 → 通过握手包检测
 *
 * 自动切换逻辑（供 LocalPlayerShootMixin 使用）：
 * - isServerHasTaczHacker() == true  → 使用真·自瞄（子弹方向修改），关闭转视角
 * - isServerHasTaczHacker() == false → 使用功能1（转视角自瞄）
 */
public class ServerDetector {

    /** 服务端是否安装了 TaczHacker */
    private static volatile boolean serverHasTaczHacker = false;

    /** 握手是否已完成 */
    private static volatile boolean handshakeCompleted = false;

    /** 是否为单人游戏/局域网（集成服务器） */
    private static volatile boolean isSinglePlayer = false;

    /**
     * 服务端是否安装了 TaczHacker mod
     *
     * 自动切换逻辑：
     * - 单人游戏/局域网（集成服务器）：永远返回 true
     * - 远程服务器：返回握手检测结果
     */
    public static boolean isServerHasTaczHacker() {
        // 单人游戏/局域网：集成服务器，客户端和服务端在同一进程
        if (isSinglePlayer) {
            return true;
        }
        // 远程服务器：等待握手完成
        if (!handshakeCompleted) {
            // 握手尚未完成，保守起见返回 false（使用功能1转视角）
            return false;
        }
        return serverHasTaczHacker;
    }

    /**
     * 设置是否为单人游戏/局域网
     */
    public static void setSinglePlayer(boolean value) {
        isSinglePlayer = value;
    }

    /**
     * 设置服务端有本mod（由握手包 S2CHandshakeAckPacket 回调调用）
     */
    public static void setServerHasTaczHacker(boolean value) {
        serverHasTaczHacker = value;
        handshakeCompleted = true;
        Taczhacker.LOGGER.info("[TaczHacker][服务端检测] 结果：{}",
                value ? "已安装 TaczHacker，将使用真·自瞄" : "未安装 TaczHacker，将使用转视角自瞄");
    }

    /**
     * 重置所有状态（客户端断开连接时调用）
     */
    public static void reset() {
        serverHasTaczHacker = false;
        handshakeCompleted = false;
        isSinglePlayer = false;
    }
}