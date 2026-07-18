package com.momo.ominousvault.client;

import com.momo.ominousvault.OminousVaultTrackClient;
import com.momo.ominousvault.config.ClothConfigScreenFactory;
import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.config.ModConfig;
import com.momo.ominousvault.storage.VaultKey;
import com.momo.ominousvault.storage.VaultStorage;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

public class VaultTrackerController {
    private static final int RESCAN_INTERVAL_TICKS = 40;
    private static final int CHUNKS_SCANNED_PER_TICK = 8;

    private final Set<VaultKey> loadedOminousVaults = new HashSet<>();
    private final Map<Long, Set<VaultKey>> vaultsByChunk = new HashMap<>();
    private final ArrayDeque<Long> chunksToScan = new ArrayDeque<>();
    private final VaultRenderer renderer = new VaultRenderer();
    private int scanCooldownTicks;
    private int refreshCooldownTicks;
    private boolean comboWasDown;
    private String lastServer = "";
    private String lastDimension = "";
    private int lastCenterChunkX = Integer.MIN_VALUE;
    private int lastCenterChunkZ = Integer.MIN_VALUE;
    private int lastChunkRadius = -1;
    private static String cachedTracerItemId = "";
    private static Item cachedTracerItem;

    public void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            clearScanState();
            comboWasDown = false;
            return;
        }

        handleConfigCombo(client);

        String server = currentServerKey(client);
        String dimension = currentDimensionKey(client.world);
        if (!server.equals(lastServer) || !dimension.equals(lastDimension)) {
            clearScanState();
            scanCooldownTicks = 0;
            lastServer = server;
            lastDimension = dimension;
        }

        if (--refreshCooldownTicks <= 0) {
            refreshCooldownTicks = 20 * 30;
            VaultStorage.applyRefresh(ConfigManager.get(), server, dimension);
        }

        ModConfig config = ConfigManager.get();
        if (!config.enabled) {
            clearScanState();
            return;
        }

        BlockPos playerPos = client.player.getBlockPos();
        int centerChunkX = ChunkSectionPos.getSectionCoord(playerPos.getX());
        int centerChunkZ = ChunkSectionPos.getSectionCoord(playerPos.getZ());
        int chunkRadius = config.renderRadius;
        boolean scanAreaChanged = centerChunkX != lastCenterChunkX
                || centerChunkZ != lastCenterChunkZ
                || chunkRadius != lastChunkRadius;

        if (scanAreaChanged || (chunksToScan.isEmpty() && --scanCooldownTicks <= 0)) {
            scheduleScan(centerChunkX, centerChunkZ, chunkRadius);
            scanCooldownTicks = RESCAN_INTERVAL_TICKS;
        }
        scanQueuedChunks(client.world, server, dimension);
    }

    public ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (!world.isClient() || hand != Hand.MAIN_HAND) return ActionResult.PASS;

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        if (!isOminousVault(state)) return ActionResult.PASS;

        MinecraftClient client = MinecraftClient.getInstance();
        VaultKey key = VaultKey.of(currentServerKey(client), currentDimensionKey(world), pos);
        VaultStorage.exclude(key);
        loadedOminousVaults.add(key);

        return ActionResult.PASS;
    }

    public void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !ConfigManager.get().enabled) return;
        renderer.render(context, client, loadedOminousVaults);
    }

    public static boolean isOminousVault(BlockState state) {
        if (!state.isOf(Blocks.VAULT)) return false;
        return state.contains(Properties.OMINOUS) && state.get(Properties.OMINOUS);
    }

    public static String currentServerKey(MinecraftClient client) {
        if (client == null) return "unknown";
        if (client.isInSingleplayer()) return "singleplayer";
        ServerInfo info = client.getCurrentServerEntry();
        if (info != null && info.address != null && !info.address.isBlank()) return info.address;
        return "unknown";
    }

    public static String currentDimensionKey(World world) {
        if (world == null) return "unknown";
        return world.getRegistryKey().getValue().toString();
    }

    private void handleConfigCombo(MinecraftClient client) {
        boolean comboDown = OminousVaultTrackClient.isConfigHotkeyPressed(client);
        if (comboDown && !comboWasDown && client.currentScreen == null) {
            client.setScreen(ClothConfigScreenFactory.create(null));
        }
        comboWasDown = comboDown;
    }

    private void scheduleScan(int centerChunkX, int centerChunkZ, int chunkRadius) {
        lastCenterChunkX = centerChunkX;
        lastCenterChunkZ = centerChunkZ;
        lastChunkRadius = chunkRadius;
        chunksToScan.clear();

        for (int ring = 0; ring <= chunkRadius; ring++) {
            for (int x = -ring; x <= ring; x++) {
                enqueueChunk(centerChunkX + x, centerChunkZ - ring);
                if (ring != 0) enqueueChunk(centerChunkX + x, centerChunkZ + ring);
            }
            for (int z = -ring + 1; z < ring; z++) {
                enqueueChunk(centerChunkX - ring, centerChunkZ + z);
                if (ring != 0) enqueueChunk(centerChunkX + ring, centerChunkZ + z);
            }
        }

        var iterator = vaultsByChunk.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int chunkX = (int) (long) entry.getKey();
            int chunkZ = (int) (entry.getKey() >> 32);
            if (Math.abs((long) chunkX - centerChunkX) > chunkRadius
                    || Math.abs((long) chunkZ - centerChunkZ) > chunkRadius) {
                loadedOminousVaults.removeAll(entry.getValue());
                iterator.remove();
            }
        }
    }

    private void enqueueChunk(int chunkX, int chunkZ) {
        chunksToScan.addLast(packChunk(chunkX, chunkZ));
    }

    private void scanQueuedChunks(ClientWorld world, String server, String dimension) {
        for (int count = 0; count < CHUNKS_SCANNED_PER_TICK && !chunksToScan.isEmpty(); count++) {
            long packed = chunksToScan.removeFirst();
            int chunkX = (int) packed;
            int chunkZ = (int) (packed >> 32);
            WorldChunk chunk = world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            Set<VaultKey> previous = vaultsByChunk.remove(packed);
            if (previous != null) loadedOminousVaults.removeAll(previous);
            if (chunk == null) continue;

            Set<VaultKey> found = scanChunk(chunk, server, dimension);
            if (!found.isEmpty()) {
                vaultsByChunk.put(packed, found);
                loadedOminousVaults.addAll(found);
            }
        }
    }

    private Set<VaultKey> scanChunk(WorldChunk chunk, String server, String dimension) {
        Set<VaultKey> found = new HashSet<>();
        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            BlockState state = chunk.getBlockState(pos);
            if (!isOminousVault(state)) continue;
            found.add(VaultKey.of(server, dimension, pos));
        }
        return found;
    }

    private void clearScanState() {
        loadedOminousVaults.clear();
        vaultsByChunk.clear();
        chunksToScan.clear();
        lastCenterChunkX = Integer.MIN_VALUE;
        lastCenterChunkZ = Integer.MIN_VALUE;
        lastChunkRadius = -1;
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return Integer.toUnsignedLong(chunkX) | ((long) chunkZ << 32);
    }

    public static boolean shouldRenderTracer(MinecraftClient client, ModConfig config) {
        if (!config.renderTracers) return false;
        if (!config.tracerRequiresItem) return true;

        Identifier id = Identifier.tryParse(config.tracerItemId);
        if (id == null) return false;
        if (!config.tracerItemId.equals(cachedTracerItemId)) {
            cachedTracerItemId = config.tracerItemId;
            cachedTracerItem = Registries.ITEM.get(id);
        }
        return client.player != null && cachedTracerItem != null
                && (client.player.getMainHandStack().isOf(cachedTracerItem) || client.player.getOffHandStack().isOf(cachedTracerItem));
    }
}
