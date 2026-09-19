package com.momo.ominousvault.client;

import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.config.ModConfig;
import com.momo.ominousvault.storage.VaultKey;
import com.momo.ominousvault.storage.VaultStorage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Shader-pack-proof vault ESP renderer.
 *
 * Instead of submitting world geometry (which Iris may render into an intermediate
 * framebuffer that is later replaced by the shader pack's final composite), this
 * renderer projects world-space vault geometry to screen space and draws it in the
 * final HUD layer. As a result, outlines and tracers are independent of depth and
 * remain visible with Iris shader packs enabled.
 */
public class VaultRenderer {
    private static final int[][] BOX_EDGES = {
            {0, 1}, {0, 2}, {0, 4},
            {1, 3}, {1, 5},
            {2, 3}, {2, 6},
            {3, 7},
            {4, 5}, {4, 6},
            {5, 7},
            {6, 7}
    };

    private static final float LINE_WIDTH = 2.0F;
    private static final float BOX_EXPAND = 0.015F;

    public void renderHud(GuiGraphicsExtractor graphics, Minecraft client, Collection<VaultKey> vaults) {
        if (vaults.isEmpty()) return;

        ModConfig config = ConfigManager.get();
        var gameState = client.gameRenderer.gameRenderState();
        CameraRenderState cameraState = gameState.levelRenderState.cameraRenderState;
        if (!cameraState.initialized) return;

        Vec3 camera = cameraState.pos;
        Matrix4f projection = buildProjectionWithViewEffects(
                cameraState,
                gameState.optionsRenderState.bobView,
                gameState.optionsRenderState.damageTiltStrength
        );

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        boolean renderTracer = VaultTrackerController.shouldRenderTracer(client, config);
        int tracerColor = toOpaqueArgb(config.tracerColor);
        String server = VaultTrackerController.currentServerKey(client);
        String dimension = VaultTrackerController.currentDimensionKey(client.level);
        BlockPos playerPos = client.player.blockPosition();
        long blockRadius = (long) config.renderRadius * 16L;
        long radiusSquared = blockRadius * blockRadius;

        for (VaultKey key : vaults) {
            if (!isInRenderScope(key, server, dimension, playerPos, radiusSquared)) continue;

            boolean excluded = VaultStorage.isExcluded(key);
            if (excluded && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;

            int boxColor = toOpaqueArgb(excluded ? config.excludedColor : config.highlightColor);
            drawProjectedBox(graphics, key, cameraState, projection, camera, width, height, boxColor);

            if (renderTracer) {
                ScreenPoint center = project(
                        key.x() + 0.5,
                        key.y() + 0.5,
                        key.z() + 0.5,
                        cameraState,
                        projection,
                        camera,
                        width,
                        height
                );
                if (center != null) {
                    // Start at the screen centre/crosshair. The line is clipped to the HUD
                    // rectangle, so off-screen vaults still point toward the correct edge.
                    drawClippedLine(
                            graphics,
                            width * 0.5F,
                            height * 0.5F,
                            center.x,
                            center.y,
                            LINE_WIDTH,
                            tracerColor,
                            width,
                            height
                    );
                }
            }
        }
    }

    private static void drawProjectedBox(
            GuiGraphicsExtractor graphics,
            VaultKey key,
            CameraRenderState cameraState,
            Matrix4f projection,
            Vec3 camera,
            int width,
            int height,
            int color
    ) {
        ScreenPoint[] points = new ScreenPoint[8];
        double minX = key.x() - BOX_EXPAND;
        double minY = key.y() - BOX_EXPAND;
        double minZ = key.z() - BOX_EXPAND;
        double maxX = key.x() + 1.0 + BOX_EXPAND;
        double maxY = key.y() + 1.0 + BOX_EXPAND;
        double maxZ = key.z() + 1.0 + BOX_EXPAND;

        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? minX : maxX;
            double y = (i & 2) == 0 ? minY : maxY;
            double z = (i & 4) == 0 ? minZ : maxZ;
            points[i] = project(x, y, z, cameraState, projection, camera, width, height);
        }

        for (int[] edge : BOX_EDGES) {
            ScreenPoint a = points[edge[0]];
            ScreenPoint b = points[edge[1]];
            if (a == null || b == null) continue;
            drawClippedLine(graphics, a.x, a.y, b.x, b.y, LINE_WIDTH, color, width, height);
        }
    }

    private static ScreenPoint project(
            double worldX,
            double worldY,
            double worldZ,
            CameraRenderState cameraState,
            Matrix4f projection,
            Vec3 camera,
            int width,
            int height
    ) {
        Vector4f clip = new Vector4f(
                (float) (worldX - camera.x),
                (float) (worldY - camera.y),
                (float) (worldZ - camera.z),
                1.0F
        );

        // Matches the inverse order used by vanilla/our old crosshair unprojection:
        // world-relative -> camera rotation -> projection (including hurt/view bob).
        clip.mul(cameraState.viewRotationMatrix);
        clip.mul(projection);

        // Points on/behind the camera plane cannot be projected safely.
        if (!Float.isFinite(clip.w) || clip.w <= 1.0e-4F) return null;

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) return null;

        // Keep coordinates bounded before the line clipper so extremely off-screen
        // points cannot overflow GUI transform math.
        ndcX = Mth.clamp(ndcX, -32.0F, 32.0F);
        ndcY = Mth.clamp(ndcY, -32.0F, 32.0F);

        float screenX = (ndcX * 0.5F + 0.5F) * width;
        float screenY = (0.5F - ndcY * 0.5F) * height;
        return new ScreenPoint(screenX, screenY);
    }

    private static Matrix4f buildProjectionWithViewEffects(
            CameraRenderState cameraState,
            boolean bobView,
            double damageTiltStrength
    ) {
        PoseStack bobStack = new PoseStack();
        applyHurtBob(cameraState, bobStack, damageTiltStrength);
        if (bobView) applyViewBob(cameraState, bobStack);

        // GameRenderer applies these view effects to the projection matrix before
        // rendering the level. Reproduce that transform so the HUD projection stays
        // aligned with the world while walking or taking damage.
        return new Matrix4f(cameraState.projectionMatrix).mul(bobStack.last().pose());
    }

    private static void applyHurtBob(
            CameraRenderState cameraState,
            PoseStack matrices,
            double damageTiltStrength
    ) {
        var entity = cameraState.entityRenderState;
        if (!entity.isLiving) return;

        float hurt = entity.hurtTime;
        if (entity.isDeadOrDying) {
            float duration = Math.min(entity.deathTime, 20.0F);
            matrices.mulPose(Axis.ZP.rotationDegrees(40.0F - 8000.0F / (duration + 200.0F)));
        }
        if (hurt < 0.0F || entity.hurtDuration <= 0) return;

        hurt /= entity.hurtDuration;
        hurt = Mth.sin(hurt * hurt * hurt * hurt * (float) Math.PI);
        matrices.mulPose(Axis.YP.rotationDegrees(-entity.hurtDir));
        matrices.mulPose(Axis.ZP.rotationDegrees((float) (-hurt * 14.0 * damageTiltStrength)));
        matrices.mulPose(Axis.YP.rotationDegrees(entity.hurtDir));
    }

    private static void applyViewBob(CameraRenderState cameraState, PoseStack matrices) {
        var entity = cameraState.entityRenderState;
        if (!entity.isPlayer) return;

        float walk = entity.backwardsInterpolatedWalkDistance;
        float bob = entity.bob;
        matrices.translate(
                Mth.sin(walk * (float) Math.PI) * bob * 0.5F,
                -Math.abs(Mth.cos(walk * (float) Math.PI) * bob),
                0.0F
        );
        matrices.mulPose(Axis.ZP.rotationDegrees(Mth.sin(walk * (float) Math.PI) * bob * 3.0F));
        matrices.mulPose(Axis.XP.rotationDegrees(Math.abs(Mth.cos(walk * (float) Math.PI - 0.2F) * bob) * 5.0F));
    }

    private static void drawClippedLine(
            GuiGraphicsExtractor graphics,
            float x1,
            float y1,
            float x2,
            float y2,
            float thickness,
            int color,
            int width,
            int height
    ) {
        float[] clipped = clipToRect(x1, y1, x2, y2, 0.0F, 0.0F, width - 1.0F, height - 1.0F);
        if (clipped == null) return;
        drawLine(graphics, clipped[0], clipped[1], clipped[2], clipped[3], thickness, color);
    }

    private static void drawLine(
            GuiGraphicsExtractor graphics,
            float x1,
            float y1,
            float x2,
            float y2,
            float thickness,
            int color
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5F) return;

        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate((float) Math.atan2(dy, dx));

        int half = Math.max(1, Math.round(thickness * 0.5F));
        graphics.fill(0, -half, Math.max(1, (int) Math.ceil(length)), half, color);
        pose.popMatrix();
    }

    /** Liang-Barsky line clipping. */
    private static float[] clipToRect(
            float x1,
            float y1,
            float x2,
            float y2,
            float minX,
            float minY,
            float maxX,
            float maxY
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float t0 = 0.0F;
        float t1 = 1.0F;

        float[] p = {-dx, dx, -dy, dy};
        float[] q = {x1 - minX, maxX - x1, y1 - minY, maxY - y1};

        for (int i = 0; i < 4; i++) {
            if (Math.abs(p[i]) < 1.0e-6F) {
                if (q[i] < 0.0F) return null;
                continue;
            }

            float r = q[i] / p[i];
            if (p[i] < 0.0F) {
                if (r > t1) return null;
                if (r > t0) t0 = r;
            } else {
                if (r < t0) return null;
                if (r < t1) t1 = r;
            }
        }

        return new float[]{
                x1 + t0 * dx,
                y1 + t0 * dy,
                x1 + t1 * dx,
                y1 + t1 * dy
        };
    }

    private static boolean isInRenderScope(
            VaultKey key,
            String server,
            String dimension,
            BlockPos playerPos,
            long radiusSquared
    ) {
        if (!key.server().equals(server) || !key.dimension().equals(dimension)) return false;
        long dx = (long) key.x() - playerPos.getX();
        long dy = (long) key.y() - playerPos.getY();
        long dz = (long) key.z() - playerPos.getZ();
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    private static int toOpaqueArgb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    private record ScreenPoint(float x, float y) {
    }
}
