package com.qw.taczhacker.feature.autoreconnect;

import com.qw.taczhacker.Taczhacker;
import com.qw.taczhacker.config.HackConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 功能10：自动重连（Auto Reconnect）
 *
 * 需求：被踢出/掉线后停在「连接中断」界面（{@link DisconnectedScreen}）时，
 * 界面下方显示倒计时（默认 10 秒），倒计时结束自动连回上一次的服务器；
 * 也可以直接点界面上的「立即重连」按钮马上连。
 *
 * 实现要点（全部是原版公开 API，不需要 mixin）：
 * - 在 {@link ScreenEvent.Init.Post} 里认出连接中断界面：加两个按钮（立即重连 / 取消自动重连）
 * - 在 {@link ScreenEvent.Render.Post} 里画倒计时文字
 * - 在 {@link TickEvent.ClientTickEvent} 里走倒计时并调
 *   {@link ConnectScreen#startConnecting}（原版进服的唯一入口）
 *
 * 服务器信息从哪来：{@code Minecraft#getCurrentServer()} 其实读的是当前网络连接的
 * {@code ClientPacketListener#getServerData()}，**断线后连接没了就拿不到了**，
 * 所以要在还在世界里的时候每 tick 缓存一份（{@link #lastServer}）。
 * 兜底用原版的 {@code options.lastMpIp}（options.txt 里的 lastServer，就是上次进的服务器地址）。
 *
 * 局域网（LAN）世界不参与自动重连：那种服务器的地址是临时端口，重连没有意义。
 *
 * 取消自动重连有两种自然方式（都不用额外代码）：
 * - 点界面上任意会换屏的按钮（原版的「返回服务器列表」）→ 屏幕变了，倒计时自动作废
 * - 点我们加的「取消自动重连」按钮 → 直接作废
 */
@Mod.EventBusSubscriber(modid = Taczhacker.MODID, value = Dist.CLIENT)
public final class AutoReconnectHandler {

    /** 上一次联机进的服务器（断线后 mc.getCurrentServer() 会变 null，所以自己缓存一份） */
    private static ServerData lastServer = null;

    /** 当前正在倒计时的「连接中断」界面；为 null 表示没有在倒计时 */
    private static Screen armedScreen = null;

    /** 倒计时剩余 tick */
    private static int ticksLeft = 0;

    /** 已经自动重连过几次（成功进服后清零） */
    private static int attempts = 0;

    /** 「立即重连」按钮：标题会跟着倒计时刷新（跟 FDPClient 的 Reconnect (9) 一样） */
    private static Button reconnectButton = null;

    private AutoReconnectHandler() {
    }

    // ============================================================
    // 界面
    // ============================================================

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!isFeatureEnabled()) return;
        if (!(event.getScreen() instanceof DisconnectedScreen screen)) return;

        Minecraft mc = Minecraft.getInstance();
        // 单机存档 / 自己开的局域网房：没有「上一次的联机服务器」可回，不参与
        if (mc.hasSingleplayerServer()) return;

        ServerData target = resolveServer(mc);
        if (target == null) return; // 单机 / 没有上次服务器 → 不显示任何东西
        if (isAttemptLimitReached()) return;

        armedScreen = screen;
        ticksLeft = Math.max(1, HackConfig.autoReconnectDelaySeconds) * 20;

        int y = screen.height - 48;
        int centerX = screen.width / 2;
        reconnectButton = Button.builder(
                        Component.literal("立即重连"),
                        button -> reconnectNow(Minecraft.getInstance()))
                .bounds(centerX - 154, y, 150, 20).build();
        event.addListener(reconnectButton);
        event.addListener(Button.builder(
                        Component.literal("取消自动重连"),
                        button -> disarm("cancel"))
                .bounds(centerX + 4, y, 150, 20).build());
        updateButtonLabel();

        Taczhacker.LOGGER.info("自动重连：{} 秒后重连到 {}（第 {} 次）",
                HackConfig.autoReconnectDelaySeconds, target.ip, attempts + 1);
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (armedScreen == null || event.getScreen() != armedScreen) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        updateButtonLabel();

        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = event.getScreen().width / 2;
        int seconds = (ticksLeft + 19) / 20;

        String text = "自动重连：" + seconds + " 秒后自动连接"
                + (attempts > 0 ? "（第 " + (attempts + 1) + " 次尝试）" : "");
        graphics.drawCenteredString(mc.font, Component.literal(text), centerX,
                event.getScreen().height - 68, 0xFFFFCC55);
        graphics.drawCenteredString(mc.font,
                Component.literal("点「立即重连」马上连，点其它按钮取消"), centerX,
                event.getScreen().height - 80, 0xFFAAAAAA);
    }

    /** 把剩余秒数写进「立即重连」按钮标题（FDPClient 的 Reconnect (9) 同款） */
    private static void updateButtonLabel() {
        if (reconnectButton == null || armedScreen == null) return;
        reconnectButton.setMessage(Component.literal("立即重连 (" + ((ticksLeft + 19) / 20) + ")"));
    }

    // ============================================================
    // 每 tick
    // ============================================================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();

        // 1) 在游戏里：缓存服务器信息 + 清零重试计数
        if (mc.level != null) {
            attempts = 0;
            ServerData current = mc.getCurrentServer();
            if (current != null && !current.isLan()) {
                lastServer = current;
            }
            return;
        }

        // 2) 不在倒计时里就什么都不做
        if (armedScreen == null) return;

        // 功能被关掉 / 界面已经被换掉（用户点了别的按钮）→ 作废
        if (!isFeatureEnabled()) {
            disarm("disabled");
            return;
        }
        if (mc.screen != armedScreen) {
            disarm("screen-changed");
            return;
        }

        // 3) 倒计时
        if (ticksLeft > 0) {
            --ticksLeft;
            return;
        }
        if (isAttemptLimitReached()) {
            disarm("attempt-limit");
            return;
        }
        reconnectNow(mc);
    }

    // ============================================================
    // 内部
    // ============================================================

    /**
     * 立刻重连（倒计时结束或用户点了「立即重连」）
     */
    private static void reconnectNow(Minecraft mc) {
        ServerData target = resolveServer(mc);
        disarm("reconnect");
        if (target == null) return;

        ++attempts;
        Taczhacker.LOGGER.info("自动重连：正在连接 {}（第 {} 次）", target.ip, attempts);

        // 父界面用「多人游戏列表」：原版从服务器列表进服时就是这个父界面，
        // 这样万一重连失败、新出现的连接中断界面上的按钮行为跟原版一致。
        Screen parent = new JoinMultiplayerScreen(new TitleScreen());
        ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(target.ip), target, false);
    }

    private static void disarm(String reason) {
        if (armedScreen != null) {
            Taczhacker.LOGGER.debug("自动重连：取消倒计时（{}）", reason);
        }
        armedScreen = null;
        reconnectButton = null;
        ticksLeft = 0;
    }

    private static boolean isFeatureEnabled() {
        return HackConfig.globalEnabled && HackConfig.autoReconnectEnabled;
    }

    private static boolean isAttemptLimitReached() {
        int max = HackConfig.autoReconnectMaxAttempts;
        return max > 0 && attempts >= max;
    }

    /**
     * 要连回哪台服务器：优先用缓存的那份 ServerData（名字/IP 都准），
     * 没有就用原版 options.txt 里的 lastServer 兜一个（只有地址，名字也用地址）
     */
    private static ServerData resolveServer(Minecraft mc) {
        if (lastServer != null && lastServer.ip != null && !lastServer.ip.isEmpty()) {
            return lastServer;
        }
        String lastIp = mc.options == null ? null : mc.options.lastMpIp;
        if (lastIp == null || lastIp.isEmpty()) {
            return null;
        }
        return new ServerData(lastIp, lastIp, false);
    }
}
