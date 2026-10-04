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

    private static final ForgeConfigSpec.DoubleValue AIMBOT_FOV = BUILDER
            .comment("搜索视场角（度）：只锁定与准星夹角小于该值的目标，防止视角被甩到身后的目标上。\n"
                    + "90 = 正前方半球，180 = 全向（会锁身后的目标）",
                    "Target FOV in degrees. Only targets within this angle of the crosshair are locked. "
                    + "90 = front hemisphere, 180 = any direction.")
            .defineInRange("aimbot.fov", 75.0, 5.0, 180.0);

    private static final ForgeConfigSpec.BooleanValue AIMBOT_FOV_CIRCLE = BUILDER
            .comment("在屏幕上画一个圈，标出自瞄的搜索范围（FOV 圈）。\n"
                    + "半径 = (屏幕高/2) * tan(fov) / tan(垂直FOV/2)，\n"
                    + "所以 fov 比屏幕范围还大时（默认 75° 就是）圈会落在屏幕外看不见，\n"
                    + "调到 40° 以下才有明显的圈。",
                    "Draw a circle showing the aimbot search FOV. "
                    + "Large FOV values put the circle off-screen.")
            .define("aimbot.fovCircle", true);

    private static final ForgeConfigSpec.IntValue AIMBOT_FOV_CIRCLE_COLOR = BUILDER
            .comment("FOV 圈颜色，填 #RRGGBB 六位十六进制对应的十进制值（0 ~ 0xFFFFFF）。\n"
                    + "不要带 alpha 字节：Cloth Config 的颜色控件在无 alpha 模式下要求恰好 6 位 hex，\n"
                    + "否则会报「不允许Alpha值！」。",
                    "FOV circle color: decimal value of a #RRGGBB hex. Do NOT include an alpha byte.")
            .defineInRange("aimbot.fovCircleColor", 0xFFFFFF, Integer.MIN_VALUE, Integer.MAX_VALUE);

    // ============================================================
    // 目标过滤（功能1 静默自瞄 / 功能3 视角锁定 共用）
    // ============================================================
    private static final ForgeConfigSpec.EnumValue<TargetMode> TARGET_MODE = BUILDER
            .comment("目标类型：ALL（所有生物）| PLAYERS_ONLY（只打玩家）| HOSTILE_ONLY（只打敌对生物）",
                    "Target type: ALL | PLAYERS_ONLY | HOSTILE_ONLY")
            .defineEnum("targeting.mode", TargetMode.ALL);

    private static final ForgeConfigSpec.BooleanValue TARGET_IGNORE_TAMED = BUILDER
            .comment("忽略已驯服的生物（狗、猫、马等）", "Ignore tamed animals")
            .define("targeting.ignoreTamed", true);

    private static final ForgeConfigSpec.BooleanValue TARGET_IGNORE_TEAMMATES = BUILDER
            .comment("忽略同队伍/盟友玩家", "Ignore players on the same team")
            .define("targeting.ignoreTeammates", true);

    private static final ForgeConfigSpec.BooleanValue TARGET_IGNORE_ARMOR_STANDS = BUILDER
            .comment("忽略盔甲架", "Ignore armor stands")
            .define("targeting.ignoreArmorStands", true);

    // ============================================================
    // 功能4：透视（X-ray）
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue XRAY_ENABLED = BUILDER
            .comment("功能4：透视总开关", "X-ray master switch")
            .define("xray.enabled", true);

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
    // 功能7：ParCool 长滑铲
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue PARCOOL_LONG_SLIDE_ENABLED = BUILDER
            .comment("功能7：ParCool 长滑铲。开启后滑铲不会自动结束（一直是滑铲状态，比走路快）。\n"
                    + "退出滑铲的方式：按跳跃键、或松开后再按一次滑铲键（ParCool 的爬行键，默认 C）、"
                    + "或按本 mod 的「取消长滑铲」键（默认 Z）。\n"
                    + "需要玩家同时安装 ParCool；若要联机使用，服务端也要装 ParCool 和本 mod。",
                    "ParCool long slide. While enabled, a slide never ends on its own. "
                    + "Stop it with the jump key, by re-pressing the crawl/slide key, or with the cancel key (default Z). "
                    + "Requires ParCool, on both sides for multiplayer.")
            .define("parcool.longSlide", true);

    private static final ForgeConfigSpec.BooleanValue PARCOOL_CANCEL_BY_JUMP = BUILDER
            .comment("功能7：按跳跃键取消滑铲（ParCool 原版就是这么退出的）。\n"
                    + "关闭后滑铲期间跳跃键依然被 ParCool 屏蔽（原版行为），只能用滑铲键/取消键退出。",
                    "Cancel the long slide by pressing jump. If disabled, jump stays blocked during the slide.")
            .define("parcool.cancelByJump", true);

    private static final ForgeConfigSpec.BooleanValue PARCOOL_STEERABLE_SLIDE = BUILDER
            .comment("功能7：滑铲方向跟随视角。ParCool 原版的滑铲方向在起滑瞬间就固定了，\n"
                    + "开启后滑铲期间转动视角即可改变滑行方向（跑动中会按你当前视角转向）。",
                    "Steerable slide: the slide direction follows your view instead of being fixed at start.")
            .define("parcool.steerableSlide", true);

    private static final ForgeConfigSpec.BooleanValue PARCOOL_INFINITE_STAMINA = BUILDER
            .comment("功能7附属：不消耗 ParCool 体力。开启后跑酷动作不再扣体力（体力条不动、也不会力竭）。\n"
                    + "需要玩家同时安装 ParCool。",
                    "ParCool infinite stamina. While enabled, parkour actions never consume stamina. "
                    + "Requires ParCool.")
            .define("parcool.infiniteStamina", true);

    // ============================================================
    // 功能8：玩家 ESP（准心连线）
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue ESP_ENABLED = BUILDER
            .comment("功能8：玩家 ESP 总开关。开启后按 ESP 键（默认 J）切换显示，\n"
                    + "会从屏幕准心向每个目标头顶的屏幕位置画一条线。\n"
                    + "纯客户端渲染，不发包、不改游戏状态，服务端装不装本 mod 都能用。",
                    "Player ESP master switch. Lines are drawn from the crosshair to each target's head.")
            .define("esp.enabled", true);

    private static final ForgeConfigSpec.DoubleValue ESP_MAX_DISTANCE = BUILDER
            .comment("ESP 最远显示距离（格）", "ESP max render distance in blocks")
            .defineInRange("esp.maxDistance", 128.0, 8.0, 512.0);

    private static final ForgeConfigSpec.IntValue ESP_COLOR = BUILDER
            .comment("ESP 线条颜色，填 #RRGGBB 六位十六进制对应的十进制值（0 ~ 0xFFFFFF）。\n"
                    + "**不要带 alpha 字节**：Cloth Config 的颜色控件在无 alpha 模式下\n"
                    + "要求恰好 6 位 hex，值里出现 alpha 字节它会显示成 8 位并报「不允许Alpha值！」。\n"
                    + "线条永远不透明，alpha 在渲染时补。\n"
                    + "范围放宽到 int 全域只是为了兼容老版本留下的带 alpha 的旧值，\n"
                    + "读取时会自动把高字节清掉。",
                    "ESP line color: decimal value of a #RRGGBB hex (0 ~ 0xFFFFFF). "
                    + "Do NOT include an alpha byte, or the color widget will reject it. "
                    + "Lines are always opaque; alpha is added at render time. "
                    + "The wide int range only exists so old values with an alpha byte still load; "
                    + "the high byte is stripped on read.")
            .defineInRange("esp.color", 0x0000FF00, Integer.MIN_VALUE, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue ESP_LINE_WIDTH = BUILDER
            .comment("ESP 线条粗细（像素）", "ESP line width in pixels")
            .defineInRange("esp.lineWidth", 1.0, 0.1, 6.0);

    private static final ForgeConfigSpec.BooleanValue ESP_INCLUDE_MOBS = BUILDER
            .comment("是否也给非玩家生物画（默认只画玩家）",
                    "Also draw for non-player living entities")
            .define("esp.includeMobs", false);

    private static final ForgeConfigSpec.BooleanValue ESP_DRAW_LINE = BUILDER
            .comment("从屏幕准心向目标头顶画线", "Draw a line from the crosshair to the target")
            .define("esp.drawLine", true);

    private static final ForgeConfigSpec.BooleanValue ESP_DRAW_BOX = BUILDER
            .comment("画 2D 包围盒（方框）", "Draw a 2D bounding box")
            .define("esp.drawBox", true);

    private static final ForgeConfigSpec.BooleanValue ESP_DRAW_SKELETON = BUILDER
            .comment("画骨骼（头 / 脖子 / 胯 / 四肢的连线）",
                    "Draw a stick-figure skeleton")
            .define("esp.drawSkeleton", true);

    // ============================================================
    // 功能9：实体信息牌（NameTags）
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue NAMETAGS_ENABLED = BUILDER
            .comment("功能9：实体信息牌总开关。在目标头顶显示名字 / 血量 / 距离 / 血条。\n"
                    + "按键切换（默认 K），纯客户端渲染，不发包。",
                    "NameTags master switch. Shows name / health / distance / bar above entities. "
                    + "Toggle with the bound key (default K). Pure client side.")
            .define("nametags.enabled", true);

    private static final ForgeConfigSpec.DoubleValue NAMETAGS_MAX_DISTANCE = BUILDER
            .comment("最远显示距离（格）", "Max render distance in blocks")
            .defineInRange("nametags.maxDistance", 64.0, 4.0, 512.0);

    private static final ForgeConfigSpec.BooleanValue NAMETAGS_INCLUDE_MOBS = BUILDER
            .comment("是否也给非玩家生物显示（默认开；关掉就只看玩家）",
                    "Also show for non-player living entities")
            .define("nametags.includeMobs", true);

    private static final ForgeConfigSpec.BooleanValue NAMETAGS_SHOW_NAME = BUILDER
            .comment("显示名字（玩家是 ID，生物是种类名）", "Show the entity name")
            .define("nametags.showName", true);

    private static final ForgeConfigSpec.BooleanValue NAMETAGS_SHOW_HEALTH = BUILDER
            .comment("显示血量。读不到血量（服务器不同步）时显示 ? 而不是 0",
                    "Show health. Shows ? instead of 0 when the server does not sync it.")
            .define("nametags.showHealth", true);

    private static final ForgeConfigSpec.BooleanValue NAMETAGS_HEALTH_ROUNDED = BUILDER
            .comment("血量取整（关掉则保留一位小数）", "Round health to whole numbers")
            .define("nametags.healthRounded", true);

    private static final ForgeConfigSpec.BooleanValue NAMETAGS_HEALTH_ABSORPTION = BUILDER
            .comment("血量计入吸收盾（金苹果那层黄心）", "Include absorption hearts in the value")
            .define("nametags.healthAbsorption", false);

    private static final ForgeConfigSpec.BooleanValue NAMETAGS_SHOW_DISTANCE = BUILDER
            .comment("显示与目标的距离（米）", "Show distance to the target")
            .define("nametags.showDistance", false);

    private static final ForgeConfigSpec.BooleanValue NAMETAGS_SHOW_BAR = BUILDER
            .comment("显示血条（标签下方一条按血量比例填充的横条）",
                    "Draw a health bar under the tag")
            .define("nametags.showBar", true);

    private static final ForgeConfigSpec.IntValue NAMETAGS_BAR_WIDTH = BUILDER
            .comment("血条宽度（像素）", "Health bar width in pixels")
            .defineInRange("nametags.barWidth", 40, 10, 200);

    private static final ForgeConfigSpec.DoubleValue NAMETAGS_SCALE = BUILDER
            .comment("整体缩放（1.0 = 原始大小）", "Overall scale (1.0 = default)")
            .defineInRange("nametags.scale", 1.0, 0.5, 3.0);

    private static final ForgeConfigSpec.BooleanValue NAMETAGS_BACKGROUND = BUILDER
            .comment("文字加半透明黑底，远处也看得清", "Draw a translucent background behind the text")
            .define("nametags.background", true);

    // ============================================================
    // 附加功能：Tacz 无后坐力
    // ============================================================
    private static final ForgeConfigSpec.BooleanValue TACZ_NO_RECOIL = BUILDER
            .comment("去掉 Tacz 开火时的后坐力（画面不跳）。\n"
                    + "Tacz 的后坐力是纯渲染的摄像机偏移（ViewportEvent.ComputeCameraAngles），\n"
                    + "**不影响弹道**：子弹方向一直是用玩家的实际旋转算的。\n"
                    + "所以这个开关的效果是「枪不再抖」，不是「子弹变准」，而且纯客户端，服务端察觉不到。",
                    "Remove Tacz's visual camera recoil. It is a render-only camera offset and does not "
                    + "affect bullet direction. Client side only.")
            .define("tacz.noRecoil", true);

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
        AIMBOT_FOV.set(aimbotFov);
        AIMBOT_FOV_CIRCLE.set(aimbotFovCircle);
        AIMBOT_FOV_CIRCLE_COLOR.set(aimbotFovCircleColor & 0xFFFFFF);

        // 目标过滤
        TARGET_MODE.set(targetMode);
        TARGET_IGNORE_TAMED.set(targetIgnoreTamed);
        TARGET_IGNORE_TEAMMATES.set(targetIgnoreTeammates);
        TARGET_IGNORE_ARMOR_STANDS.set(targetIgnoreArmorStands);

        // 功能4
        XRAY_ENABLED.set(xrayEnabled);

        // 功能5
        FLIGHT_ENABLED.set(flightEnabled);
        FLIGHT_HORIZONTAL_SPEED.set(flightHorizontalSpeed);
        FLIGHT_VERTICAL_SPEED.set(flightVerticalSpeed);
        FLIGHT_TOGGLE_MODE.set(flightToggleMode);

        // 功能6
        FULLBRIGHT_ENABLED.set(fullbrightEnabled);
        FULLBRIGHT_GAMMA.set(fullbrightGamma);

        // 功能7
        PARCOOL_LONG_SLIDE_ENABLED.set(parcoolLongSlideEnabled);
        PARCOOL_INFINITE_STAMINA.set(parcoolInfiniteStamina);
        PARCOOL_CANCEL_BY_JUMP.set(parcoolCancelByJump);
        PARCOOL_STEERABLE_SLIDE.set(parcoolSteerableSlide);

        // 功能8
        ESP_ENABLED.set(espEnabled);
        ESP_MAX_DISTANCE.set(espMaxDistance);
        ESP_COLOR.set(espColor);
        ESP_LINE_WIDTH.set(espLineWidth);
        ESP_INCLUDE_MOBS.set(espIncludeMobs);
        ESP_DRAW_LINE.set(espDrawLine);
        ESP_DRAW_BOX.set(espDrawBox);
        ESP_DRAW_SKELETON.set(espDrawSkeleton);

        // 功能9
        NAMETAGS_ENABLED.set(nameTagsEnabled);
        NAMETAGS_MAX_DISTANCE.set(nameTagsMaxDistance);
        NAMETAGS_INCLUDE_MOBS.set(nameTagsIncludeMobs);
        NAMETAGS_SHOW_NAME.set(nameTagsShowName);
        NAMETAGS_SHOW_HEALTH.set(nameTagsShowHealth);
        NAMETAGS_HEALTH_ROUNDED.set(nameTagsHealthRounded);
        NAMETAGS_HEALTH_ABSORPTION.set(nameTagsHealthAbsorption);
        NAMETAGS_SHOW_DISTANCE.set(nameTagsShowDistance);
        NAMETAGS_SHOW_BAR.set(nameTagsShowBar);
        NAMETAGS_BAR_WIDTH.set(nameTagsBarWidth);
        NAMETAGS_SCALE.set(nameTagsScale);
        NAMETAGS_BACKGROUND.set(nameTagsBackground);

        // 附加：Tacz 无后坐力
        TACZ_NO_RECOIL.set(taczNoRecoil);

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
    public static double aimBulletSpeed;
    public static double aimRecoilCompensation;

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
    public static double aimbotFov;
    public static boolean aimbotFovCircle;
    public static int aimbotFovCircleColor;

    // 目标过滤
    public static TargetMode targetMode;
    public static boolean targetIgnoreTamed;
    public static boolean targetIgnoreTeammates;
    public static boolean targetIgnoreArmorStands;

    // 功能4
    public static boolean xrayEnabled;

    // 功能5
    public static boolean flightEnabled;
    public static double flightHorizontalSpeed;
    public static double flightVerticalSpeed;
    public static boolean flightToggleMode;

    // 功能6
    public static boolean fullbrightEnabled;
    public static double fullbrightGamma;

    // 功能7：ParCool 长滑铲
    public static boolean parcoolLongSlideEnabled;
    public static boolean parcoolInfiniteStamina;
    public static boolean parcoolCancelByJump;
    public static boolean parcoolSteerableSlide;

    // 功能8：玩家 ESP
    public static boolean espEnabled;
    public static double espMaxDistance;
    public static int espColor;
    public static double espLineWidth;
    public static boolean espIncludeMobs;
    public static boolean espDrawLine;
    public static boolean espDrawBox;
    public static boolean espDrawSkeleton;

    // 功能9：实体信息牌
    public static boolean nameTagsEnabled;
    public static double nameTagsMaxDistance;
    public static boolean nameTagsIncludeMobs;
    public static boolean nameTagsShowName;
    public static boolean nameTagsShowHealth;
    public static boolean nameTagsHealthRounded;
    public static boolean nameTagsHealthAbsorption;
    public static boolean nameTagsShowDistance;
    public static boolean nameTagsShowBar;
    public static int nameTagsBarWidth;
    public static double nameTagsScale;
    public static boolean nameTagsBackground;

    // 附加：Tacz 无后坐力
    public static boolean taczNoRecoil;

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
            aimbotFov = AIMBOT_FOV.get();
            aimbotFovCircle = AIMBOT_FOV_CIRCLE.get();
            aimbotFovCircleColor = AIMBOT_FOV_CIRCLE_COLOR.get() & 0xFFFFFF;

            // 目标过滤
            targetMode = TARGET_MODE.get();
            targetIgnoreTamed = TARGET_IGNORE_TAMED.get();
            targetIgnoreTeammates = TARGET_IGNORE_TEAMMATES.get();
            targetIgnoreArmorStands = TARGET_IGNORE_ARMOR_STANDS.get();

            // 功能4
            xrayEnabled = XRAY_ENABLED.get();

            // 功能5
            flightEnabled = FLIGHT_ENABLED.get();
            flightHorizontalSpeed = FLIGHT_HORIZONTAL_SPEED.get();
            flightVerticalSpeed = FLIGHT_VERTICAL_SPEED.get();
            flightToggleMode = FLIGHT_TOGGLE_MODE.get();

            // 功能6
            fullbrightEnabled = FULLBRIGHT_ENABLED.get();
            fullbrightGamma = FULLBRIGHT_GAMMA.get();

            // 功能7
            parcoolLongSlideEnabled = PARCOOL_LONG_SLIDE_ENABLED.get();
            parcoolInfiniteStamina = PARCOOL_INFINITE_STAMINA.get();
            parcoolCancelByJump = PARCOOL_CANCEL_BY_JUMP.get();
            parcoolSteerableSlide = PARCOOL_STEERABLE_SLIDE.get();

            // 功能8
            espEnabled = ESP_ENABLED.get();
            espMaxDistance = ESP_MAX_DISTANCE.get();
            // 只保留低 24 位。颜色控件在无 alpha 模式下要求恰好 6 位 hex，
            // 值里留着 alpha 字节会显示成 8 位并报「不允许Alpha值！」。
            // 不透明在渲染时（PlayerEspHandler）补上。
            espColor = ESP_COLOR.get() & 0xFFFFFF;
            espLineWidth = ESP_LINE_WIDTH.get();
            espIncludeMobs = ESP_INCLUDE_MOBS.get();
            espDrawLine = ESP_DRAW_LINE.get();
            espDrawBox = ESP_DRAW_BOX.get();
            espDrawSkeleton = ESP_DRAW_SKELETON.get();

            // 功能9
            nameTagsEnabled = NAMETAGS_ENABLED.get();
            nameTagsMaxDistance = NAMETAGS_MAX_DISTANCE.get();
            nameTagsIncludeMobs = NAMETAGS_INCLUDE_MOBS.get();
            nameTagsShowName = NAMETAGS_SHOW_NAME.get();
            nameTagsShowHealth = NAMETAGS_SHOW_HEALTH.get();
            nameTagsHealthRounded = NAMETAGS_HEALTH_ROUNDED.get();
            nameTagsHealthAbsorption = NAMETAGS_HEALTH_ABSORPTION.get();
            nameTagsShowDistance = NAMETAGS_SHOW_DISTANCE.get();
            nameTagsShowBar = NAMETAGS_SHOW_BAR.get();
            nameTagsBarWidth = NAMETAGS_BAR_WIDTH.get();
            nameTagsScale = NAMETAGS_SCALE.get();
            nameTagsBackground = NAMETAGS_BACKGROUND.get();

            // 附加：Tacz 无后坐力
            taczNoRecoil = TACZ_NO_RECOIL.get();

            LOGGER.debug("TaczHacker 配置已刷新");
        }
    }

    // ============================================================
    // 枚举类型
    // ============================================================
    public enum AimPosition {
        HEAD,
        BODY
    }

    /** 目标类型过滤 */
    public enum TargetMode {
        /** 所有生物 */
        ALL,
        /** 只打玩家 */
        PLAYERS_ONLY,
        /** 只打敌对生物 */
        HOSTILE_ONLY
    }
}