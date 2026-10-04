package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import com.qw.taczhacker.config.HackConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
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
            // 单人游戏/局域网：集成服务器与客户端共享同一份 COMMON 配置，
            // 因此直接按本地配置填充服务端能力位
            ServerDetector.setServerCapabilities(
                    true,
                    HackConfig.aimEnabled,
                    HackConfig.aimSinglePlayerHomingBullet,
                    HackConfig.aimSinglePlayerBulletPenetration);
            Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 单人游戏/局域网模式");
            return;
        }

        // 远程服务器：发送握手包检测
        // 服务端没装本 mod 时这个通道在对面不存在（协商结果是 ABSENT），不能发，
        // 直接按「无服务端能力」处理，功能1 继续走转视角方案。
        // 整段包 try-catch：握手只是个可选增强，任何异常都不该影响进游戏。
        try {
            Connection connection = mc.getConnection() == null ? null : mc.getConnection().getConnection();
            if (connection != null && Taczhacker.CHANNEL.isRemotePresent(connection)) {
                Taczhacker.CHANNEL.sendToServer(new C2SHandshakePacket());
                Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 已发送握手包到远程服务器，等待回复...");
            } else {
                ServerDetector.setServerCapabilities(false, false, false, false);
                Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 服务端未安装本 mod（通道不存在），使用纯客户端方案");
            }
        } catch (Throwable t) {
            ServerDetector.setServerCapabilities(false, false, false, false);
            Taczhacker.LOGGER.warn("[TaczHacker][服务端检测] 握手异常，已按无服务端能力处理（不影响游戏）", t);
        }
    }

    /**
     * 客户端断开连接时重置状态
     */
    @SubscribeEvent
    public static void onClientDisconnected(ClientPlayerNetworkEvent.LoggingOut event) {
        ServerDetector.reset();
        Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 断开连接，重置检测状态");
    }
}