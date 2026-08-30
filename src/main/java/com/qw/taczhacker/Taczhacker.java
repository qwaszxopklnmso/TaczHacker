package com.qw.taczhacker;

import com.mojang.logging.LogUtils;
import com.qw.taczhacker.config.ConfigScreen;
import com.qw.taczhacker.config.HackConfig;

import com.qw.taczhacker.network.C2SHandshakePacket;
import com.qw.taczhacker.network.S2CHandshakeAckPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;

@Mod(Taczhacker.MODID)
public class Taczhacker {

    public static final String MODID = "taczhacker";
    public static final Logger LOGGER = LogUtils.getLogger();

    // ============================================================
    // 网络通道
    // ============================================================
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public Taczhacker() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册配置（COMMON 类型，服务端和客户端都加载，确保真·自瞄在专用服务器上也能读取配置）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HackConfig.SPEC, "taczhacker-common.toml");
        modEventBus.addListener(HackConfig::onLoad);

        // 注册通用事件（网络包注册）
        modEventBus.addListener(this::onCommonSetup);

        // 注册客户端事件
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            modEventBus.addListener(this::onClientSetup);

            // 注册 Cloth Config API 配置菜单
            ModLoadingContext.get().registerExtensionPoint(
                    ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, screen) ->
                            ConfigScreen.build(screen))
            );

            
        });

        LOGGER.info("TaczHacker 已加载！");
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        // 注册网络包
        int id = 0;
        CHANNEL.registerMessage(id++, C2SHandshakePacket.class,
                C2SHandshakePacket::encode, C2SHandshakePacket::decode,
                C2SHandshakePacket::handle);
        CHANNEL.registerMessage(id++, S2CHandshakeAckPacket.class,
                S2CHandshakeAckPacket::encode, S2CHandshakeAckPacket::decode,
                S2CHandshakeAckPacket::handle);
        LOGGER.info("TaczHacker 网络通道初始化完成");
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        // 按键绑定在 KeyBindings 中注册，由客户端事件触发
        LOGGER.info("TaczHacker Client 初始化完成");
    }
}