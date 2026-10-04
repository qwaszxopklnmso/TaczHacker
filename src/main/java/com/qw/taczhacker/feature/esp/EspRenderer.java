package com.qw.taczhacker.feature.esp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.qw.taczhacker.feature.render.ScreenProjector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 功能8 的绘制工具：连线 / 方框 / 骨骼
 *
 * GuiGraphics#fill 只能画轴对齐矩形，所以：
 *   - 任意方向的线：先把坐标系平移到起点、绕 Z 轴转到线的方向，再画「长度 x 线宽」的矩形
 *   - 方框：把包围盒 8 个角投影到屏幕，取屏幕空间的 min/max，再用线画 4 条边
 *   - 骨骼：按包围盒 + 实体朝向估出关节点，关节点之间连线
 *
 * 线宽小于 1 时用「1 像素 + 按比例降不透明度」模拟更细的线（GUI 矩形最小就是 1 像素）。
 */
public final class EspRenderer {

    /** 低于这个不透明度就不画了，太淡在亮背景上等于没有 */
    private static final int MIN_ALPHA = 0x20;

    private EspRenderer() {
    }

    /**
     * 画一条任意方向的线
     */
    public static void drawLine(GuiGraphics graphics, float x1, float y1, float x2, float y2,
                                int color, float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 1.0F) return;

        int len = Math.max(1, Math.round(length));

        int thick;
        int argb = color;
        if (width < 1.0F) {
            // GUI 的矩形最小就是 1 像素，画不出真的 0.5 像素。
            // 线宽 < 1 就改用「1 像素 + 按比例降不透明度」模拟，视觉上比实心 1 像素轻。
            thick = 1;
            int alpha = Math.round(255.0F * Mth.clamp(width, 0.1F, 1.0F));
            alpha = Mth.clamp(alpha, MIN_ALPHA, 0xFF);
            argb = (color & 0x00FFFFFF) | (alpha << 24);
        } else {
            thick = Math.max(1, Math.round(width));
        }

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x1, y1, 0.0F);
        pose.mulPose(Axis.ZP.rotation((float) Mth.atan2(dy, dx)));
        graphics.fill(0, 0, len, thick, argb);
        pose.popPose();
    }

    /**
     * 画一个圆
     *
     * GuiGraphics 没有圆弧 API，所以用线段拼：段数随半径增加，
     * 少了会明显看出多边形。
     */
    public static void drawCircle(GuiGraphics graphics, float centerX, float centerY, float radius,
                                  int color, float width) {
        if (radius < 1.0F) return;

        int segments = Mth.clamp((int) (radius * 0.8F), 24, 128);
        double step = Math.PI * 2.0D / segments;

        float prevX = centerX + radius;
        float prevY = centerY;
        for (int i = 1; i <= segments; i++) {
            double angle = step * i;
            float x = centerX + (float) (Math.cos(angle) * radius);
            float y = centerY + (float) (Math.sin(angle) * radius);
            drawLine(graphics, prevX, prevY, x, y, color, width);
            prevX = x;
            prevY = y;
        }
    }

    /**
     * 画 2D 包围盒
     *
     * 调用前先用 {@link ScreenProjector#isInFront} 确认目标在相机前方，
     * 相机平面之后的点投影出来只有方向是对的，框会变形。
     */
    public static void drawBox(GuiGraphics graphics, AABB box, int color, float width) {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int i = 0; i < 8; i++) {
            double x = ((i & 1) == 0) ? box.minX : box.maxX;
            double y = ((i & 2) == 0) ? box.minY : box.maxY;
            double z = ((i & 4) == 0) ? box.minZ : box.maxZ;

            float[] point = ScreenProjector.project(new Vec3(x, y, z));
            if (point == null) return;

            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }

        if (maxX - minX < 1.0F || maxY - minY < 1.0F) return;

        drawLine(graphics, minX, minY, maxX, minY, color, width);
        drawLine(graphics, minX, maxY, maxX, maxY, color, width);
        drawLine(graphics, minX, minY, minX, maxY, color, width);
        drawLine(graphics, maxX, minY, maxX, maxY, color, width);
    }

    /**
     * 画「棍状人」骨骼：头 / 脖子 / 胯 + 两条手臂 + 两条腿
     *
     * 原版没给骨骼节点，所以按包围盒高度比例估计关节位置，
     * 左右方向取实体朝向的垂直方向（这样转身时骨架跟着转）。
     */
    public static void drawSkeleton(GuiGraphics graphics, LivingEntity living, int color, float width) {
        AABB box = living.getBoundingBox();
        double height = box.getYsize();
        double entityWidth = box.getXsize();
        if (height <= 0.0D) return;

        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        double footY = box.minY;

        // 朝向的垂直方向就是「左右」
        double yaw = Math.toRadians(living.getYRot());
        double offsetX = Math.cos(yaw) * entityWidth * 0.5D;
        double offsetZ = Math.sin(yaw) * entityWidth * 0.5D;

        Vec3 head = new Vec3(centerX, footY + height, centerZ);
        Vec3 neck = new Vec3(centerX, footY + height * 0.84D, centerZ);
        Vec3 hip = new Vec3(centerX, footY + height * 0.48D, centerZ);

        Vec3 shoulderLeft = new Vec3(centerX - offsetX, footY + height * 0.82D, centerZ - offsetZ);
        Vec3 shoulderRight = new Vec3(centerX + offsetX, footY + height * 0.82D, centerZ + offsetZ);
        Vec3 handLeft = new Vec3(centerX - offsetX * 1.3D, footY + height * 0.45D, centerZ - offsetZ * 1.3D);
        Vec3 handRight = new Vec3(centerX + offsetX * 1.3D, footY + height * 0.45D, centerZ + offsetZ * 1.3D);
        Vec3 legLeft = new Vec3(centerX - offsetX * 0.5D, footY, centerZ - offsetZ * 0.5D);
        Vec3 legRight = new Vec3(centerX + offsetX * 0.5D, footY, centerZ + offsetZ * 0.5D);

        segment(graphics, head, neck, color, width);
        segment(graphics, neck, hip, color, width);
        segment(graphics, shoulderLeft, handLeft, color, width);
        segment(graphics, shoulderRight, handRight, color, width);
        segment(graphics, hip, legLeft, color, width);
        segment(graphics, hip, legRight, color, width);
    }

    private static void segment(GuiGraphics graphics, Vec3 from, Vec3 to, int color, float width) {
        float[] a = ScreenProjector.project(from);
        float[] b = ScreenProjector.project(to);
        if (a == null || b == null) return;
        drawLine(graphics, a[0], a[1], b[0], b[1], color, width);
    }
}
