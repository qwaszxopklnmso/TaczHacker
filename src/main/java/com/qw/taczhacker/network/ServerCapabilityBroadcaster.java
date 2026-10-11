package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import com.qw.taczhacker.config.HackConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;

/**
 * 服务端能力位推送（服务端配置一变，立刻告诉所有在线客户端）
 *
 * 为什么需要它 —— 2026-10-11 作者报「追踪弹 / 穿墙子弹的 ON/OFF 有时同步不及时」，
 * 日志实锤了这条时间线（同一份 debug.log）：
 *
 *   21:25:39.549  客户端上报 aim=true, homing=true, penetration=true
 *   21:25:39.560  服务端回复 aim=true, homing=false, penetration=false   ← 服务端那份 toml 还关着
 *   21:25:50.251  服务端的 taczhacker-common.toml 被改 → 「配置已刷新」
 *                 ↑ 从这一瞬间起服务端真的开始追踪/穿墙了（与门左边成立，客户端那一半早就报过 true）
 *   21:26:57.008  客户端 HUD 才把这两行变成 ON —— 因为客户端这一刻恰好保存了自己的配置，
 *                 顺手发了一次握手，服务端回包才把新的能力位带给它。**中间差了 67 秒**。
 *
 * 根因：客户端手里的「服务端能力位」只是**握手那一刻的快照**，而握手只在
 * 「客户端登录 / 客户端自己改配置」时发生 —— 服务端改配置没有任何人通知客户端，
 * 于是 HUD 显示的和子弹实际行为可以长时间打架（行为是服务端实时读配置的）。
 *
 * 修法：服务端配置发生变化时（{@link ModConfigEvent}，Forge 的配置监听器发现 toml 被改
 * 就会触发 Loading/Reloading），把新的能力位主动推给所有在线玩家，客户端收到就更新
 * {@link ServerDetector}。稳态零发包 —— 只在改配置那一刻发，配置改动是管理员/玩家手动行为。
 *
 * 本类不引用任何客户端专属类，服务端可安全加载；专用服务器上通过
 * {@link ServerLifecycleHooks#getCurrentServer()} 拿到当前服务器，拿不到（纯客户端连别人服）
 * 直接什么都不做。
 */
@Mod.EventBusSubscriber(modid = Taczhacker.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ServerCapabilityBroadcaster {

    @SubscribeEvent
    public static void onConfigChanged(ModConfigEvent event) {
        // 只关心本 mod 的配置
        if (!event.getConfig().getModId().equals(Taczhacker.MODID)) {
            return;
        }
        // 纯客户端连别人的服时这里是 null（本 mod 只有在开集成服务器/专用服务器时才有服务器）
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        // Forge 的配置监听器是在文件监听线程（日志里的 Thread-17）触发这个事件的，
        // 发包要在服务器主线程做，统一丢进 server.execute
        server.execute(() -> pushToAll(server));
    }

    /**
     * 把当前服务端能力位推给所有在线玩家
     */
    private static void pushToAll(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            return;
        }
        for (ServerPlayer player : players) {
            Taczhacker.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CHandshakeAckPacket(
                            HackConfig.aimEnabled,
                            HackConfig.aimSinglePlayerHomingBullet,
                            HackConfig.aimSinglePlayerBulletPenetration));
        }
        Taczhacker.LOGGER.debug("[TaczHacker][服务端检测] 服务端配置变化，已向 {} 名玩家推送能力位：aim={}, homing={}, penetration={}",
                players.size(),
                HackConfig.aimEnabled,
                HackConfig.aimSinglePlayerHomingBullet,
                HackConfig.aimSinglePlayerBulletPenetration);
    }
}
