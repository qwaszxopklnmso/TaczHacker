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
        aimCat.addEntry(e.startBooleanToggle(
                Component.literal("静默开镜"),
                HackConfig.aimSilentScope
        ).setTooltip(Component.literal("开火时偷偷让服务端以为你在瞄准，散布按瞄准档算。\n"
                + "只发 Tacz 的 aim 包，本地不进入瞄准状态 ——\n"
                + "自己画面不会开镜、准星不缩放、FOV 不变，但别人看得到你在瞄准。\n"
                + "停火 1 秒后自动关镜；连发期间一直保持。\n"
                + "开了穿墙子弹或追踪弹时不生效。"))
                .setSaveConsumer(v -> HackConfig.aimSilentScope = v)
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
        aimCat.addEntry(e.startBooleanToggle(
                Component.literal("显示 FOV 圈"),
                HackConfig.aimFovCircle
        ).setTooltip(Component.literal("把上面的追踪锥角画成屏幕上的圈。\n"
                + "半径 = (屏幕高/2) × tan(锥角) ÷ tan(垂直FOV/2)。\n"
                + "默认 45°、FOV 70° 时半径约 0.71 倍屏幕高，\n"
                + "比上下边缘远、比左右边缘近，能看到左右两段弧。"))
                .setSaveConsumer(v -> HackConfig.aimFovCircle = v)
                .build());
        aimCat.addEntry(e.startColorField(
                Component.literal("FOV 圈颜色"),
                HackConfig.aimFovCircleColor
        ).setTooltip(Component.literal("六位 #RRGGBB。"))
                .setAlphaMode(false)
                .setDefaultValue(0x00FF00)
                .setSaveConsumer(v -> HackConfig.aimFovCircleColor = v & 0xFFFFFF)
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
        aimbotCat.addEntry(e.startDoubleField(
                Component.literal("搜索视场角（度）"),
                HackConfig.aimbotFov
        ).setTooltip(Component.literal("只锁定与准星夹角小于该值的目标，防止视角被甩到身后。90=正前方半球，180=全向"))
                .setMin(5.0).setMax(180.0)
                .setSaveConsumer(v -> HackConfig.aimbotFov = v)
                .build());
        aimbotCat.addEntry(e.startBooleanToggle(
                Component.literal("显示 FOV 圈"),
                HackConfig.aimbotFovCircle
        ).setTooltip(Component.literal("把搜索视场角画成屏幕上的一个圈。\n"
                + "半径 = (屏幕高/2) × tan(fov) ÷ tan(垂直FOV/2)。\n"
                + "默认 75° 比屏幕范围还大，圈会落在屏幕外看不见，\n"
                + "想看到圈就把上面的视场角调到 40 以下。"))
                .setSaveConsumer(v -> HackConfig.aimbotFovCircle = v)
                .build());
        aimbotCat.addEntry(e.startColorField(
                Component.literal("FOV 圈颜色"),
                HackConfig.aimbotFovCircleColor
        ).setTooltip(Component.literal("六位 #RRGGBB。"))
                .setAlphaMode(false)
                .setDefaultValue(0xFFFFFF)
                .setSaveConsumer(v -> HackConfig.aimbotFovCircleColor = v & 0xFFFFFF)
                .build());

        // ============================================================
        // 目标过滤（功能1 / 功能3 共用）
        // ============================================================
        ConfigCategory targetCat = builder.getOrCreateCategory(Component.literal("目标过滤"));
        targetCat.addEntry(e.startEnumSelector(
                Component.literal("目标类型"),
                HackConfig.TargetMode.class,
                HackConfig.targetMode
        ).setTooltip(Component.literal("ALL=所有生物，PLAYERS_ONLY=只打玩家，HOSTILE_ONLY=只打敌对生物"))
                .setSaveConsumer(v -> HackConfig.targetMode = v).build());
        targetCat.addEntry(e.startBooleanToggle(
                Component.literal("忽略已驯服生物"),
                HackConfig.targetIgnoreTamed
        ).setTooltip(Component.literal("不打狗、猫、马等已驯服的宠物"))
                .setSaveConsumer(v -> HackConfig.targetIgnoreTamed = v).build());
        targetCat.addEntry(e.startBooleanToggle(
                Component.literal("忽略同队玩家"),
                HackConfig.targetIgnoreTeammates
        ).setSaveConsumer(v -> HackConfig.targetIgnoreTeammates = v).build());
        targetCat.addEntry(e.startBooleanToggle(
                Component.literal("忽略盔甲架"),
                HackConfig.targetIgnoreArmorStands
        ).setSaveConsumer(v -> HackConfig.targetIgnoreArmorStands = v).build());

        // ============================================================
        // 功能4：透视
        // ============================================================
        ConfigCategory xrayCat = builder.getOrCreateCategory(Component.literal("功能4：透视 X-ray"));
        xrayCat.addEntry(e.startBooleanToggle(
                Component.literal("透视开关"),
                HackConfig.xrayEnabled
        ).setSaveConsumer(v -> HackConfig.xrayEnabled = v).build());

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

        // ============================================================
        // 功能7：ParCool 长滑铲
        // ============================================================
        ConfigCategory parcoolCat = builder.getOrCreateCategory(Component.literal("功能7：ParCool 长滑铲"));
        parcoolCat.addEntry(e.startBooleanToggle(
                Component.literal("长滑铲开关"),
                HackConfig.parcoolLongSlideEnabled
        ).setTooltip(Component.literal("滑铲开始后不会自动结束（一直保持滑铲状态，速度比走路快）。\n"
                + "起滑：ParCool 原生操作（跑动中按爬行键，默认 C）。\n"
                + "退出：按跳跃键 / 松开后再按一次滑铲键 / 按「取消长滑铲」键（默认 Z）。\n"
                + "需要客户端安装 ParCool；联机时服务端也要装 ParCool 和本 mod。"))
                .setSaveConsumer(v -> HackConfig.parcoolLongSlideEnabled = v)
                .build());
        parcoolCat.addEntry(e.startBooleanToggle(
                Component.literal("按跳跃键取消滑铲"),
                HackConfig.parcoolCancelByJump
        ).setTooltip(Component.literal("开启后滑铲期间按跳跃键（空格）会结束滑铲。\n"
                + "这是 ParCool 原版退出滑铲的方式；关闭后滑铲期间跳跃键仍然被 ParCool 屏蔽。"))
                .setSaveConsumer(v -> HackConfig.parcoolCancelByJump = v)
                .build());
        parcoolCat.addEntry(e.startBooleanToggle(
                Component.literal("滑铲方向跟随视角"),
                HackConfig.parcoolSteerableSlide
        ).setTooltip(Component.literal("ParCool 原版滑铲方向在起滑瞬间就固定了（转视角不会转向）。\n"
                + "开启后滑铲期间转动视角即可改变滑行方向。"))
                .setSaveConsumer(v -> HackConfig.parcoolSteerableSlide = v)
                .build());
        parcoolCat.addEntry(e.startBooleanToggle(
                Component.literal("不消耗体力"),
                HackConfig.parcoolInfiniteStamina
        ).setTooltip(Component.literal("跑酷动作不再扣 ParCool 体力（体力条不动、也不会力竭）。"))
                .setSaveConsumer(v -> HackConfig.parcoolInfiniteStamina = v)
                .build());

        // ============================================================
        // 功能8：玩家 ESP（准心连线）
        // ============================================================
        ConfigCategory espCat = builder.getOrCreateCategory(Component.literal("功能8：玩家 ESP"));
        espCat.addEntry(e.startBooleanToggle(
                Component.literal("ESP 开关"),
                HackConfig.espEnabled
        ).setTooltip(Component.literal("开启后按 ESP 键（默认 J）切换显示。\n"
                + "会从屏幕准心向每个目标头顶的屏幕位置画一条线。\n"
                + "纯客户端渲染，不发包、不改游戏状态，服务端装不装本 mod 都能用。"))
                .setSaveConsumer(v -> HackConfig.espEnabled = v)
                .build());
        espCat.addEntry(e.startDoubleField(
                Component.literal("最远显示距离（格）"),
                HackConfig.espMaxDistance
        ).setTooltip(Component.literal("超过这个距离的目标不画线。"))
                .setMin(8.0).setMax(512.0)
                .setSaveConsumer(v -> HackConfig.espMaxDistance = v)
                .build());
        espCat.addEntry(e.startColorField(
                Component.literal("线条颜色"),
                HackConfig.espColor
        ).setAlphaMode(false)  // 只要 #RRGGBB 六位，不要 alpha
                .setTooltip(Component.literal("填 #RRGGBB 六位十六进制。\n"
                        + "线条始终不透明，不需要 alpha 通道。"))
                .setDefaultValue(0x0000FF00)
                .setSaveConsumer(v -> HackConfig.espColor = v & 0xFFFFFF)
                .build());
        espCat.addEntry(e.startDoubleField(
                Component.literal("线条粗细（像素）"),
                HackConfig.espLineWidth
        ).setTooltip(Component.literal("1 最细，建议 1~3。\n"
                + "小于 1 的数值画不出真的亚像素线（GUI 矩形最小就是 1 像素），\n"
                + "会改用「1 像素 + 按比例降低不透明度」来模拟，看着比实心 1 像素轻。"))
                .setMin(0.1).setMax(6.0)
                .setSaveConsumer(v -> HackConfig.espLineWidth = v)
                .build());
        espCat.addEntry(e.startBooleanToggle(
                Component.literal("也给生物画"),
                HackConfig.espIncludeMobs
        ).setTooltip(Component.literal("默认只画玩家；开启后僵尸、动物这些也会画。"))
                .setSaveConsumer(v -> HackConfig.espIncludeMobs = v)
                .build());
        espCat.addEntry(e.startBooleanToggle(
                Component.literal("准心连线"),
                HackConfig.espDrawLine
        ).setTooltip(Component.literal("从屏幕中心的准心连到目标头顶。"))
                .setSaveConsumer(v -> HackConfig.espDrawLine = v)
                .build());
        espCat.addEntry(e.startBooleanToggle(
                Component.literal("方框"),
                HackConfig.espDrawBox
        ).setTooltip(Component.literal("在目标周围画一个 2D 包围盒。"))
                .setSaveConsumer(v -> HackConfig.espDrawBox = v)
                .build());
        espCat.addEntry(e.startBooleanToggle(
                Component.literal("骨骼"),
                HackConfig.espDrawSkeleton
        ).setTooltip(Component.literal("头 / 脖子 / 胯 / 四肢的连线，会跟着目标转身。"))
                .setSaveConsumer(v -> HackConfig.espDrawSkeleton = v)
                .build());
        // ============================================================
        // 功能9：实体信息牌（NameTags）
        // ============================================================
        ConfigCategory tagsCat = builder.getOrCreateCategory(Component.literal("功能9：实体信息牌"));
        tagsCat.addEntry(e.startBooleanToggle(
                Component.literal("信息牌开关"),
                HackConfig.nameTagsEnabled
        ).setTooltip(Component.literal("开启后按 K（可改键）切换显示。\n"
                + "在目标头顶显示名字 / 血量 / 距离 / 血条，纯客户端渲染、不发包。"))
                .setSaveConsumer(v -> HackConfig.nameTagsEnabled = v)
                .build());
        tagsCat.addEntry(e.startDoubleField(
                Component.literal("最远显示距离（格）"),
                HackConfig.nameTagsMaxDistance
        ).setTooltip(Component.literal("超过这个距离的目标不显示信息牌。"))
                .setMin(4.0).setMax(512.0)
                .setSaveConsumer(v -> HackConfig.nameTagsMaxDistance = v)
                .build());
        tagsCat.addEntry(e.startBooleanToggle(
                Component.literal("也给生物显示"),
                HackConfig.nameTagsIncludeMobs
        ).setTooltip(Component.literal("默认玩家和生物都显示；关掉就只看玩家。"))
                .setSaveConsumer(v -> HackConfig.nameTagsIncludeMobs = v)
                .build());
        tagsCat.addEntry(e.startBooleanToggle(
                Component.literal("显示名字"),
                HackConfig.nameTagsShowName
        ).setTooltip(Component.literal("玩家显示 ID，生物显示种类名（如「僵尸」）。"))
                .setSaveConsumer(v -> HackConfig.nameTagsShowName = v)
                .build());
        tagsCat.addEntry(e.startBooleanToggle(
                Component.literal("显示血量"),
                HackConfig.nameTagsShowHealth
        ).setTooltip(Component.literal("读不到血量（服务器不同步）时显示 ? 而不是 0。"))
                .setSaveConsumer(v -> HackConfig.nameTagsShowHealth = v)
                .build());
        tagsCat.addEntry(e.startBooleanToggle(
                Component.literal("血量取整"),
                HackConfig.nameTagsHealthRounded
        ).setTooltip(Component.literal("开启显示 18/20，关掉显示 18.5/20.0。"))
                .setSaveConsumer(v -> HackConfig.nameTagsHealthRounded = v)
                .build());
        tagsCat.addEntry(e.startBooleanToggle(
                Component.literal("血量计入吸收盾"),
                HackConfig.nameTagsHealthAbsorption
        ).setTooltip(Component.literal("把金苹果那层黄心也算进血量。"))
                .setSaveConsumer(v -> HackConfig.nameTagsHealthAbsorption = v)
                .build());
        tagsCat.addEntry(e.startBooleanToggle(
                Component.literal("显示距离"),
                HackConfig.nameTagsShowDistance
        ).setTooltip(Component.literal("在牌子最下面显示与目标的距离（米）。"))
                .setSaveConsumer(v -> HackConfig.nameTagsShowDistance = v)
                .build());
        tagsCat.addEntry(e.startBooleanToggle(
                Component.literal("显示血条"),
                HackConfig.nameTagsShowBar
        ).setTooltip(Component.literal("血量下面画一条按比例填充的横条，颜色同样随血量变。"))
                .setSaveConsumer(v -> HackConfig.nameTagsShowBar = v)
                .build());
        tagsCat.addEntry(e.startIntField(
                Component.literal("血条宽度（像素）"),
                HackConfig.nameTagsBarWidth
        ).setTooltip(Component.literal("10 ~ 200。"))
                .setMin(10).setMax(200)
                .setSaveConsumer(v -> HackConfig.nameTagsBarWidth = v)
                .build());
        tagsCat.addEntry(e.startDoubleField(
                Component.literal("整体缩放"),
                HackConfig.nameTagsScale
        ).setTooltip(Component.literal("1.0 = 原始大小。"))
                .setMin(0.5).setMax(3.0)
                .setSaveConsumer(v -> HackConfig.nameTagsScale = v)
                .build());
        tagsCat.addEntry(e.startBooleanToggle(
                Component.literal("文字加背景"),
                HackConfig.nameTagsBackground
        ).setTooltip(Component.literal("半透明黑底，远处或者亮背景下也看得清。"))
                .setSaveConsumer(v -> HackConfig.nameTagsBackground = v)
                .build());

        // 附加功能
        ConfigCategory miscCat = builder.getOrCreateCategory(Component.literal("附加功能"));
        miscCat.addEntry(e.startBooleanToggle(
                Component.literal("无后坐力（Tacz）"),
                HackConfig.taczNoRecoil
        ).setTooltip(Component.literal("去掉开火时画面的上跳。\n"
                + "Tacz 的后坐力是纯渲染的摄像机偏移，**不影响弹道**，\n"
                + "所以这个开关只是让枪不抖，不是让子弹变准。\n"
                + "纯客户端，服务端察觉不到。\n"
                + "（无扩散做不到：散布是服务端生成子弹时算的）"))
                .setSaveConsumer(v -> HackConfig.taczNoRecoil = v)
                .build());

        // 保存回调：用户点击"保存并退出"时持久化配置
        builder.setSavingRunnable(() -> {
            Taczhacker.LOGGER.debug("TaczHacker 配置已保存");
            HackConfig.save();
        });

        return builder.build();
    }
}