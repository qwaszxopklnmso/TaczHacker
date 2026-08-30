package com.qw.taczhacker.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * TaczHacker 按键绑定管理
 * 所有功能按键在此集中注册
 */
@Mod.EventBusSubscriber(modid = "taczhacker", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class KeyBindings {

    // 功能3：视角锁定自瞄（按住触发）
    public static final KeyMapping AIMBOT_KEY = new KeyMapping(
            "key.taczhacker.aimbot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,  // 默认 V 键
            "key.category.taczhacker"
    );

    // 功能5：飞行挂
    public static final KeyMapping FLIGHT_KEY = new KeyMapping(
            "key.taczhacker.flight",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,  // 默认 G 键
            "key.category.taczhacker"
    );

    // 功能4：透视（开关切换）
    public static final KeyMapping XRAY_KEY = new KeyMapping(
            "key.taczhacker.xray",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,  // 默认 X 键
            "key.category.taczhacker"
    );

    // 功能2：低头转圈（开关切换）
    public static final KeyMapping FAKEROT_KEY = new KeyMapping(
            "key.taczhacker.fakerot",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,  // 默认 H 键
            "key.category.taczhacker"
    );

    // 功能6：伽马值修改（全亮/夜视，开关切换）
    public static final KeyMapping FULLBRIGHT_KEY = new KeyMapping(
            "key.taczhacker.fullbright",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,  // 默认 B 键
            "key.category.taczhacker"
    );

    

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(AIMBOT_KEY);
        event.register(FLIGHT_KEY);
        event.register(XRAY_KEY);
        event.register(FAKEROT_KEY);
        event.register(FULLBRIGHT_KEY);
        
    }
}