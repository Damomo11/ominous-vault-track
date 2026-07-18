package com.momo.ominousvault.client;

import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.config.ModConfig;
import com.momo.ominousvault.storage.VaultKey;
import com.momo.ominousvault.storage.VaultStorage;
import java.util.Collection;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class VaultRenderer {
    public void render(WorldRenderContext context, MinecraftClient client, Collection<VaultKey> vaults) {
        ModConfig config = ConfigManager.get();
        MatrixStack matrices = context.matrices();
        Vec3d camera = context.worldState().cameraRenderState.pos;
        Vector3f crosshairOrigin = getCrosshairOrigin(client);
        boolean renderTracer = VaultTrackerController.shouldRenderTracer(client, config);
        int tracerColor = toOpaqueArgb(config.tracerColor);
        String server = VaultTrackerController.currentServerKey(client);
        String dimension = VaultTrackerController.currentDimensionKey(client.world);
        BlockPos playerPos = client.player.getBlockPos();
        long blockRadius = (long) config.renderRadius * 16L;
        long radiusSquared = blockRadius * blockRadius;
        VertexConsumerProvider consumers = context.consumers();

        matrices.push();
        MatrixStack.Entry pose = matrices.peek();

        VertexConsumer boxLines = consumers.getBuffer(VaultRenderLayers.SEE_THROUGH_LINES);
        for (VaultKey key : vaults) {
            if (!isInRenderScope(key, server, dimension, playerPos, radiusSquared)) continue;

            boolean excluded = VaultStorage.isExcluded(key);
            if (excluded && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;
            int boxColor = toOpaqueArgb(excluded ? config.excludedColor : config.highlightColor);
            BlockPos pos = key.toBlockPos();
            drawBox(boxLines, pose, pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                    pos.getX() + 1 - camera.x, pos.getY() + 1 - camera.y, pos.getZ() + 1 - camera.z, boxColor);
        }
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(VaultRenderLayers.SEE_THROUGH_LINES);
        }

        if (renderTracer) {
            VertexConsumer tracerLines = consumers.getBuffer(RenderLayers.lines());
            for (VaultKey key : vaults) {
                if (!isInRenderScope(key, server, dimension, playerPos, radiusSquared)) continue;
                if (VaultStorage.isExcluded(key) && config.excludedRenderMode == ModConfig.ExcludedRenderMode.HIDE) continue;

                Vec3d end = Vec3d.ofCenter(key.toBlockPos());
                drawLine(tracerLines, pose, crosshairOrigin.x, crosshairOrigin.y, crosshairOrigin.z,
                        end.x - camera.x, end.y - camera.y, end.z - camera.z, tracerColor);
            }
        }

        matrices.pop();
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) immediate.draw();
    }

    private static boolean isInRenderScope(VaultKey key, String server, String dimension, BlockPos playerPos, long radiusSquared) {
        if (!key.server().equals(server) || !key.dimension().equals(dimension)) return false;
        long dx = (long) key.x() - playerPos.getX();
        long dy = (long) key.y() - playerPos.getY();
        long dz = (long) key.z() - playerPos.getZ();
        return dx * dx + dy * dy + dz * dz <= radiusSquared;
    }

    private static void drawBox(VertexConsumer consumer, MatrixStack.Entry pose, double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        drawLine(consumer, pose, x1, y1, z1, x2, y1, z1, argb);
        drawLine(consumer, pose, x2, y1, z1, x2, y1, z2, argb);
        drawLine(consumer, pose, x2, y1, z2, x1, y1, z2, argb);
        drawLine(consumer, pose, x1, y1, z2, x1, y1, z1, argb);
        drawLine(consumer, pose, x1, y2, z1, x2, y2, z1, argb);
        drawLine(consumer, pose, x2, y2, z1, x2, y2, z2, argb);
        drawLine(consumer, pose, x2, y2, z2, x1, y2, z2, argb);
        drawLine(consumer, pose, x1, y2, z2, x1, y2, z1, argb);
        drawLine(consumer, pose, x1, y1, z1, x1, y2, z1, argb);
        drawLine(consumer, pose, x2, y1, z1, x2, y2, z1, argb);
        drawLine(consumer, pose, x2, y1, z2, x2, y2, z2, argb);
        drawLine(consumer, pose, x1, y1, z2, x1, y2, z2, argb);
    }

    private static void drawLine(VertexConsumer consumer, MatrixStack.Entry pose, double x1, double y1, double z1, double x2, double y2, double z2, int argb) {
        float nx = (float) (x2 - x1);
        float ny = (float) (y2 - y1);
        float nz = (float) (z2 - z1);
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 0.0F) {
            nx /= length;
            ny /= length;
            nz /= length;
        }
        consumer.vertex(pose, (float) x1, (float) y1, (float) z1).color(argb).lineWidth(2.0F).normal(pose, nx, ny, nz);
        consumer.vertex(pose, (float) x2, (float) y2, (float) z2).color(argb).lineWidth(2.0F).normal(pose, nx, ny, nz);
    }

    private static Vector3f getCrosshairOrigin(MinecraftClient client) {
        Matrix4f projection = client.gameRenderer.getBasicProjectionMatrix(client.options.getFov().getValue());
        MatrixStack bobStack = new MatrixStack();
        if (client.options.getBobView().getValue() && client.getCameraEntity() instanceof AbstractClientPlayerEntity player) {
            float tickProgress = client.gameRenderer.getCamera().getLastTickProgress();
            float distance = player.getState().getReverseLerpedDistanceMoved(tickProgress);
            float movement = player.getState().lerpMovement(tickProgress);
            bobStack.translate(
                    Math.sin(distance * Math.PI) * movement * 0.5F,
                    -Math.abs(Math.cos(distance * Math.PI) * movement),
                    0.0F
            );
            bobStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) (Math.sin(distance * Math.PI) * movement * 3.0F)));
            bobStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) (Math.abs(Math.cos(distance * Math.PI - 0.2F) * movement) * 5.0F)));
        }
        projection.mul(bobStack.peek().getPositionMatrix());

        Matrix4f view = new Matrix4f().rotation(client.gameRenderer.getCamera().getRotation().conjugate(new org.joml.Quaternionf()));
        org.joml.Vector4f center = new org.joml.Vector4f(0.0F, 0.0F, 0.0F, 1.0F)
                .mul(projection.invert())
                .mul(view.invert());
        center.div(center.w);
        return new Vector3f(center.x, center.y, center.z);
    }

    private static int toOpaqueArgb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
