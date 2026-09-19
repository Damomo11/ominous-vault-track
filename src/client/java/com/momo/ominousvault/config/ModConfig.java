package com.momo.ominousvault.config;

public class ModConfig {
    public boolean enabled = false;
    public int highlightColor = 0xFF3C3C;
    public ExcludedRenderMode excludedRenderMode = ExcludedRenderMode.HIDE;
    public int excludedColor = 0x40A0FF;
    public boolean renderTracers = true;
    public int tracerColor = 0x00FF80;
    public boolean tracerRequiresItem = false;
    public String tracerItemId = "minecraft:ominous_trial_key";
    /** Number of chunks to search around the player. Kept under the old JSON name for migration. */
    public int renderRadius = 8;
    public int configHotkey = 66;
    public boolean refreshEnabled = false;
    public RefreshScope refreshScope = RefreshScope.SERVER_DIMENSION;
    public RefreshMode refreshMode = RefreshMode.REAL_TIME_COOLDOWN;
    public long refreshCooldownMinutes = 1440;
    public int dailyResetHour = 0;

    public void validate() {
        if (renderRadius > 32) renderRadius = (renderRadius + 15) >> 4;
        renderRadius = Math.max(1, Math.min(32, renderRadius));
        configHotkey = Math.max(-1, configHotkey);
        refreshCooldownMinutes = Math.max(1, refreshCooldownMinutes);
        dailyResetHour = Math.max(0, Math.min(23, dailyResetHour));
        if (excludedRenderMode == null) excludedRenderMode = ExcludedRenderMode.HIDE;
        if (refreshScope == null) refreshScope = RefreshScope.SERVER_DIMENSION;
        if (refreshMode == null) refreshMode = RefreshMode.REAL_TIME_COOLDOWN;
        if (tracerItemId == null || tracerItemId.isBlank() || tracerItemId.equals("minecraft:trial_key")) {
            tracerItemId = "minecraft:ominous_trial_key";
        }
        highlightColor &= 0xFFFFFF;
        excludedColor &= 0xFFFFFF;
        tracerColor &= 0xFFFFFF;
    }

    public enum ExcludedRenderMode {
        HIDE("隐藏"),
        OTHER_COLOR("使用其他颜色");

        private final String displayName;

        ExcludedRenderMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum RefreshScope {
        SERVER_DIMENSION("当前服务器和维度"),
        SERVER_ALL_DIMENSIONS("当前服务器所有维度"),
        GLOBAL("全部服务器");

        private final String displayName;

        RefreshScope(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public enum RefreshMode {
        REAL_TIME_COOLDOWN("现实时间冷却"),
        DAILY_RESET("每日重置");

        private final String displayName;

        RefreshMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
