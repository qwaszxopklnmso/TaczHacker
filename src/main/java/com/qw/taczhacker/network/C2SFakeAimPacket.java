package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 假开镜状态包（客户端 → 服务端）
 *
 * 客户端拦住 Tacz 的开镜包（ClientMessagePlayerAim）之后，用这个包告诉服务端
 * 「我这边算是在开镜」。服务端只记一个标记，不做任何状态改动——
 * 因此服务端的 isAiming / aimingProgress 全程保持 false / 0，
 * 移速惩罚、冲刺打断、开镜动画、灵敏度同步统统不会发生。
 *
 * 唯一读这个标记的地方是散布档位（InaccuracyType#getInaccuracyType），
 * 假开镜时直接按 AIM 档取，于是子弹享受开镜精度。
 *
 * 客户端只在自己和服务端都装了本 mod 时才发这个包。
 */
public class C2SFakeAimPacket {

    private final boolean fakeAiming;

    public C2SFakeAimPacket(boolean fakeAiming) {
        this.fakeAiming = fakeAiming;
    }

    public boolean isFakeAiming() {
        return fakeAiming;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(fakeAiming);
    }

    public static C2SFakeAimPacket decode(FriendlyByteBuf buf) {
        return new C2SFakeAimPacket(buf.readBoolean());
    }

    public static void handle(C2SFakeAimPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;
            FakeAimTracker.setFakeAiming(sender, message.fakeAiming);
            Taczhacker.LOGGER.debug("[TaczHacker][假开镜] 玩家 {} 的假开镜状态 = {}",
                    sender.getGameProfile().getName(), message.fakeAiming);
        });
        context.setPacketHandled(true);
    }
}
