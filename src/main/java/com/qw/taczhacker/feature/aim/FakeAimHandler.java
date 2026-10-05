package com.qw.taczhacker.feature.aim;

import com.qw.taczhacker.Taczhacker;
import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.network.C2SFakeAimPacket;
import com.qw.taczhacker.network.ServerDetector;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 附加功能：Tacz 自动假开镜（Fake ADS）
 *
 * 需求：功能1（开火静默自瞄）开着的时候，自动处于「开镜」状态拿开镜精度，
 * 但不影响 FOV、鼠标灵敏度、移动速度，也不需要按右键。
 *
 * 做法：
 * - 客户端在 Tacz 的 LocalPlayerAim#aim(boolean) 处拦一刀（TaczLocalPlayerAimMixin），
 *   **不发** Tacz 的开镜包、也**不改**本地 clientIsAiming。
 *   于是开镜动画、相机 FOV、瞄准镜画面、右键灵敏度全部保持原样。
 * - 只发本 mod 的 C2SFakeAimPacket 告诉服务端「我在假开镜」。
 * - 服务端（FakeAimTracker + InaccuracyTypeMixin）在算散布档位时按 AIM 档取，
 *   但自己的 isAiming / aimingProgress 一动不动，所以移速惩罚、冲刺打断都不出现。
 *
 * 为什么是「持续开着」而不是「开火那一瞬间才开」：
 * 服务端算散布是在收到开火包的时候，而假开镜状态要提前一个包送到，
 * 时序对不上就有几率第一枪吃不到精度。持续开着没有代价——
 * 服务端除了散布档位之外什么都没改，所以一直挂着最稳。
 *
 * 代价（写清楚免得踩坑）：
 * - 需要服务端也装本 mod，服务端没装时这个功能自动失效，开镜键退回 Tacz 原版行为。
 * - 假开镜生效期间，开镜键（右键）不再触发 Tacz 的原版开镜——
 *   本地 clientIsAiming 一直是 false，所以瞄准镜画面不会出现。
 *   想看瞄准镜：关掉「假开镜」或者关掉功能1。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class FakeAimHandler {

    /** 当前是否处于假开镜状态（也就是「服务端认为你在开镜」） */
    private static boolean fakeAiming = false;

    private FakeAimHandler() {
    }

    public static boolean isFakeAiming() {
        return fakeAiming;
    }

    /**
     * 当前是否应该处于假开镜状态。
     *
     * 条件：总开关 + 「假开镜」配置 + 服务端装着本 mod + 功能1 正在生效 + 手里拿着枪。
     */
    public static boolean shouldFakeAim() {
        if (!HackConfig.globalEnabled || !HackConfig.taczFakeAim) return false;
        if (!ServerDetector.isServerHasTaczHacker()) return false;
        if (!AimHandler.isActive()) return false;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isDeadOrDying()) return false;
        return IGun.mainHandHoldGun(player);
    }

    /**
     * 由 TaczLocalPlayerAimMixin 在 LocalPlayerAim#aim 的 HEAD 调用。
     *
     * @return true 表示这次调用被本功能接管，原方法（改本地状态 + 发原版开镜包）不要执行
     */
    public static boolean interceptAim(boolean requested) {
        // 自动假开镜生效期间，右键不再触发 Tacz 的原版开镜：
        // 一旦让原版开镜跑起来，服务端的 isAiming 就会变 true，移速惩罚和灵敏度就都来了。
        return shouldFakeAim() || fakeAiming;
    }

    /**
     * 每 tick 把假开镜状态同步到「应该开」——状态变化时才发包。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        boolean want = shouldFakeAim();
        if (want == fakeAiming) return;

        fakeAiming = want;
        // 已经离开世界（player 为 null）时包发不出去，只清本地状态
        if (Minecraft.getInstance().player != null) {
            sendToServer(want);
        }
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
}
