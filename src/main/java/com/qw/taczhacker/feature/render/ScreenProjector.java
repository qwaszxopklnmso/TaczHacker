package com.qw.taczhacker.feature.render;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 世界坐标 → 屏幕（GUI）坐标的投影工具（ESP 和 NameTags 共用）
 *
 * 两个要点：
 *
 * 1. **矩阵从渲染管线直接拿，不要自己拼相机旋转**
 *    在 RenderLevelStageEvent(AFTER_PARTICLES) 里把这一帧的 view / projection 矩阵和
 *    相机位置存下来。GUI 阶段（RenderGuiOverlayEvent）时世界矩阵已经被换成 GUI 的了，
 *    所以必须提前抓。
 *    曾经用 new PoseStack().mulPose(camera.rotation().conjugate()) 自己拼，方向是反的，
 *    症状是「视野内的目标被丢掉、视野外的反而画出来」。
 *
 * 2. **相机平面之后不能走透视除法**
 *    除以负的 w 得到的是镜像，翻符号也还原不回来，而且 w 接近 0 时会爆成无穷大。
 *    所以背后单独处理：只拿相机空间的 x/y 定方向（右就是右、上就是上），
 *    大小固定放大到 NDC 的 ±4，落到屏幕外。
 */
@Mod.EventBusSubscriber(modid = "taczhacker", value = Dist.CLIENT)
public final class ScreenProjector {

    /** 相机前方判定阈值（格），太贴近相机的点也当作「背后」处理，避免除法爆掉 */
    private static final float FRONT_Z_THRESHOLD = -0.05F;

    /** 背后目标放大到的 NDC 幅度 */
    private static final float BEHIND_NDC_SCALE = 4.0F;

    private static final Matrix4f FRAME_MODELVIEW = new Matrix4f();
    private static final Matrix4f FRAME_PROJECTION = new Matrix4f();
    private static double frameCamX;
    private static double frameCamY;
    private static double frameCamZ;
    private static volatile boolean frameValid = false;

    private ScreenProjector() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        FRAME_MODELVIEW.set(event.getPoseStack().last().pose());
        FRAME_PROJECTION.set(event.getProjectionMatrix());

        Vec3 cameraPos = event.getCamera().getPosition();
        frameCamX = cameraPos.x;
        frameCamY = cameraPos.y;
        frameCamZ = cameraPos.z;
        frameValid = true;
    }

    /**
     * 这一帧的矩阵抓到了没有（没抓到就别画，画出来位置是错的）
     */
    public static boolean isReady() {
        return frameValid;
    }

    /**
     * 世界坐标 → GUI 坐标；拿不到有效投影时返回 null
     */
    public static float[] project(Vec3 worldPos) {
        if (!frameValid) return null;

        // 转到相机空间（相机朝 -Z 看）
        Vector4f eye = toCameraSpace(worldPos);

        float ndcX;
        float ndcY;

        if (eye.z < FRONT_Z_THRESHOLD) {
            // 相机前方：正常做透视投影
            Vector4f clip = new Vector4f(eye.x, eye.y, eye.z, 1.0F);
            clip.mul(FRAME_PROJECTION);
            if (clip.w <= 1.0E-4F) {
                return null;
            }
            ndcX = clip.x / clip.w;
            ndcY = clip.y / clip.w;
        } else {
            // 相机平面之后：方向只由相机空间的 x/y 决定，不碰 w
            float length = Mth.sqrt(eye.x * eye.x + eye.y * eye.y);
            if (length < 1.0E-3F) {
                // 正好在正后方，没有方向可言，就让它指向屏幕中心
                length = 1.0E-3F;
            }
            float scale = BEHIND_NDC_SCALE / length;
            ndcX = eye.x * scale;
            ndcY = eye.y * scale;
        }

        float screenW = screenWidth();
        float screenH = screenHeight();
        float screenX = (ndcX * 0.5F + 0.5F) * screenW;
        float screenY = (-ndcY * 0.5F + 0.5F) * screenH;
        return new float[]{screenX, screenY};
    }

    /**
     * 世界坐标 → 相机空间坐标（相机朝 -Z 看，z 越小越靠前）
     *
     * 抓下来的 view 矩阵是「相机相对」的，所以输入要先减相机位置。
     */
    private static Vector4f toCameraSpace(Vec3 worldPos) {
        float relativeX = (float) (worldPos.x - frameCamX);
        float relativeY = (float) (worldPos.y - frameCamY);
        float relativeZ = (float) (worldPos.z - frameCamZ);

        Vector4f eye = new Vector4f(relativeX, relativeY, relativeZ, 1.0F);
        eye.mul(FRAME_MODELVIEW);
        return eye;
    }

    /**
     * 目标是不是在相机前方
     *
     * 相机平面之后的点，投影出来只有方向是对的（大小是硬放大的），
     * 所以画方框 / 骨骼这类「要求位置精确」的东西之前先用这个挡掉。
     */
    public static boolean isInFront(Vec3 worldPos) {
        if (!frameValid) return false;
        return toCameraSpace(worldPos).z < FRONT_Z_THRESHOLD;
    }

    public static float screenWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public static float screenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
