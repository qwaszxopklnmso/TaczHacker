package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端 握手包
 *
 * 客户端连接服务器后发送此包，服务端收到后回复 S2CHandshakeAckPacket。
 * 用于检测服务端是否安装了 TaczHacker mod。
 *
 * 如果服务端安装了本mod，服务端的 SimpleChannel 能处理此包 → 回复确认包
 * 如果服务端没有安装本mod，此包不会被处理 → 客户端收不到回复 → 判定服务端无mod
 */
public class C2SHandshakePacket {

    /**
     * 编码：此包不含数据，无需写入
     */
    public void encode(FriendlyByteBuf buf) {
        // 空包，无需写入任何数据
    }

    /**
     * 解码：此包不含数据，直接返回新实例
     */
    public static C2SHandshakePacket decode(FriendlyByteBuf buf) {
        return new C2SHandshakePacket();
    }

    /**
     * 处理：服务端收到握手包，回复确认包
     */
    public static void handle(C2SHandshakePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // 服务端收到握手包，说明客户端也安装了本mod
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                // 回复确认包到客户端
                Taczhacker.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new S2CHandshakeAckPacket()
                );
                Taczhacker.LOGGER.info("[TaczHacker][握手] 收到客户端握手并回复：{}", player.getName().getString());
            }
        });
        ctx.setPacketHandled(true);
    }
}