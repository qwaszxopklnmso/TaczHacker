package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端检测器客户端事件处理器（仅客户端）
 *
 * 处理客户端网络事件，更新 ServerDetector 的检测状态。
 * 此类的 @Mod.EventBusSubscriber(value = Dist.CLIENT) 确保仅在客户端加载。
 *
 * 事件处理：
 * - LoggedIn：客户端登录服务器后，发送握手包并检测是否为单人游戏
 * - LoggingOut：客户端断开连接时，重置检测状态
 */
@Mod.EventBusSubscriber(modid = Taczhacker.MODID, value = Dist.CLIENT)
public class ServerDetectorClientHandler {

    /**
     * 客户端登录服务器时触发
     * 发送握手包检测服务端是否安装了本mod
     */
    @SubscribeEvent
    public static void onClientLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // 重置状态
        ServerDetector.reset();

        // 检测是否为单人游戏/局域网（集成服务器）
        Minecraft mc = Minecraft.getInstance();
        boolean isSP = mc.isLocalServer();
        ServerDetector.setSinglePlayer(isSP);

        if (isSP) {
            // 单人游戏/局域网：服务端在同一进程，直接标记为有本mod
            ServerDetector.setServerHasTaczHacker(true);
            Taczhacker.LOGGER.info("[TaczHacker][服务端检测] 单人游戏/局域网模式，直接启用真·自瞄");
            return;
        }

        // 远程服务器：发送握手包检测
        Taczhacker.CHANNEL.sendToServer(new C2SHandshakePacket());
        Taczhacker.LOGGER.info("[TaczHacker][服务端检测] 已发送握手包到远程服务器，等待回复...");
    }

    /**
     * 客户端断开连接时重置状态
     */
    @SubscribeEvent
    public static void onClientDisconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        // 注意：Forge 1.20.1 中 LoggingOut 事件在客户端断开连接时触发
        ServerDetector.reset();
        Taczhacker.LOGGER.info("[TaczHacker][服务端检测] 断开连接，重置检测状态");
    }
}