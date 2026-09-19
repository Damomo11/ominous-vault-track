package com.momo.ominousvault.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.momo.ominousvault.OminousVaultTrack;
import com.momo.ominousvault.config.ModConfig;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class VaultStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path STORAGE_PATH = FabricLoader.getInstance().getConfigDir().resolve(OminousVaultTrack.MOD_ID + "-vaults.json");
    private static final Map<VaultKey, VaultRecord> RECORDS = new HashMap<>();

    private VaultStorage() {
    }

    public static void init() {
        load();
    }

    public static void exclude(VaultKey key) {
        VaultRecord record = RECORDS.computeIfAbsent(key, VaultRecord::new);
        record.excludedAtMillis = System.currentTimeMillis();
        save();
    }

    public static boolean isExcluded(VaultKey key) {
        VaultRecord record = RECORDS.get(key);
        return record != null && record.excluded();
    }

    public static void applyRefresh(ModConfig config, String server, String dimension) {
        if (!config.refreshEnabled) return;

        long now = System.currentTimeMillis();
        boolean changed = RECORDS.values().removeIf(record ->
                record.excluded()
                        && refreshApplies(config, record, server, dimension)
                        && isExpired(config, record.excludedAtMillis, now)
        );

        if (changed) save();
    }

    private static boolean refreshApplies(ModConfig config, VaultRecord record, String server, String dimension) {
        return switch (config.refreshScope) {
            case SERVER_DIMENSION -> record.server.equals(server) && record.dimension.equals(dimension);
            case SERVER_ALL_DIMENSIONS -> record.server.equals(server);
            case GLOBAL -> true;
        };
    }

    private static boolean isExpired(ModConfig config, long excludedAtMillis, long nowMillis) {
        if (config.refreshMode == ModConfig.RefreshMode.REAL_TIME_COOLDOWN) {
            long cooldownMillis = config.refreshCooldownMinutes * 60_000L;
            return nowMillis - excludedAtMillis >= cooldownMillis;
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone);
        LocalDateTime excluded = LocalDateTime.ofInstant(Instant.ofEpochMilli(excludedAtMillis), zone);
        LocalDateTime latestCutoff = now.withHour(config.dailyResetHour).withMinute(0).withSecond(0).withNano(0);
        if (now.isBefore(latestCutoff)) latestCutoff = latestCutoff.minusDays(1);
        return excluded.isBefore(latestCutoff);
    }

    private static void load() {
        RECORDS.clear();
        if (!Files.exists(STORAGE_PATH)) return;

        try (Reader reader = Files.newBufferedReader(STORAGE_PATH)) {
            StorageFile file = GSON.fromJson(reader, StorageFile.class);
            if (file != null && file.records != null) {
                boolean droppedOldScanRecords = false;
                for (VaultRecord record : file.records) {
                    if (record.server != null && record.dimension != null && record.excluded()) {
                        RECORDS.put(record.key(), record);
                    } else {
                        droppedOldScanRecords = true;
                    }
                }
                if (droppedOldScanRecords) save();
            }
        } catch (Exception e) {
            OminousVaultTrack.LOGGER.warn("Failed to load vault storage", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(STORAGE_PATH.getParent());
            StorageFile file = new StorageFile();
            file.records = new ArrayList<>(RECORDS.values());
            try (Writer writer = Files.newBufferedWriter(STORAGE_PATH)) {
                GSON.toJson(file, writer);
            }
        } catch (IOException e) {
            OminousVaultTrack.LOGGER.warn("Failed to save vault storage", e);
        }
    }

    private static class StorageFile {
        Collection<VaultRecord> records = java.util.List.of();
    }
}
