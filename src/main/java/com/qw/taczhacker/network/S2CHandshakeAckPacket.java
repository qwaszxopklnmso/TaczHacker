package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端 握手确认包
 *
 * 服务端收到 C2SHandshakePacket 后回复此包，客户端收到后标记服务端已安装本mod。
 *
 * 客户端收到此包 → 判定服务端有 TaczHacker → 使用真·自瞄（子弹方向修改）
 * 客户端未收到此包 → 判定服务端无 TaczHacker → 使用功能1（转视角自瞄）
 */
public class S2CHandshakeAckPacket {

    /**
     * 编码：此包不含数据，无需写入
     */
    public void encode(FriendlyByteBuf buf) {
        // 空包，无需写入任何数据
    }

    /**
     * 解码：此包不含数据，直接返回新实例
     */
    public static S2CHandshakeAckPacket decode(FriendlyByteBuf buf) {
        return new S2CHandshakeAckPacket();
    }

    /**
     * 处理：客户端收到确认包，标记服务端已安装本mod
     */
    public static void handle(S2CHandshakeAckPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // 客户端收到确认包，标记服务端已安装本mod
            ServerDetector.setServerHasTaczHacker(true);
            Taczhacker.LOGGER.info("[TaczHacker][握手] 收到服务端确认，服务端已安装 TaczHacker");
        });
        ctx.setPacketHandled(true);
    }
}