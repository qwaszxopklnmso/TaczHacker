package com.qw.taczhacker.feature.fullbright;

import com.mojang.logging.LogUtils;
import com.qw.taczhacker.config.HackConfig;
import com.qw.taczhacker.keybind.KeyBindings;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 功能6：伽马值修改（Fullbright）
 *
 * 按 B 键切换全亮模式，将伽马值设为配置值（默认 1.0，即原版最大亮度），
 * 关闭时恢复原版伽马值。
 *
 * 注意：Minecraft 的 LightTexture 渲染公式为
 * pixel.lerp(notGamma(pixel), Math.max(0, gamma - darknessScale))，
 * 当 gamma >= 1.0 时 lerp 因子饱和，因此 gamma=1.0 即为最大亮度。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public class FullbrightHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        LOGGER.info("[TaczHacker][全亮] FullbrightHandler 类已加载！");
    }

    /** 全亮开关状态 */
    private static boolean fullbrightActive = false;

    /** 上次按键状态（用于检测上升沿） */
    private static boolean wasKeyDown = false;

    /** 保存的原始伽马值，用于关闭时恢复 */
    private static double savedGamma = 1.0;

    /** 是否已保存原始伽马值 */
    private static boolean hasSavedGamma = false;

    /** 玩家是否刚加入世界（用于首次 tick 检测） */
    private static boolean playerJustJoined = true;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            // 玩家离开世界，重置标记
            playerJustJoined = true;
            return;
        }

        // 玩家刚加入世界时，检测并重置 gamma 状态
        if (playerJustJoined) {
            playerJustJoined = false;
            onPlayerJoinWorld(mc);
        }

        // 总开关检查
        if (!HackConfig.globalEnabled || !HackConfig.fullbrightEnabled) {
            if (fullbrightActive) {
                disableFullbright(mc);
            }
            return;
        }

        // 按键检测（上升沿触发切换）
        boolean isKeyDown = KeyBindings.FULLBRIGHT_KEY.isDown();
        if (isKeyDown && !wasKeyDown) {
            LOGGER.info("[TaczHacker][全亮] B键按下，切换全亮！当前状态={}", fullbrightActive);
            fullbrightActive = !fullbrightActive;
            if (fullbrightActive) {
                enableFullbright(mc);
            } else {
                disableFullbright(mc);
            }
        }
        wasKeyDown = isKeyDown;

        // 如果全亮激活，每 tick 强制设置伽马值（防止被其他 mod 修改）
        if (fullbrightActive) {
            double targetGamma = HackConfig.fullbrightGamma;
            double currentGamma = mc.options.gamma().get();
            if (Math.abs(currentGamma - targetGamma) > 0.01) {
                mc.options.gamma().set(targetGamma);
            }
        }
    }

    /**
     * 玩家刚加入世界时调用，重置 gamma 状态。
     *
     * 进世界时无条件将 gamma 重置为原版默认值 0.5，
     * 确保 B 键 toggle 功能可正常工作（避免上局全亮残留 gamma=1.0 导致按 B 无效）。
     */
    private static void onPlayerJoinWorld(Minecraft mc) {
        // 重置状态变量，确保 toggle 功能可正常工作
        fullbrightActive = false;
        hasSavedGamma = false;
        wasKeyDown = false;
        savedGamma = 1.0;

        // 无条件重置为原版默认 gamma 值
        mc.options.gamma().set(0.5);
        LOGGER.info("[TaczHacker][全亮] 进世界，gamma 已重置为 0.5");
    }

    /**
     * 启用全亮
     *
     * 将 gamma 设为配置值（默认 1.0，即原版最大亮度）。
     * gamma=1.0 时 LightTexture 渲染公式饱和，达到最大亮度。
     */
    private static void enableFullbright(Minecraft mc) {
        if (!hasSavedGamma) {
            savedGamma = mc.options.gamma().get();
            hasSavedGamma = true;
            LOGGER.info("[TaczHacker][全亮] 保存原始伽马={}", savedGamma);
        }
        double targetGamma = HackConfig.fullbrightGamma;
        mc.options.gamma().set(targetGamma);
        LOGGER.info("[TaczHacker][全亮] 已启用，伽马={}", mc.options.gamma().get());
    }

    /**
     * 禁用全亮，恢复原始伽马值
     */
    private static void disableFullbright(Minecraft mc) {
        if (hasSavedGamma) {
            mc.options.gamma().set(savedGamma);
        } else {
            mc.options.gamma().set(0.5);
        }
        fullbrightActive = false;
        LOGGER.info("[TaczHacker][全亮] 已禁用，恢复伽马={}", mc.options.gamma().get());
    }

    /**
     * 获取当前全亮状态（供其他类查询）
     */
    public static boolean isFullbrightActive() {
        return fullbrightActive;
    }
}