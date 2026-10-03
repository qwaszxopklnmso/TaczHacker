package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端 握手确认包
 *
 * 服务端收到 C2SHandshakePacket 后回复此包，客户端收到后标记服务端已安装本 mod，
 * 同时获知服务端**实际开启**了哪些子弹端功能。
 *
 * 为什么需要携带服务端配置：
 * 本 mod 的配置是 COMMON 类型，服务端有自己的一份 taczhacker-common.toml。
 * 客户端如果只看「服务端装了 mod」就跳过转视角自瞄，而服务端恰好把 aim.enabled
 * 关着（默认就是 false），那么功能1 会完全失效且没有任何提示。
 * 因此客户端必须按服务端回报的能力位来决定走哪条路径。
 */
public class S2CHandshakeAckPacket {

    /** 服务端是否启用了子弹方向自瞄（aim.enabled） */
    private final boolean aimEnabled;
    /** 服务端是否启用了追踪弹（aim.singlePlayerHomingBullet） */
    private final boolean homingEnabled;
    /** 服务端是否启用了穿墙子弹（aim.singlePlayerBulletPenetration） */
    private final boolean penetrationEnabled;

    public S2CHandshakeAckPacket(boolean aimEnabled, boolean homingEnabled, boolean penetrationEnabled) {
        this.aimEnabled = aimEnabled;
        this.homingEnabled = homingEnabled;
        this.penetrationEnabled = penetrationEnabled;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(aimEnabled);
        buf.writeBoolean(homingEnabled);
        buf.writeBoolean(penetrationEnabled);
    }

    public static S2CHandshakeAckPacket decode(FriendlyByteBuf buf) {
        return new S2CHandshakeAckPacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }

    /**
     * 处理：客户端收到确认包，记录服务端 mod 存在与服务端能力位
     */
    public static void handle(S2CHandshakeAckPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerDetector.setServerCapabilities(true, msg.aimEnabled, msg.homingEnabled, msg.penetrationEnabled);
            Taczhacker.LOGGER.debug("[TaczHacker][握手] 服务端确认：aim={}, homing={}, penetration={}",
                    msg.aimEnabled, msg.homingEnabled, msg.penetrationEnabled);
        });
        ctx.setPacketHandled(true);
    }
}
