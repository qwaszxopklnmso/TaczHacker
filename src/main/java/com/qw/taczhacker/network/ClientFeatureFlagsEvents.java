package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * {@link ClientFeatureFlags} 的清理钩子（双端都能加载，纯服务端逻辑）
 *
 * - 玩家退出：删掉它的能力位记录
 * - 服务器关停：清空整表（集成服务器反复开关世界时不会残留）
 */
@Mod.EventBusSubscriber(modid = Taczhacker.MODID)
public class ClientFeatureFlagsEvents {

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ClientFeatureFlags.remove(serverPlayer.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ClientFeatureFlags.clear();
    }
}
