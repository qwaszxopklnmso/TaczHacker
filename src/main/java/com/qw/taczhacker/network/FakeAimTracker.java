package com.qw.taczhacker.network;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 假开镜玩家表（服务端 / 集成服务端）
 *
 * 记录哪些玩家当前处于「假开镜」状态。服务端不修改任何玩家状态，
 * 只在散布档位判定时查这张表（见 {@code InaccuracyTypeMixin}）。
 *
 * 注意：这里只依赖原版 Player，严禁引用任何客户端类，
 * 否则专用服务器加载本类会炸。
 */
@Mod.EventBusSubscriber(modid = "taczhacker")
public final class FakeAimTracker {

    private static final Set<UUID> FAKE_AIMING = ConcurrentHashMap.newKeySet();

    private FakeAimTracker() {
    }

    public static void setFakeAiming(Player player, boolean fakeAiming) {
        if (player == null) return;
        if (fakeAiming) {
            FAKE_AIMING.add(player.getUUID());
        } else {
            FAKE_AIMING.remove(player.getUUID());
        }
    }

    public static boolean isFakeAiming(Player player) {
        return player != null && FAKE_AIMING.contains(player.getUUID());
    }

    /** 下线时清掉，免得 UUID 泄漏或者重连后残留旧状态 */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        FAKE_AIMING.remove(event.getEntity().getUUID());
    }
}
