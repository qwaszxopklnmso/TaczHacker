package com.qw.taczhacker.feature.aim;

import com.qw.taczhacker.config.HackConfig;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessagePlayerAim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * 功能1 附属：静默开镜（Silent Scope）
 *
 * Tacz 的散布按「姿态」取档：{@code InaccuracyType} 里有 STAND / AIM / SNEAK / LIE 等，
 * 瞄准档（AIM）明显更小。而服务端判断你在不在瞄准，靠的是客户端发的
 * {@link ClientMessagePlayerAim}（一个 boolean 包）。
 *
 * 所以这里**只发这个包，不动本地状态**：
 *   - 服务端（以及别人的客户端）认为你在瞄准 → 散布按 AIM 档算
 *   - 本地 {@code LocalPlayer} 的 isAiming 还是 false → 自己看不到开镜、准星不缩放、FOV 不变
 *
 * 什么时候开镜：开火的那一刻发 aim(true)（这一枪本身就吃到 AIM 档），
 * 停火 {@link #RELEASE_DELAY} tick 之后自动发 aim(false)。
 * 连发时不用重复发包，一直保持到松手为止 —— 每次交战的额外发包只有 2 个。
 *
 * ⚠️ 移速：服务端认为你在瞄准，就会按枪的 MoveSpeed 给你挂瞄准减速的 AttributeModifier，
 * 而且这个 modifier 会同步回客户端。所以这里额外做了一件事：
 * 静默开镜生效、且玩家本地**没有真的在瞄准**时，每 tick 把 Tacz 那两个 modifier 从
 * 客户端的 MOVEMENT_SPEED 上摘掉。玩家自己右键开镜（本地也在瞄准）时不动，不影响正常玩法。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public final class SilentScope {

    /** 停火多少 tick 之后关镜（20 tick = 1 秒） */
    private static final int RELEASE_DELAY = 20;

    /** 服务端当前是不是被我们「骗」成瞄准状态 */
    private static boolean scopeSent = false;

    /** 最后一次开火时的 tick 计数 */
    private static int lastShotTick = -100000;

    /** 累加 tick（用来自判断停火多久） */
    private static int tickCounter = 0;

    /** Tacz 那两个移速 modifier 的 UUID（反射取，取不到就跳过移速保护） */
    private static UUID extraSpeedUuid = null;
    private static UUID weightSpeedUuid = null;
    private static boolean uuidResolved = false;

    private SilentScope() {
    }

    /**
     * 开火时调用（在射击逻辑最前面，保证 aim 包比 shoot 包先到服务端）
     */
    public static void onShoot() {
        if (!isEnabled()) return;

        lastShotTick = tickCounter;
        if (scopeSent) return;

        // 玩家自己正在开镜时不用我们插手：Tacz 客户端本来就会把瞄准状态同步给服务端，
        // 我们再发一遍的话，收镜时反而会把他的开镜一起关掉
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && isLocallyAiming(mc.player)) return;

        scopeSent = true;
        sendAim(true);
    }

    /**
     * 静默开镜是否处于生效状态（配置 + 功能1 + 没开单机特供）
     */
    public static boolean isEnabled() {
        return HackConfig.globalEnabled
                && AimHandler.isActive()
                && HackConfig.aimSilentScope
                // 单机特供（追踪弹 / 穿墙子弹）需要服务端也装本 mod，
                // 那种情况下服务端自己会处理弹道，没必要再骗它开镜
                && !HackConfig.aimSinglePlayerBulletPenetration
                && !HackConfig.aimSinglePlayerHomingBullet;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) {
            // 离开世界：状态清零，但不用发包（服务端那边玩家实体也没了）
            scopeSent = false;
            tickCounter = 0;
            return;
        }

        tickCounter++;

        if (scopeSent && tickCounter - lastShotTick > RELEASE_DELAY) {
            scopeSent = false;
            // 这期间玩家自己按下了开镜键：服务端本来就该是瞄准状态，别给他关掉
            if (!isLocallyAiming(player)) {
                sendAim(false);
            }
        }

        if (scopeSent) {
            keepMoveSpeed(player);
        }
    }

    /**
     * 把 Tacz 挂上来的瞄准减速摘掉
     *
     * 只在「我们骗服务端开了镜」且「本地玩家并没有真的在瞄准」时动手：
     * 玩家自己按右键开镜时是正常玩法，不该改他的移速。
     */
    private static void keepMoveSpeed(LocalPlayer player) {
        if (isLocallyAiming(player)) return;

        resolveUuids();
        if (extraSpeedUuid == null) return;

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;

        // 服务端每次状态变化都会重新同步过来，所以这里每 tick 摘一次
        if (speed.getModifier(extraSpeedUuid) != null) {
            speed.removeModifier(extraSpeedUuid);
        }
        if (weightSpeedUuid != null && speed.getModifier(weightSpeedUuid) != null) {
            speed.removeModifier(weightSpeedUuid);
        }
    }

    private static boolean isLocallyAiming(LocalPlayer player) {
        try {
            return IGunOperator.fromLivingEntity(player).getSynIsAiming();
        } catch (Throwable t) {
            // Tacz 不在或者 API 变了，就当没在瞄准
            return false;
        }
    }

    private static void resolveUuids() {
        if (uuidResolved) return;
        uuidResolved = true;
        try {
            Class<?> clazz = Class.forName("com.tacz.guns.entity.shooter.LivingEntitySpeedModifier");
            Field extra = clazz.getDeclaredField("EXTRA_SPEED_MODIFIER_UUID");
            Field weight = clazz.getDeclaredField("WEIGHT_SPEED_MODIFIER_UUID");
            extra.setAccessible(true);
            weight.setAccessible(true);
            extraSpeedUuid = (UUID) extra.get(null);
            weightSpeedUuid = (UUID) weight.get(null);
        } catch (Throwable ignored) {
            // 反射失败就放弃移速保护，其余功能照常
        }
    }

    private static void sendAim(boolean aiming) {
        try {
            NetworkHandler.CHANNEL.sendToServer(new ClientMessagePlayerAim(aiming));
        } catch (Throwable ignored) {
            // Tacz 不在或者通道没准备好，忽略
        }
    }
}
