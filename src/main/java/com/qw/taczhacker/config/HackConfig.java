package com.qw.taczhacker.config;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;

import java.util.List;

/**
 * TaczHacker 功能配置中心
 * 所有 hack 功能的配置项集中管理，使用 CLIENT 配置类型
 */
@Mod.EventBusSubscriber(modid = "taczhacker", bus = Mod.EventBusSubscriber.Bus.MOD)
public class HackConfig {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ============================================================
    // 全局设置
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue GLOBAL_ENABLED = BUILDER
            .comment("全局总开关。关闭后所有功能禁用。", "Global master switch. All features disabled when off.")
            .define("global.enabled", true);

    // ============================================================
    // 功能1：开火静默自瞄（Silent Aim）
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue AIM_ENABLED = BUILDER
            .comment("功能1：开火静默自瞄总开关", "Silent aim master switch")
            .define("aim.enabled", false);

    private static final ForgeConfigSpec.DoubleValue AIM_LOCK_RADIUS = BUILDER
            .comment("锁定半径（格）", "Lock radius in blocks")
            .defineInRange("aim.lockRadius", 64.0, 1.0, 256.0);

    private static final ForgeConfigSpec.DoubleValue AIM_CONE_ANGLE = BUILDER
            .comment("追踪锥角（度），0=仅准星方向，180=全向", "Tracking cone angle in degrees")
            .defineInRange("aim.coneAngle", 45.0, 0.0, 180.0);

    private static final ForgeConfigSpec.DoubleValue AIM_PREDICTION_FACTOR = BUILDER
            .comment("提前量系数（0=直瞄当前位置，1.0=全额预测）", "Prediction factor for moving targets")
            .defineInRange("aim.predictionFactor", 1.0, 0.0, 3.0);

    private static final ForgeConfigSpec.BooleanValue AIM_PASS_THROUGH_WALLS = BUILDER
            .comment("是否穿透障碍物选目标（仅影响目标选择，不影响子弹是否撞墙）", "Pass through walls for target selection only")
            .define("aim.passThroughWalls", false);

    private static final ForgeConfigSpec.BooleanValue AIM_REQUIRE_GUN_EQUIPPED = BUILDER
            .comment("是否仅持枪时生效", "Only work when gun is equipped")
            .define("aim.requireGunEquipped", true);

    private static final ForgeConfigSpec.DoubleValue AIM_BULLET_SPEED = BUILDER
            .comment("子弹速度（格/tick），用于提前量预测。不同枪弹速不同，建议值：\n"
                    + "手枪 ≈ 10，步枪 ≈ 20，狙击 ≈ 30，霰弹 ≈ 8。\n"
                    + "如果设为 0，则跳过提前量预测（直瞄当前位置）。",
                    "Bullet speed in blocks/tick for prediction. "
                    + "Pistol ≈ 10, Rifle ≈ 20, Sniper ≈ 30, Shotgun ≈ 8. "
                    + "Set to 0 to disable prediction (aim at current position).")
            .defineInRange("aim.bulletSpeed", 16.5, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue AIM_RECOIL_COMPENSATION = BUILDER
            .comment("后坐力补偿（度），补偿枪械后坐力对弹道的影响。\n"
                    + "正数=向下压枪（对抗上跳后坐力），负数=向上补偿。\n"
                    + "不同枪后坐力不同，建议值 0.5~5.0，从 1.0 开始试。",
                    "Recoil compensation in degrees. Positive = compensate down (counteract upward recoil), "
                    + "negative = compensate up. Start with 1.0.")
            .defineInRange("aim.recoilCompensation", 0.1, -20.0, 20.0);

    // 单机/局域网专属
    private static final ForgeConfigSpec.BooleanValue AIM_SINGLE_PLAYER_BULLET_PENETRATION = BUILDER
            .comment("【仅单机/局域网有效】穿墙子弹：子弹穿透方块", "SINGLE PLAYER ONLY: Bullet penetrates blocks")
            .define("aim.singlePlayerBulletPenetration", false);

    private static final ForgeConfigSpec.BooleanValue AIM_SINGLE_PLAYER_HOMING_BULLET = BUILDER
            .comment("【仅单机/局域网有效】真·追踪弹：子弹飞行中转向目标", "SINGLE PLAYER ONLY: Homing bullet")
            .define("aim.singlePlayerHomingBullet", false);

    // ============================================================
    // 功能2：低头转圈（Fake Rotation）
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue FAKEROT_ENABLED = BUILDER
            .comment("功能2：低头转圈总开关", "Fake rotation master switch")
            .define("fakerot.enabled", true);

    private static final ForgeConfigSpec.DoubleValue FAKEROT_PITCH_ANGLE = BUILDER
            .comment("低头角度（度），90=完全低头看地", "Pitch angle while faking")
            .defineInRange("fakerot.pitchAngle", 90.0, 0.0, 90.0);

    private static final ForgeConfigSpec.DoubleValue FAKEROT_ROTATION_SPEED = BUILDER
            .comment("旋转速度（度/tick）", "Yaw rotation speed in degrees per tick")
            .defineInRange("fakerot.rotationSpeed", 15.0, 0.0, 360.0);

    private static final ForgeConfigSpec.IntValue FAKEROT_ACTIVE_REFRESH_INTERVAL = BUILDER
            .comment("主动发包刷新间隔（tick），0=使用默认值（20 tick=1秒），数值越小别人视角越流畅但风险越高。建议 ≤10 tick",
                    "Active packet refresh interval in ticks. 0=use default (20 ticks=1s). Smaller values = smoother but riskier.")
            .defineInRange("fakerot.activeRefreshInterval", 0, 0, 100);

    private static final ForgeConfigSpec.BooleanValue FAKEROT_REQUIRE_GUN_EQUIPPED = BUILDER
            .comment("是否仅持枪时生效", "Only work when gun is equipped")
            .define("fakerot.requireGunEquipped", false);

    // ============================================================
    // 功能3：视角锁定自瞄（Aimbot）
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue AIMBOT_ENABLED = BUILDER
            .comment("功能3：视角锁定自瞄总开关", "Aimbot master switch")
            .define("aimbot.enabled", true);

    private static final ForgeConfigSpec.DoubleValue AIMBOT_RANGE = BUILDER
            .comment("锁定范围（格）", "Lock range in blocks")
            .defineInRange("aimbot.range", 64.0, 1.0, 256.0);

    private static final ForgeConfigSpec.DoubleValue AIMBOT_SMOOTHNESS = BUILDER
            .comment("平滑度（0=瞬移，1=极慢），建议 0.3-0.7", "Smoothness factor, 0=instant, 1=very slow")
            .defineInRange("aimbot.smoothness", 0.5, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue AIMBOT_PASS_THROUGH_WALLS = BUILDER
            .comment("是否穿透障碍物瞄准", "Ignore line-of-sight check")
            .define("aimbot.passThroughWalls", false);

    private static final ForgeConfigSpec.EnumValue<AimPosition> AIMBOT_AIM_POSITION = BUILDER
            .comment("瞄准位置：HEAD（头部）| BODY（身体）", "Aim position: HEAD or BODY")
            .defineEnum("aimbot.aimPosition", AimPosition.HEAD);

    // ============================================================
    // 功能4：透视（X-ray）
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue XRAY_ENABLED = BUILDER
            .comment("功能4：透视总开关", "X-ray master switch")
            .define("xray.enabled", true);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> XRAY_TARGET_BLOCKS = BUILDER
            .comment("目标方块列表（ResourceLocation），如 [\"minecraft:diamond_ore\", \"minecraft:iron_ore\"]")
            .defineListAllowEmpty("xray.targetBlocks", List.of(
                    "minecraft:diamond_ore",
                    "minecraft:deepslate_diamond_ore",
                    "minecraft:iron_ore",
                    "minecraft:deepslate_iron_ore",
                    "minecraft:gold_ore",
                    "minecraft:deepslate_gold_ore",
                    "minecraft:coal_ore",
                    "minecraft:deepslate_coal_ore",
                    "minecraft:emerald_ore",
                    "minecraft:deepslate_emerald_ore",
                    "minecraft:copper_ore",
                    "minecraft:deepslate_copper_ore",
                    "minecraft:lapis_ore",
                    "minecraft:deepslate_lapis_ore",
                    "minecraft:redstone_ore",
                    "minecraft:deepslate_redstone_ore",
                    "minecraft:nether_quartz_ore",
                    "minecraft:nether_gold_ore",
                    "minecraft:ancient_debris"
            ), s -> s instanceof String);

    private static final ForgeConfigSpec.BooleanValue XRAY_HIGHLIGHT_TARGETS = BUILDER
            .comment("目标方块是否高亮显示", "Highlight target blocks")
            .define("xray.highlightTargets", true);

    private static final ForgeConfigSpec.BooleanValue XRAY_ENTITY_ESP = BUILDER
            .comment("启用实体 ESP（透过墙体显示实体方框/标签）", "Enable entity ESP box/label rendering")
            .define("xray.entityESP", false);

    // ============================================================
    // 功能6：伽马值修改（Fullbright）
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue FULLBRIGHT_ENABLED = BUILDER
            .comment("功能6：伽马值修改总开关",
                    "Fullbright master switch")
            .define("fullbright.enabled", true);

    private static final ForgeConfigSpec.DoubleValue FULLBRIGHT_GAMMA = BUILDER
            .comment("伽马值（0.0=暗，1.0=最大亮度，超过1.0不会更亮因为渲染公式饱和）",
                    "Gamma value (0.0=dark, 1.0=max brightness). Values > 1.0 have no effect due to rendering formula saturation.")
            .defineInRange("fullbright.gamma", 1.0, 0.0, 1.0);

    // ============================================================
    // 功能5：飞行挂（Fly Hack）
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue FLIGHT_ENABLED = BUILDER
            .comment("功能5：飞行挂总开关。注意：有反作弊的服务器有风险！",
                    "Flight master switch. CAUTION: Risky on anti-cheat servers!")
            .define("flight.enabled", true);

    private static final ForgeConfigSpec.DoubleValue FLIGHT_HORIZONTAL_SPEED = BUILDER
            .comment("水平自动前进速度（格/tick），建议 ≤0.5 避免触发位置校验",
                    "Horizontal speed in blocks/tick. Keep ≤0.5 to avoid position checks.")
            .defineInRange("flight.horizontalSpeed", 0.0, 0.0, 2.5);

    private static final ForgeConfigSpec.DoubleValue FLIGHT_VERTICAL_SPEED = BUILDER
            .comment("垂直飞行速度（格/tick）", "Vertical speed in blocks/tick")
            .defineInRange("flight.verticalSpeed", 0.4, 0.0, 2.5);

    private static final ForgeConfigSpec.BooleanValue FLIGHT_TOGGLE_MODE = BUILDER
            .comment("true=开关切换（按一次开/关），false=按住键才飞", "true=toggle mode, false=hold mode")
            .define("flight.toggleMode", true);

    // ============================================================
    // 构建 SPEC
    // ============================================================
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** ModConfig 引用，用于保存配置 */
    private static ModConfig modConfig;

    /**
     * 保存配置到文件（供 Cloth Config UI 在保存时调用）
     *
     * 注意：Cloth Config 的 setSaveConsumer 只修改了本类的静态字段，
     * 没有更新 ForgeConfigSpec 的 ConfigValue 内部值。
     * save() 必须先将静态字段同步回 ConfigValue，再写入文件。
     */
    public static void save() {
        if (modConfig == null) return;

        // ===== 将静态字段同步回 ConfigValue =====
        // 全局
        GLOBAL_ENABLED.set(globalEnabled);

        // 功能1
        AIM_ENABLED.set(aimEnabled);
        AIM_LOCK_RADIUS.set(aimLockRadius);
        AIM_CONE_ANGLE.set(aimConeAngle);
        AIM_PREDICTION_FACTOR.set(aimPredictionFactor);
        AIM_PASS_THROUGH_WALLS.set(aimPassThroughWalls);
        AIM_REQUIRE_GUN_EQUIPPED.set(aimRequireGunEquipped);
        AIM_BULLET_SPEED.set(aimBulletSpeed);
        AIM_RECOIL_COMPENSATION.set(aimRecoilCompensation);
        AIM_SINGLE_PLAYER_BULLET_PENETRATION.set(aimSinglePlayerBulletPenetration);
        AIM_SINGLE_PLAYER_HOMING_BULLET.set(aimSinglePlayerHomingBullet);

        // 功能2
        FAKEROT_ENABLED.set(fakerotEnabled);
        FAKEROT_PITCH_ANGLE.set(fakerotPitchAngle);
        FAKEROT_ROTATION_SPEED.set(fakerotRotationSpeed);
        FAKEROT_ACTIVE_REFRESH_INTERVAL.set(fakerotActiveRefreshInterval);
        FAKEROT_REQUIRE_GUN_EQUIPPED.set(fakerotRequireGunEquipped);

        // 功能3
        AIMBOT_ENABLED.set(aimbotEnabled);
        AIMBOT_RANGE.set(aimbotRange);
        AIMBOT_SMOOTHNESS.set(aimbotSmoothness);
        AIMBOT_PASS_THROUGH_WALLS.set(aimbotPassThroughWalls);
        AIMBOT_AIM_POSITION.set(aimbotAimPosition);

        // 功能4
        XRAY_ENABLED.set(xrayEnabled);
        XRAY_TARGET_BLOCKS.set(xrayTargetBlocks);
        XRAY_HIGHLIGHT_TARGETS.set(xrayHighlightTargets);
        XRAY_ENTITY_ESP.set(xrayEntityESP);

        // 功能6
        FULLBRIGHT_ENABLED.set(fullbrightEnabled);
        FULLBRIGHT_GAMMA.set(fullbrightGamma);

        // 功能5
        FLIGHT_ENABLED.set(flightEnabled);
        FLIGHT_HORIZONTAL_SPEED.set(flightHorizontalSpeed);
        FLIGHT_VERTICAL_SPEED.set(flightVerticalSpeed);
        FLIGHT_TOGGLE_MODE.set(flightToggleMode);

        // 写入文件
        modConfig.save();
    }

    // ============================================================
    // 运行时缓存字段（从 config 读取后缓存, 避免每 tick 访问 get()）
    // ============================================================
    // 全局
    public static boolean globalEnabled;

    // 功能1
    public static boolean aimEnabled;
    public static double aimLockRadius;
    public static double aimConeAngle;
    public static double aimPredictionFactor;
    public static boolean aimPassThroughWalls;
    public static boolean aimRequireGunEquipped;
    public static double aimBulletSpeed;public static double aimRecoilCompensation;

    // 单机/局域网专属
    public static boolean aimSinglePlayerBulletPenetration;
    public static boolean aimSinglePlayerHomingBullet;

    // 功能2
    public static boolean fakerotEnabled;
    public static double fakerotPitchAngle;
    public static double fakerotRotationSpeed;
    public static int fakerotActiveRefreshInterval;
    public static boolean fakerotRequireGunEquipped;

    // 功能3
    public static boolean aimbotEnabled;
    public static double aimbotRange;
    public static double aimbotSmoothness;
    public static boolean aimbotPassThroughWalls;
    public static AimPosition aimbotAimPosition;

    // 功能4
    public static boolean xrayEnabled;
    public static List<? extends String> xrayTargetBlocks;
    public static boolean xrayHighlightTargets;
    public static boolean xrayEntityESP;

    // 功能5
    public static boolean flightEnabled;
    public static double flightHorizontalSpeed;
    public static double flightVerticalSpeed;
    public static boolean flightToggleMode;

    // 功能6
    public static boolean fullbrightEnabled;
    public static double fullbrightGamma;

    /**
     * 配置变更时刷新缓存
     */
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        // 仅处理本 mod 的配置变更
        if (event.getConfig().getModId().equals("taczhacker")) {
            // 保存 ModConfig 引用，供 Cloth Config UI 保存时使用
            if (modConfig == null) {
                modConfig = event.getConfig();
            }
            // 全局
            globalEnabled = GLOBAL_ENABLED.get();

            // 功能1
            aimEnabled = AIM_ENABLED.get();
            aimLockRadius = AIM_LOCK_RADIUS.get();
            aimConeAngle = AIM_CONE_ANGLE.get();
            aimPredictionFactor = AIM_PREDICTION_FACTOR.get();
            aimPassThroughWalls = AIM_PASS_THROUGH_WALLS.get();
            aimRequireGunEquipped = AIM_REQUIRE_GUN_EQUIPPED.get();
            aimBulletSpeed = AIM_BULLET_SPEED.get();
            aimRecoilCompensation = AIM_RECOIL_COMPENSATION.get();
            aimSinglePlayerBulletPenetration = AIM_SINGLE_PLAYER_BULLET_PENETRATION.get();
            aimSinglePlayerHomingBullet = AIM_SINGLE_PLAYER_HOMING_BULLET.get();

            // 功能2
            fakerotEnabled = FAKEROT_ENABLED.get();
            fakerotPitchAngle = FAKEROT_PITCH_ANGLE.get();
            fakerotRotationSpeed = FAKEROT_ROTATION_SPEED.get();
            fakerotActiveRefreshInterval = FAKEROT_ACTIVE_REFRESH_INTERVAL.get();
            fakerotRequireGunEquipped = FAKEROT_REQUIRE_GUN_EQUIPPED.get();

            // 功能3
            aimbotEnabled = AIMBOT_ENABLED.get();
            aimbotRange = AIMBOT_RANGE.get();
            aimbotSmoothness = AIMBOT_SMOOTHNESS.get();
            aimbotPassThroughWalls = AIMBOT_PASS_THROUGH_WALLS.get();
            aimbotAimPosition = AIMBOT_AIM_POSITION.get();

            // 功能4
            xrayEnabled = XRAY_ENABLED.get();
            xrayTargetBlocks = XRAY_TARGET_BLOCKS.get();
            xrayHighlightTargets = XRAY_HIGHLIGHT_TARGETS.get();
            xrayEntityESP = XRAY_ENTITY_ESP.get();

            // 功能5
            flightEnabled = FLIGHT_ENABLED.get();
            flightHorizontalSpeed = FLIGHT_HORIZONTAL_SPEED.get();
            flightVerticalSpeed = FLIGHT_VERTICAL_SPEED.get();
            flightToggleMode = FLIGHT_TOGGLE_MODE.get();

            // 功能6
            fullbrightEnabled = FULLBRIGHT_ENABLED.get();
            fullbrightGamma = FULLBRIGHT_GAMMA.get();

            LOGGER.info("TaczHacker 配置已刷新");
        }
    }

    // ============================================================
    // 枚举类型
    // ============================================================
    public enum AimPosition {
        HEAD,
        BODY
    }
}