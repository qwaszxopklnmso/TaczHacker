package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 客户端配置变化时补报能力位（仅客户端）
 *
 * 这是 {@link ServerCapabilityBroadcaster} 的反方向：那边管「服务端配置变了 → 告诉客户端」，
 * 这边管「客户端配置变了 → 告诉服务端」，两侧都别拿着旧快照。
 *
 * 客户端的三项开关本来在「配置界面保存」和「功能1 的 N 键」这两条路上已经会补发
 * （{@link ServerDetectorClientHandler#sendFeatureFlags}），但**手改 toml** 不经过这两个入口：
 * Forge 的配置监听器发现文件被改就刷新内存里的值，功能立刻按新值走，
 * 而服务端手里还是旧的上报 —— 于是 HUD 和服务端行为又对不上了。
 * 所以这里挂一个 ModConfigEvent 监听，任何来源的配置变化都补报一次。
 *
 * ⚠️ 必须是**独立的一个类**：{@code ModConfigEvent} 走的是 MOD 事件总线，
 * 而 {@link ServerDetectorClientHandler} 挂在 FORGE 总线上（要收 ClientPlayerNetworkEvent），
 * 一个类的 {@code @Mod.EventBusSubscriber#bus} 只能选一个，混不进同一个类。
 *
 * 稳态零发包：只在配置真的变化那一刻发一包。事件本身在 Forge 的文件监听线程触发，
 * 但 {@code sendFeatureFlags} 只是往 Netty 通道写包，不碰游戏状态，线程安全。
 */
@Mod.EventBusSubscriber(modid = Taczhacker.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientCapabilityResyncHandler {

    @SubscribeEvent
    public static void onConfigChanged(ModConfigEvent event) {
        if (!event.getConfig().getModId().equals(Taczhacker.MODID)) {
            return;
        }
        ServerDetectorClientHandler.sendFeatureFlags();
    }
}
