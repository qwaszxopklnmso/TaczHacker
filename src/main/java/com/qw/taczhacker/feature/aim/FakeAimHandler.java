package com.qw.taczhacker.feature.aim;

import com.qw.taczhacker.Taczhacker;
import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.network.C2SFakeAimPacket;
import com.qw.taczhacker.network.ServerDetector;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.client.KeyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 附加功能：Tacz 假开镜（Fake ADS）
 *
 * 需求：按开镜键时只让「精度」生效，不影响 FOV、灵敏度、移动速度。
 *
 * 做法：
 * - 客户端在 Tacz 的 LocalPlayerAim#aim(boolean) 处拦一刀（TaczLocalPlayerAimMixin），
 *   **不发** Tacz 的开镜包、也**不改**本地 clientIsAiming。
 *   于是开镜动画、相机 FOV、瞄准镜画面、右键灵敏度全部保持原样。
 * - 只发本 mod 的 C2SFakeAimPacket 告诉服务端「我在假开镜」。
 * - 服务端（FakeAimTracker + InaccuracyTypeMixin）在算散布档位时按 AIM 档取，
 *   但自己的 isAiming / aimingProgress 一动不动，所以移速惩罚、冲刺打断都不出现。
 *
 * 代价（写清楚免得踩坑）：
 * - 需要服务端也装本 mod，服务端没装时这个功能自动失效，开镜键退回 Tacz 原版行为。
 * - 因为本地 clientIsAiming 一直是 false，瞄准镜画面不会再出现——
 *   想看瞄准镜请先把「假开镜」关掉。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class FakeAimHandler {

    /** 当前是否处于假开镜状态 */
    private static boolean fakeAiming = false;

    private FakeAimHandler() {
    }

    public static boolean isFakeAiming() {
        return fakeAiming;
    }

    /**
     * 由 TaczLocalPlayerAimMixin 在 LocalPlayerAim#aim 的 HEAD 调用。
     *
     * @param requested Tacz 原本想设置的开镜状态（按住模式=true/false，切换模式=true）
     * @return true 表示这次调用被本功能接管，原方法（改本地状态 + 发原版开镜包）不要执行
     */
    public static boolean interceptAim(boolean requested) {
        if (!isFeatureUsable()) return false;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !IGun.mainHandHoldGun(player)) return false;

        boolean holdToAim = Boolean.TRUE.equals(KeyConfig.HOLD_TO_AIM.get());
        if (holdToAim) {
            // 按住模式：requested 就是按键状态
            apply(requested);
        } else if (requested) {
            // 切换模式：Tacz 每次右键只传 true，开/关由 isAim() 决定；
            // 而我们没动 isAim()（永远是 false），所以自己维护一个开关
            apply(!fakeAiming);
        }
        return true;
    }

    /**
     * 功能是否可用：总开关 + 本功能开关 + 服务端装着本 mod。
     * 服务端没装就直接放行，让 Tacz 走原版开镜。
     */
    private static boolean isFeatureUsable() {
        return HackConfig.globalEnabled && HackConfig.taczFakeAim && ServerDetector.isServerHasTaczHacker();
    }

    private static void apply(boolean state) {
        if (state == fakeAiming) return;
        fakeAiming = state;
        sendToServer(state);
    }

    private static void sendToServer(boolean state) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Connection connection = mc.getConnection() == null ? null : mc.getConnection().getConnection();
            if (connection == null || !Taczhacker.CHANNEL.isRemotePresent(connection)) return;
            Taczhacker.CHANNEL.sendToServer(new C2SFakeAimPacket(state));
        } catch (Throwable t) {
            Taczhacker.LOGGER.warn("[TaczHacker][假开镜] 状态包发送失败（不影响游戏）", t);
        }
    }

    /**
     * 兜底清理：不在游戏 / 死亡 / 换掉枪 / 功能被关掉 / 按住模式下松手，
     * 都自动退出假开镜，免得服务端那边一直以为你在开镜。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!fakeAiming) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            // 已经离开世界：包发不出去了，只清本地状态
            fakeAiming = false;
            return;
        }

        boolean abort = player.isDeadOrDying()
                || !isFeatureUsable()
                || !IGun.mainHandHoldGun(player)
                || (Boolean.TRUE.equals(KeyConfig.HOLD_TO_AIM.get())
                    && !mc.options.keyUse.isDown());
        if (abort) {
            apply(false);
        }
    }
}
