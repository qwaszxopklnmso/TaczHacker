package com.qw.taczhacker.config;

import com.qw.taczhacker.Taczhacker;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config API 配置屏幕
 *
 * 为 TaczHacker 所有功能提供可视化配置 UI，
 * 通过 Forge 的 mods 列表 → TaczHacker → "配置" 按钮进入。
 *
 * AGENTS.md 要求：万物皆 UI 配置，每个功能参数都能在 UI 里改。
 */
public class ConfigScreen {

    /**
     * 构建 Cloth Config 配置屏幕
     *
     * @param parent 上一级屏幕（mods 列表）
     * @return 配置屏幕实例
     */
    public static Screen build(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("TaczHacker 配置"));
        ConfigEntryBuilder e = builder.entryBuilder();

        // ============================================================
        // 全局设置
        // ============================================================
        ConfigCategory global = builder.getOrCreateCategory(Component.literal("全局设置"));
        global.addEntry(e.startBooleanToggle(
                Component.literal("全局总开关"),
                HackConfig.globalEnabled
        ).setTooltip(Component.literal("关闭后所有功能禁用"))
                .setSaveConsumer(v -> HackConfig.globalEnabled = v)
                .build());

        // ============================================================
        // 功能1：开火静默自瞄
        // ============================================================
        ConfigCategory aimCat = builder.getOrCreateCategory(Component.literal("功能1：开火静默自瞄"));
        aimCat.addEntry(e.startBooleanToggle(
                Component.literal("自瞄开关"),
                HackConfig.aimEnabled
        ).setTooltip(Component.literal("开火时自动瞄准最近目标"))
                .setSaveConsumer(v -> HackConfig.aimEnabled = v)
                .build());
        aimCat.addEntry(e.startDoubleField(
                Component.literal("锁定半径（格）"),
                HackConfig.aimLockRadius
        ).setTooltip(Component.literal("在这个半径内搜索目标"))
                .setMin(1.0).setMax(256.0)
                .setSaveConsumer(v -> HackConfig.aimLockRadius = v)
                .build());
        aimCat.addEntry(e.startDoubleField(
                Component.literal("追踪锥角（度）"),
                HackConfig.aimConeAngle
        ).setTooltip(Component.literal("0=仅准星方向，180=全向"))
                .setMin(0.0).setMax(180.0)
                .setSaveConsumer(v -> HackConfig.aimConeAngle = v)
                .build());
        aimCat.addEntry(e.startDoubleField(
                Component.literal("提前量系数"),
                HackConfig.aimPredictionFactor
        ).setTooltip(Component.literal("0=直瞄当前位置，1.0=全额预测移动目标"))
                .setMin(0.0).setMax(5.0)
                .setSaveConsumer(v -> HackConfig.aimPredictionFactor = v)
                .build());
        aimCat.addEntry(e.startBooleanToggle(
                Component.literal("穿透障碍物选目标"),
                HackConfig.aimPassThroughWalls
        ).setTooltip(Component.literal("仅影响目标选择，不影响子弹是否撞墙"))
                .setSaveConsumer(v -> HackConfig.aimPassThroughWalls = v)
                .build());
        aimCat.addEntry(e.startBooleanToggle(
                Component.literal("仅持枪时生效"),
                HackConfig.aimRequireGunEquipped
        ).setTooltip(Component.literal("不持枪时自瞄不触发"))
                .setSaveConsumer(v -> HackConfig.aimRequireGunEquipped = v)
                .build());
        aimCat.addEntry(e.startDoubleField(
                Component.literal("子弹速度（格/tick）"),
                HackConfig.aimBulletSpeed
        ).setTooltip(Component.literal("用于提前量预测。手枪≈10，步枪≈20，狙击≈30"))
                .setMin(0.0).setMax(100.0)
                .setSaveConsumer(v -> HackConfig.aimBulletSpeed = v)
                .build());
        aimCat.addEntry(e.startDoubleField(
                Component.literal("后坐力补偿（度）"),
                HackConfig.aimRecoilCompensation
        ).setTooltip(Component.literal("补偿枪械后坐力，正数=向上压枪，从1.0开始试"))
                .setMin(-20.0).setMax(20.0)
                .setSaveConsumer(v -> HackConfig.aimRecoilCompensation = v)
                .build());
        // 单机/局域网专属
        aimCat.addEntry(e.startBooleanToggle(
                Component.literal("[单机]穿墙子弹"),
                HackConfig.aimSinglePlayerBulletPenetration
        ).setTooltip(Component.literal("仅单机/局域网有效：子弹穿透方块"))
                .setSaveConsumer(v -> HackConfig.aimSinglePlayerBulletPenetration = v)
                .build());
        aimCat.addEntry(e.startBooleanToggle(
                Component.literal("[单机]真·追踪弹"),
                HackConfig.aimSinglePlayerHomingBullet
        ).setTooltip(Component.literal("仅单机/局域网有效：子弹飞行中转向目标"))
                .setSaveConsumer(v -> HackConfig.aimSinglePlayerHomingBullet = v)
                .build());

        // ============================================================
        // 功能2：低头转圈
        // ============================================================
        ConfigCategory fakerotCat = builder.getOrCreateCategory(Component.literal("功能2：低头转圈"));
        fakerotCat.addEntry(e.startBooleanToggle(
                Component.literal("转圈开关"),
                HackConfig.fakerotEnabled
        ).setSaveConsumer(v -> HackConfig.fakerotEnabled = v).build());
        fakerotCat.addEntry(e.startDoubleField(
                Component.literal("低头角度（度）"),
                HackConfig.fakerotPitchAngle
        ).setTooltip(Component.literal("90=完全低头看地"))
                .setMin(0.0).setMax(90.0)
                .setSaveConsumer(v -> HackConfig.fakerotPitchAngle = v)
                .build());
        fakerotCat.addEntry(e.startDoubleField(
                Component.literal("旋转速度（度/tick）"),
                HackConfig.fakerotRotationSpeed
        ).setMin(0.0).setMax(360.0)
                .setSaveConsumer(v -> HackConfig.fakerotRotationSpeed = v)
                .build());
        fakerotCat.addEntry(e.startIntField(
                Component.literal("主动发包间隔（tick）"),
                HackConfig.fakerotActiveRefreshInterval
        ).setTooltip(Component.literal("0=不主动发包，仅随原版周期包"))
                .setMin(0).setMax(100)
                .setSaveConsumer(v -> HackConfig.fakerotActiveRefreshInterval = v)
                .build());
        fakerotCat.addEntry(e.startBooleanToggle(
                Component.literal("仅持枪时生效"),
                HackConfig.fakerotRequireGunEquipped
        ).setSaveConsumer(v -> HackConfig.fakerotRequireGunEquipped = v).build());

        // ============================================================
        // 功能3：视角锁定自瞄
        // ============================================================
        ConfigCategory aimbotCat = builder.getOrCreateCategory(Component.literal("功能3：视角锁定自瞄"));
        aimbotCat.addEntry(e.startBooleanToggle(
                Component.literal("视角锁定开关"),
                HackConfig.aimbotEnabled
        ).setSaveConsumer(v -> HackConfig.aimbotEnabled = v).build());
        aimbotCat.addEntry(e.startDoubleField(
                Component.literal("锁定范围（格）"),
                HackConfig.aimbotRange
        ).setMin(1.0).setMax(256.0)
                .setSaveConsumer(v -> HackConfig.aimbotRange = v)
                .build());
        aimbotCat.addEntry(e.startDoubleField(
                Component.literal("平滑度"),
                HackConfig.aimbotSmoothness
        ).setTooltip(Component.literal("0=瞬移，1=极慢，建议0.3-0.7"))
                .setMin(0.0).setMax(1.0)
                .setSaveConsumer(v -> HackConfig.aimbotSmoothness = v)
                .build());
        aimbotCat.addEntry(e.startBooleanToggle(
                Component.literal("穿透障碍物瞄准"),
                HackConfig.aimbotPassThroughWalls
        ).setSaveConsumer(v -> HackConfig.aimbotPassThroughWalls = v).build());
        aimbotCat.addEntry(e.startEnumSelector(
                Component.literal("瞄准位置"),
                HackConfig.AimPosition.class,
                HackConfig.aimbotAimPosition
        ).setSaveConsumer(v -> HackConfig.aimbotAimPosition = v).build());

        // ============================================================
        // 功能4：透视
        // ============================================================
        ConfigCategory xrayCat = builder.getOrCreateCategory(Component.literal("功能4：透视 X-ray"));
        xrayCat.addEntry(e.startBooleanToggle(
                Component.literal("透视开关"),
                HackConfig.xrayEnabled
        ).setSaveConsumer(v -> HackConfig.xrayEnabled = v).build());
        xrayCat.addEntry(e.startStrList(
                Component.literal("目标方块列表"),
                (java.util.List<String>) HackConfig.xrayTargetBlocks
        ).setTooltip(Component.literal("ResourceLocation 格式，如 minecraft:diamond_ore"))
                .setSaveConsumer(v -> HackConfig.xrayTargetBlocks = v)
                .build());
        xrayCat.addEntry(e.startBooleanToggle(
                Component.literal("目标方块高亮"),
                HackConfig.xrayHighlightTargets
        ).setTooltip(Component.literal("目标方块全彩显示，不受半透明影响"))
                .setSaveConsumer(v -> HackConfig.xrayHighlightTargets = v)
                .build());

        // ============================================================
        // 功能5：飞行挂
        // ============================================================
        ConfigCategory flightCat = builder.getOrCreateCategory(Component.literal("功能5：飞行挂"));
        flightCat.addEntry(e.startBooleanToggle(
                Component.literal("飞行开关"),
                HackConfig.flightEnabled
        ).setTooltip(Component.literal("注意：有反作弊的服务器有风险！"))
                .setSaveConsumer(v -> HackConfig.flightEnabled = v)
                .build());
        flightCat.addEntry(e.startDoubleField(
                Component.literal("水平速度（格/tick）"),
                HackConfig.flightHorizontalSpeed
        ).setTooltip(Component.literal("建议 ≤0.5 避免触发位置校验"))
                .setMin(0.05).setMax(2.0)
                .setSaveConsumer(v -> HackConfig.flightHorizontalSpeed = v)
                .build());
        flightCat.addEntry(e.startDoubleField(
                Component.literal("垂直速度（格/tick）"),
                HackConfig.flightVerticalSpeed
        ).setMin(0.05).setMax(2.0)
                .setSaveConsumer(v -> HackConfig.flightVerticalSpeed = v)
                .build());
        flightCat.addEntry(e.startBooleanToggle(
                Component.literal("切换模式"),
                HackConfig.flightToggleMode
        ).setTooltip(Component.literal("true=按一次开/关，false=按住才飞"))
                .setSaveConsumer(v -> HackConfig.flightToggleMode = v)
                .build());

        // ============================================================
        // 功能6：伽马值修改（Fullbright）
        // ============================================================
        ConfigCategory fullbrightCat = builder.getOrCreateCategory(Component.literal("功能6：伽马值修改"));
        fullbrightCat.addEntry(e.startBooleanToggle(
                Component.literal("全亮开关"),
                HackConfig.fullbrightEnabled
        ).setTooltip(Component.literal("按 B 键切换全亮"))
                .setSaveConsumer(v -> HackConfig.fullbrightEnabled = v)
                .build());
        fullbrightCat.addEntry(e.startDoubleField(
                Component.literal("伽马值"),
                HackConfig.fullbrightGamma
        ).setTooltip(Component.literal("0.0=暗，1.0=最大亮度（渲染公式饱和，超过1.0不会更亮）"))
                .setMin(0.0).setMax(1.0)
                .setSaveConsumer(v -> HackConfig.fullbrightGamma = v)
                .build());

        // 保存回调：用户点击"保存并退出"时持久化配置
        builder.setSavingRunnable(() -> {
            Taczhacker.LOGGER.info("TaczHacker 配置已保存");
            HackConfig.save();
        });

        return builder.build();
    }
}