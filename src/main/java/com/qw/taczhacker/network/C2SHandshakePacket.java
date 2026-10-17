package com.qw.taczhacker.network;

import com.qw.taczhacker.Taczhacker;
import com.qw.taczhacker.config.HackConfig;
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
 *
 * 2026-10-11 起这个包还兼职**上报客户端的子弹端功能开关**（aim / homing / penetration），
 * 服务端用 {@link ClientFeatureFlags} 记下来，和它自己的配置做**与门**：
 * 「服务端开着 **且** 该玩家客户端也开着」才真正生效。
 *
 * ⚠️ **不需要改 PROTOCOL_VERSION**：三个布尔是追加在包尾的，
 * 旧版服务端的 decode 压根不读它们（多余字节直接丢弃），旧版客户端发来的空包
 * 在新版服务端这边会被防御式解码成「三项全 false」（保守：不替它开任何东西）。
 */
public class C2SHandshakePacket {

    /** 客户端功能1（开火静默自瞄）当前是否生效 */
    private final boolean aim;
    /** 客户端「真·自瞄」（服务端零散布方向覆盖）开关 */
    private final boolean trueAim;
    /** 客户端追踪弹开关 */
    private final boolean homing;
    /** 客户端穿墙子弹开关 */
    private final boolean penetration;

    /** 兼容旧调用：不带能力位（一律 false） */
    public C2SHandshakePacket() {
        this(false, false, false, false);
    }

    public C2SHandshakePacket(boolean aim, boolean trueAim, boolean homing, boolean penetration) {
        this.aim = aim;
        this.trueAim = trueAim;
        this.homing = homing;
        this.penetration = penetration;
    }

    /**
     * 编码：末尾追加四个能力位（旧版服务端只读前三个，多余的字节直接忽略）
     *
     * ⚠️ 顺序固定为 aim / homing / penetration / trueAim —— 最后那个 trueAim 是 1.8.10 新加的，
     * 只能加在**最后**，否则旧服务端会把 trueAim 当成 homing 读，语义全乱。
     */
    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(aim);
        buf.writeBoolean(homing);
        buf.writeBoolean(penetration);
        buf.writeBoolean(trueAim);
    }

    /**
     * 解码：旧版客户端只发三个（或空包）→ 缺的位按 false 处理
     * （与门里客户端算 false，服务端就不动手，保守正确）
     */
    public static C2SHandshakePacket decode(FriendlyByteBuf buf) {
        if (!buf.isReadable(3)) {
            return new C2SHandshakePacket(false, false, false, false);
        }
        boolean aim = buf.readBoolean();
        boolean homing = buf.readBoolean();
        boolean penetration = buf.readBoolean();
        boolean trueAim = buf.isReadable(1) && buf.readBoolean();
        return new C2SHandshakePacket(aim, trueAim, homing, penetration);
    }

    /**
     * 处理：服务端收到握手包，记录客户端能力位，并回复确认包
     */
    public static void handle(C2SHandshakePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            // 服务端收到握手包，说明客户端也安装了本mod
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                // 记下这个客户端的四项开关（服务端与门用）
                ClientFeatureFlags.update(player.getUUID(), msg.aim, msg.trueAim, msg.homing, msg.penetration);

                // 回复确认包到客户端，并附带服务端自己实际启用的子弹端能力
                Taczhacker.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new S2CHandshakeAckPacket(
                                HackConfig.aimEnabled,
                                HackConfig.aimTrueAim,
                                HackConfig.aimSinglePlayerHomingBullet,
                                HackConfig.aimSinglePlayerBulletPenetration)
                );
                Taczhacker.LOGGER.debug("[TaczHacker][握手] 收到客户端握手并回复：{}"
                                + "（客户端 aim={}, trueAim={}, homing={}, penetration={}）",
                        player.getName().getString(), msg.aim, msg.trueAim, msg.homing, msg.penetration);
            }
        });
        ctx.setPacketHandled(true);
    }
}