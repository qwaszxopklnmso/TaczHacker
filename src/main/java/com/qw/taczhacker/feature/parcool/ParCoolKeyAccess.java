package com.qw.taczhacker.feature.parcool;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 功能7：ParCool 长滑铲 —— ParCool 键盘状态读取（全部走反射）
 *
 * 为什么用反射而不是直接 import：
 * 本类由 ParCoolLongSlideClient（无论有没有装 ParCool 都会被加载）调用，
 * 直接 import ParCool 的类会在没装 ParCool 时抛 NoClassDefFoundError。
 * 反射可以吞掉这个异常，返回「没按下」。
 *
 * 用途：读取 ParCool 记录的「滑铲键（爬行键，默认 C）」状态，
 * 实现「松开后再按一次按键 = 退出滑铲」。
 */
public final class ParCoolKeyAccess {

    private static boolean resolved = false;
    private static Object crawlKeyState = null;
    private static Method isPressedMethod = null;

    private ParCoolKeyAccess() {
    }

    /**
     * @return 滑铲键（ParCool 爬行键）当前是否按下；没装 ParCool 时恒为 false
     */
    public static boolean isSlideKeyDown(Minecraft mc) {
        if (!resolve()) {
            return false;
        }
        try {
            Object result = isPressedMethod.invoke(crawlKeyState);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return crawlKeyState != null;
        }
        resolved = true;
        try {
            Class<?> recorder = Class.forName("com.alrex.parcool.client.input.KeyRecorder");
            Field field = recorder.getField("keyCrawlState");
            field.setAccessible(true);
            Object state = field.get(null);
            if (state == null) {
                return false;
            }
            isPressedMethod = state.getClass().getMethod("isPressed");
            isPressedMethod.setAccessible(true);
            crawlKeyState = state;
        } catch (Throwable t) {
            crawlKeyState = null;
            isPressedMethod = null;
        }
        return crawlKeyState != null;
    }

    /**
     * 退出世界时复位（避免残留缓存影响下一次进世界）
     */
    public static void reset() {
        resolved = false;
        crawlKeyState = null;
        isPressedMethod = null;
    }
}
