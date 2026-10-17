package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.aim.AimHandler;
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
                    HackConfig.aimTrueAim,
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
                Taczhacker.CHANNEL.sendToServer(new C2SHandshakePacket(
                        AimHandler.isActive(),
                        AimHandler.isActive() && HackConfig.aimTrueAim,
                        HackConfig.globalEnabled && HackConfig.aimSinglePlayerHomingBullet,
                        HackConfig.globalEnabled && HackConfig.aimSinglePlayerBulletPenetration));
                Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 已发送握手包到远程服务器，等待回复...");
            } else {
                ServerDetector.setServerCapabilities(false, false, false, false, false);
                Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 服务端未安装本 mod（通道不存在），使用纯客户端方案");
            }
        } catch (Throwable t) {
            ServerDetector.setServerCapabilities(false, false, false, false, false);
            Taczhacker.LOGGER.warn("[TaczHacker][服务端检测] 握手异常，已按无服务端能力处理（不影响游戏）", t);
        }
    }

    /**
     * 把「客户端当前开着哪些子弹端功能」重新同步给服务端
     *
     * 服务端拿它和自己的配置做与门（详见 {@link ClientFeatureFlags}）。
     * 调用时机：功能1 的 N 键切换、配置界面保存，以及任何来源的配置变化
     * （{@link ClientCapabilityResyncHandler} 挂的 ModConfigEvent，覆盖手改 toml 的情况）。
     * 集成服务器不用发：客户端与服务端同进程、共用同一份配置，服务端直接读配置。
     */
    public static void sendFeatureFlags() {
        Minecraft mc = Minecraft.getInstance();
        // 配置加载事件（ModConfigEvent.Loading）在启动阶段就会触发，
        // 那时客户端实例可能还没建好 —— 这种时候没有连接，直接跳过
        if (mc == null) return;
        if (mc.getConnection() == null || mc.isLocalServer()) return;
        try {
            Connection connection = mc.getConnection().getConnection();
            if (connection == null || !Taczhacker.CHANNEL.isRemotePresent(connection)) {
                // 服务端没装本 mod：没有与门可言，客户端自己那份配置就是全部（不发包是对的）
                Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 服务端通道不存在，跳过能力位同步");
                return;
            }
            Taczhacker.CHANNEL.sendToServer(new C2SHandshakePacket(
                    AimHandler.isActive(),
                    AimHandler.isActive() && HackConfig.aimTrueAim,
                    HackConfig.globalEnabled && HackConfig.aimSinglePlayerHomingBullet,
                    HackConfig.globalEnabled && HackConfig.aimSinglePlayerBulletPenetration));
            Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 已同步客户端能力位："
                            + "aim={}, trueAim={}, homing={}, penetration={}",
                    AimHandler.isActive(),
                    AimHandler.isActive() && HackConfig.aimTrueAim,
                    HackConfig.globalEnabled && HackConfig.aimSinglePlayerHomingBullet,
                    HackConfig.globalEnabled && HackConfig.aimSinglePlayerBulletPenetration);
        } catch (Throwable t) {
            Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 同步客户端能力位失败（忽略）", t);
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