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
 * ParCool 4.0 的按键结构（3.4 的 client.input.KeyRecorder 已不存在）：
 *   com.alrex.parcool.client.input.ParCoolKeyBinds.CRAWL         —— 爬行/滑铲键，默认 C
 *   → Input（record）的 state() 返回 InputState
 *   → InputState.isDown()
 * 反射只碰 ParCool 自己的类名/方法名（这些名字不会被重混淆），不碰原版方法。
 */
public final class ParCoolKeyAccess {

    private static boolean resolved = false;
    private static Object crawlInput = null;
    private static Method stateMethod = null;
    private static Method isDownMethod = null;

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
            Object state = stateMethod.invoke(crawlInput);
            if (state == null) {
                return false;
            }
            Object result = isDownMethod.invoke(state);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return crawlInput != null;
        }
        resolved = true;
        try {
            Class<?> keyBinds = Class.forName("com.alrex.parcool.client.input.ParCoolKeyBinds");
            Field field = keyBinds.getField("CRAWL");
            Object input = field.get(null);
            if (input == null) {
                return false;
            }
            // Input#state()（record 访问器）
            Method state = input.getClass().getMethod("state");
            state.setAccessible(true);
            Object stateObject = state.invoke(input);
            if (stateObject == null) {
                return false;
            }
            // InputState#isDown()
            Method down = stateObject.getClass().getMethod("isDown");
            down.setAccessible(true);

            crawlInput = input;
            stateMethod = state;
            isDownMethod = down;
        } catch (Throwable t) {
            crawlInput = null;
            stateMethod = null;
            isDownMethod = null;
        }
        return crawlInput != null;
    }

    /**
     * 退出世界时复位（避免残留缓存影响下一次进世界）
     */
    public static void reset() {
        resolved = false;
        crawlInput = null;
        stateMethod = null;
        isDownMethod = null;
    }
}
