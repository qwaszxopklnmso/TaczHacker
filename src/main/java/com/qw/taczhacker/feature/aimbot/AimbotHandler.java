package com.qw.taczhacker.feature.aimbot;

import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.config.HackConfig.AimPosition;
import com.qw.taczhacker.feature.aim.TargetFilter;
import com.qw.taczhacker.feature.esp.EspRenderer;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能3：视角锁定自瞄（Aimbot / Lock-On）
 *
 * 表现：按住指定按键时，玩家视角自动平滑锁定到附近目标头部/身体。
 * 零额外发包——本地旋转经原版移动包自然上传给服务器。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class AimbotHandler {

    private static boolean active = false;

    /**
     * 当前锁定目标。
     * 由客户端线程每 tick 写入，子弹 Mixin 会在集成服务器线程 / 客户端渲染线程读取，
     * 因此必须是 volatile。
     */
    private static volatile LivingEntity currentTarget = null;

    /**
     * 每 tick 检测按键状态并执行视角锁定
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        // 检查全局开关和功能开关
        if (!HackConfig.globalEnabled || !HackConfig.aimbotEnabled) {
            if (active) {
                active = false;
                currentTarget = null;
            }
            return;
        }

        // 检测按键
        boolean keyDown = KeyBindings.AIMBOT_KEY.isDown();

        if (!keyDown) {
            if (active) {
                active = false;
                currentTarget = null;
            }
            return;
        }

        active = true;

        // 收集候选目标
        List<LivingEntity> targets = findTargets(player, mc);

        if (targets.isEmpty()) {
            currentTarget = null;
            return;
        }

        // 选最优目标（最接近准星方向）
        LivingEntity bestTarget = selectBestTarget(player, targets);

        if (bestTarget == null) {
            currentTarget = null;
            return;
        }

        currentTarget = bestTarget;

        // 计算瞄准角度
        Vec3 targetPos = getAimPosition(bestTarget);
        Vec3 playerEye = player.getEyePosition(1.0f);

        double dx = targetPos.x - playerEye.x;
        double dy = targetPos.y - playerEye.y;
        double dz = targetPos.z - playerEye.z;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.01) return;

        // 计算 yaw 和 pitch
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        // 平滑插值
        float smoothness = (float) HackConfig.aimbotSmoothness;
        float currentYaw = player.getYRot();
        float currentPitch = player.getXRot();

        // 处理 yaw 绕圈
        float yawDiff = targetYaw - currentYaw;
        while (yawDiff > 180) yawDiff -= 360;
        while (yawDiff < -180) yawDiff += 360;

        float lerpFactor = 1.0f - smoothness;
        if (lerpFactor < 0.01f) lerpFactor = 0.01f;

        float newYaw = currentYaw + yawDiff * lerpFactor;
        float newPitch = currentPitch + (targetPitch - currentPitch) * lerpFactor;

        player.setYRot(newYaw);
        player.setXRot(newPitch);
    }

    /**
     * 收集附近所有符合条件的 LivingEntity
     */
    private static List<LivingEntity> findTargets(LocalPlayer player, Minecraft mc) {
        List<LivingEntity> targets = new ArrayList<>();
        double range = HackConfig.aimbotRange;

        AABB searchBox = player.getBoundingBox().inflate(range);

        if (mc.level != null) {
            for (var entity : mc.level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
                // 目标过滤（创造/旁观、队友、已驯服、盔甲架、目标类型）
                if (!TargetFilter.isValid(player, entity)) continue;

                // 视线检查
                if (!HackConfig.aimbotPassThroughWalls) {
                    if (mc.level != null && mc.level.clip(new ClipContext(
                            player.getEyePosition(1.0f),
                            entity.getEyePosition(1.0f),
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            player
                    )).getType() != HitResult.Type.MISS) {
                        continue;
                    }
                }

                // 距离检查
                if (player.distanceTo(entity) > range) continue;

                targets.add(entity);
            }
        }

        return targets;
    }

    /**
     * 选最优目标（最接近准星方向）
     *
     * 修复：旧实现用 min() + 哨兵值 Double.MAX_VALUE 来"排除"身后的目标，
     * 但 min() 依然会返回该元素，导致视角被甩到身后。
     * 现在改为先按 FOV 过滤，再比较夹角。
     */
    private static LivingEntity selectBestTarget(LocalPlayer player, List<LivingEntity> targets) {
        Vec3 lookVec = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition(1.0f);

        // 配置的是"视场角的一半"（与准星的夹角），180 度表示不限制
        double fovDegrees = HackConfig.aimbotFov;
        double minDot = fovDegrees >= 180.0 ? -1.0 : Math.cos(Math.toRadians(fovDegrees));

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (LivingEntity target : targets) {
            Vec3 toTarget = target.getEyePosition(1.0f).subtract(eyePos);
            if (toTarget.lengthSqr() < 1.0E-6) continue;

            double dot = lookVec.dot(toTarget.normalize());
            if (dot < minDot) continue; // 不在视场角内

            double score = 1.0 - dot; // 夹角越小，值越小
            if (score < bestScore) {
                bestScore = score;
                best = target;
            }
        }

        return best;
    }

    /**
     * 获取瞄准位置（头部或身体）
     */
    private static Vec3 getAimPosition(LivingEntity target) {
        Vec3 eyePos = target.getEyePosition(1.0f);
        if (HackConfig.aimbotAimPosition == AimPosition.HEAD) {
            // 头部：眼睛位置 + 小偏移
            return eyePos.add(0, 0.1, 0);
        } else {
            // 身体：脚部位置 + 身体高度一半
            return target.position().add(0, target.getBbHeight() * 0.5, 0);
        }
    }

    /**
     * 获取当前锁定目标（供其他模块使用）
     */
    public static LivingEntity getCurrentTarget() {
        return currentTarget;
    }

    /**
     * 是否正在锁定
     */
    public static boolean isActive() {
        return active;
    }

    /**
     * FOV 圈：把 aimbot.fov 的搜索范围画在屏幕上（纯客户端，常显，不用按住 V）
     *
     * 半径换算：aimbot.fov 是「从准星算起的最大夹角」（也就是半角），
     * MC 的 fov 设置是垂直 FOV。屏幕像素是方的，所以
     *     r = (屏幕高 / 2) * tan(夹角) / tan(垂直FOV / 2)
     * 这个式子在水平和垂直方向同时成立。
     *
     * 注意：默认 fov = 75° 时半径比屏幕还大（整个屏幕都在范围内），
     * 圈会落在屏幕外看不见 —— 调到 40° 以下才有明显的圈。
     */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (!HackConfig.globalEnabled || !HackConfig.aimbotEnabled || !HackConfig.aimbotFovCircle) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double fovDegrees = HackConfig.aimbotFov;
        if (fovDegrees <= 0.0D) return;

        float screenWidth = mc.getWindow().getGuiScaledWidth();
        float screenHeight = mc.getWindow().getGuiScaledHeight();

        double verticalFov = mc.options.fov().get();
        if (verticalFov <= 1.0D) return;

        double radius = (screenHeight / 2.0D) * Math.tan(Math.toRadians(fovDegrees))
                / Math.tan(Math.toRadians(verticalFov / 2.0D));

        // 半径太离谱就不画，免得整屏都在画屏幕外的无效像素
        if (radius > Math.max(screenWidth, screenHeight) * 3.0D) return;

        int color = HackConfig.aimbotFovCircleColor | 0xFF000000;
        EspRenderer.drawCircle(event.getGuiGraphics(),
                screenWidth / 2.0F, screenHeight / 2.0F, (float) radius, color, 1.0F);
    }
}