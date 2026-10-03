package com.qw.taczhacker.feature.fakerot;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.feature.aimbot.AimbotHandler;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 功能2：低头转圈（Fake Rotation）
 *
 * 表现：他人视角中，角色低着头（pitch 接近 90°）并持续转圈（yaw 不断旋转）；
 * 本地视角完全正常。
 *
 * 核心实现：通过 Mixin LocalPlayer#sendPosition() 替换移动包中的旋转为假旋转。
 * 此类负责控制假旋转的状态（开启/关闭/角度计算）。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class FakeRotationHandler {

    /** 当前是否启用低头转圈（H 键开关，代表玩家意图） */
    private static boolean enabled = false;

    /**
     * 临时暂停剩余 tick 数。
     *
     * 触发场景：
     * - 功能3（视角锁定）按住时：暂停转圈，让玩家看到真实视角
     * - 功能1（开火静默自瞄）那一 tick：暂停转圈，保证发给服务器的是瞄准旋转
     *
     * 注意：暂停 ≠ 关闭。暂停结束后，只要 enabled 仍为 true 就继续转圈，
     * 玩家不需要重新按 H 键。
     */
    private static int suspendTicks = 0;

    /** 当前假旋转的 yaw 值（每 tick 递增） */
    private static float fakeYaw = 0.0f;

    /** 距离上次主动发包经过的 tick 数 */
    private static int ticksSinceLastPacket = 0;

    /** 暂停时长（tick）：覆盖 aimbot 松开后的一小段时间与开火瞬间 */
    public static final int SUSPEND_TICKS = 3;

    /**
     * 请求临时暂停转圈若干 tick（取最大值，避免短暂停打断长暂停）
     */
    public static void suspendFor(int ticks) {
        if (ticks > suspendTicks) {
            suspendTicks = ticks;
        }
    }

    /**
     * 每 tick 处理按键切换和旋转角度更新
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 暂停计时
        if (suspendTicks > 0) {
            suspendTicks--;
        }

        // 先检查全局开关和功能开关（在按键处理之前，避免浪费按键事件）
        if (!HackConfig.globalEnabled || !HackConfig.fakerotEnabled) {
            if (enabled) {
                enabled = false;
            }
            ticksSinceLastPacket = 0;
            suspendTicks = 0;
            return;
        }

        // 功能3（视角锁定自瞄）按住时：临时暂停转圈，而不是把开关关掉。
        // 松开按键后暂停自动结束，转圈按原开关状态继续。
        if (AimbotHandler.isActive()) {
            suspendFor(SUSPEND_TICKS);
            ticksSinceLastPacket = 0;
            return;
        }

        // 暂停中：不发包也不更新角度，但保留 enabled 状态
        if (suspendTicks > 0) {
            return;
        }

        // 按键切换
        while (KeyBindings.FAKEROT_KEY.consumeClick()) {
            if (!enabled) {
                // 刚开启：立即发送一个包让服务器知道假旋转
                enabled = true;
                ticksSinceLastPacket = Integer.MAX_VALUE; // 确保下一 tick 立即发包
            } else {
                enabled = false;
            }
        }

        if (!enabled) {
            ticksSinceLastPacket = 0;
            return;
        }

        // 每 tick 更新假旋转 yaw
        fakeYaw += (float) HackConfig.fakerotRotationSpeed;
        if (fakeYaw > 360.0f) fakeYaw -= 360.0f;
        if (fakeYaw < 0.0f) fakeYaw += 360.0f;

        // 主动发包控制
        // 注意：LocalPlayerMixin 修改了 sendPosition() 中的旋转包，但玩家不动时不调 sendPosition()
        // 所以需要主动发包来确保服务器始终持有正确的假旋转值
        int refreshInterval = HackConfig.fakerotActiveRefreshInterval;
        if (ticksSinceLastPacket >= Integer.MAX_VALUE / 2) {
            // 刚开启模式：立即发送一个包确保服务器知道假旋转
            sendFakeRotationPacket(player);
            ticksSinceLastPacket = 0;
        } else {
            // 使用有效发包间隔（<=0 时使用默认 20 tick = 1秒）
            int effectiveInterval = (refreshInterval > 0) ? refreshInterval : 20;
            ticksSinceLastPacket++;
            if (ticksSinceLastPacket >= effectiveInterval) {
                sendFakeRotationPacket(player);
                ticksSinceLastPacket = 0;
            }
        }
    }

    /**
     * 主动发送假旋转包
     * 仅在配置了主动刷新间隔时使用
     */
    private static void sendFakeRotationPacket(LocalPlayer player) {
        if (player.connection == null) return;
        float pitch = (float) HackConfig.fakerotPitchAngle;
        player.connection.send(new ServerboundMovePlayerPacket.Rot(
                fakeYaw, pitch, player.onGround()
        ));
    }

    /**
     * 获取当前假旋转的 yaw
     */
    public static float getFakeYaw() {
        return fakeYaw;
    }

    /**
     * 获取当前假旋转的 pitch（固定为配置值）
     */
    public static float getFakePitch() {
        return (float) HackConfig.fakerotPitchAngle;
    }

    /**
     * H 键开关状态（玩家意图），供 HUD 显示
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 当前是否真的在替换旋转包
     * = 开关已打开 且 未被临时暂停（aimbot / 开火）
     */
    public static boolean isActive() {
        return enabled && suspendTicks <= 0;
    }

    /**
     * 设置启用状态
     */
    public static void setEnabled(boolean enabled) {
        FakeRotationHandler.enabled = enabled;
    }
}