package com.qw.taczhacker.feature.flight;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 功能5：飞行挂（Fly Hack）
 *
 * 表现：按住按键/开关飞行——水平向视线方向移动、垂直自由升降。
 * 零额外发包——每 tick 给玩家加小步增量速度，位置经原版移动包上传。
 *
 * 注意：单 tick 位移控制在安全范围，避免触发 "player moved wrongly" 检测。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class FlightHandler {

    /** 飞行开关状态（toggle 模式用） */
    private static boolean flying = false;

    /** 上次按键状态（用于检测上升沿） */
    private static boolean wasKeyDown = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 检查全局开关和功能开关
        if (!HackConfig.globalEnabled || !HackConfig.flightEnabled) {
            if (flying) {
                flying = false;
            }
            return;
        }

        boolean keyDown = KeyBindings.FLIGHT_KEY.isDown();

        if (HackConfig.flightToggleMode) {
            // 开关切换模式：按一次开/关
            if (keyDown && !wasKeyDown) {
                flying = !flying;
            }
        } else {
            // 按住模式：按住键才飞
            flying = keyDown;
        }

        wasKeyDown = keyDown;

        if (!flying) return;

        // ===== 执行飞行逻辑 =====

        // 禁止原版飞行状态——我们手动控制速度
        player.getAbilities().flying = false;

        // 获取玩家视线方向
        Vec3 lookAngle = player.getLookAngle();
        float yaw = player.getYRot();

        // 水平方向：视线方向的水平分量
        double lookX = -Math.sin(Math.toRadians(yaw));
        double lookZ = Math.cos(Math.toRadians(yaw));

        double horizontalSpeed = HackConfig.flightHorizontalSpeed;
        double verticalSpeed = HackConfig.flightVerticalSpeed;

        // 计算当前速度
        Vec3 motion = player.getDeltaMovement();

        // 水平速度：向视线方向平滑加速
        double targetMotionX = lookX * horizontalSpeed;
        double targetMotionZ = lookZ * horizontalSpeed;

        // 平滑过渡（避免抖动）
        double newMotionX = motion.x + (targetMotionX - motion.x) * 0.3;
        double newMotionZ = motion.z + (targetMotionZ - motion.z) * 0.3;

        // 垂直升降
        double newMotionY = 0;
        if (mc.options.keyJump.isDown()) {
            newMotionY = verticalSpeed;
        } else if (mc.options.keyShift.isDown()) {
            newMotionY = -verticalSpeed;
        } else {
            // 悬停：不主动加力，让重力自然下落（玩家可按空格上升）
            // 原为 0.04 试图抵消重力，但会导致缓慢上升
            newMotionY = 0.0;
        }

        // 应用速度（限制单 tick 位移防止被踢）
        Vec3 newMotion = new Vec3(
                Math.max(-2.0, Math.min(2.0, newMotionX)),
                Math.max(-2.0, Math.min(2.0, newMotionY)),
                Math.max(-2.0, Math.min(2.0, newMotionZ))
        );

        player.setDeltaMovement(newMotion);

        // 设置 fallDistance 为 0 防止摔落伤害
        player.fallDistance = 0;
    }

    /**
     * 是否正在飞行
     */
    public static boolean isFlying() {
        return flying;
    }
}