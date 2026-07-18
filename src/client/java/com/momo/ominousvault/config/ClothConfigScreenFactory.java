package com.momo.ominousvault.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public final class ClothConfigScreenFactory {
    private ClothConfigScreenFactory() {
    }

    public static Screen create(Screen parent) {
        ModConfig config = ConfigManager.get();
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("text.ominous-vault-track.title"))
                .setSavingRunnable(ConfigManager::save);
        ConfigEntryBuilder entries = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Text.translatable("text.ominous-vault-track.category.general"));
        general.addEntry(entries.startBooleanToggle(Text.translatable("text.ominous-vault-track.option.enabled"), config.enabled)
                .setDefaultValue(false)
                .setSaveConsumer(value -> config.enabled = value)
                .build());
        general.addEntry(entries.startColorField(Text.translatable("text.ominous-vault-track.option.highlight_color"), config.highlightColor)
                .setDefaultValue(0xFF3C3C)
                .setSaveConsumer(value -> config.highlightColor = value)
                .build());
        general.addEntry(entries.startEnumSelector(Text.translatable("text.ominous-vault-track.option.excluded_mode"), ModConfig.ExcludedRenderMode.class, config.excludedRenderMode)
                .setDefaultValue(ModConfig.ExcludedRenderMode.HIDE)
                .setSaveConsumer(value -> config.excludedRenderMode = value)
                .build());
        general.addEntry(entries.startColorField(Text.translatable("text.ominous-vault-track.option.excluded_color"), config.excludedColor)
                .setDefaultValue(0x40A0FF)
                .setSaveConsumer(value -> config.excludedColor = value)
                .build());
        general.addEntry(entries.startIntField(Text.translatable("text.ominous-vault-track.option.render_chunks"), config.renderRadius)
                .setDefaultValue(8)
                .setMin(1)
                .setMax(32)
                .setSaveConsumer(value -> config.renderRadius = value)
                .build());

        ConfigCategory keybinds = builder.getOrCreateCategory(Text.translatable("text.ominous-vault-track.category.keybinds"));
        keybinds.addEntry(entries.startKeyCodeField(
                        Text.translatable("text.ominous-vault-track.option.hotkey"),
                        InputUtil.Type.KEYSYM.createFromCode(config.configHotkey))
                .setDefaultValue(InputUtil.Type.KEYSYM.createFromCode(InputUtil.GLFW_KEY_B))
                .setKeySaveConsumer(key -> config.configHotkey = key.getCode())
                .build());

        ConfigCategory tracer = builder.getOrCreateCategory(Text.translatable("text.ominous-vault-track.category.tracer"));
        tracer.addEntry(entries.startBooleanToggle(Text.translatable("text.ominous-vault-track.option.render_tracers"), config.renderTracers)
                .setDefaultValue(true)
                .setSaveConsumer(value -> config.renderTracers = value)
                .build());
        tracer.addEntry(entries.startColorField(Text.translatable("text.ominous-vault-track.option.tracer_color"), config.tracerColor)
                .setDefaultValue(0x00FF80)
                .setSaveConsumer(value -> config.tracerColor = value)
                .build());
        tracer.addEntry(entries.startBooleanToggle(Text.translatable("text.ominous-vault-track.option.tracer_requires_item"), config.tracerRequiresItem)
                .setDefaultValue(false)
                .setSaveConsumer(value -> config.tracerRequiresItem = value)
                .build());
        tracer.addEntry(entries.startStrField(Text.translatable("text.ominous-vault-track.option.tracer_item"), config.tracerItemId)
                .setDefaultValue("minecraft:ominous_trial_key")
                .setSaveConsumer(value -> config.tracerItemId = value)
                .build());

        ConfigCategory refresh = builder.getOrCreateCategory(Text.translatable("text.ominous-vault-track.category.refresh"));
        refresh.addEntry(entries.startBooleanToggle(Text.translatable("text.ominous-vault-track.option.refresh_enabled"), config.refreshEnabled)
                .setDefaultValue(false)
                .setSaveConsumer(value -> config.refreshEnabled = value)
                .build());
        refresh.addEntry(entries.startEnumSelector(Text.translatable("text.ominous-vault-track.option.refresh_scope"), ModConfig.RefreshScope.class, config.refreshScope)
                .setDefaultValue(ModConfig.RefreshScope.SERVER_DIMENSION)
                .setSaveConsumer(value -> config.refreshScope = value)
                .build());
        refresh.addEntry(entries.startEnumSelector(Text.translatable("text.ominous-vault-track.option.refresh_mode"), ModConfig.RefreshMode.class, config.refreshMode)
                .setDefaultValue(ModConfig.RefreshMode.REAL_TIME_COOLDOWN)
                .setSaveConsumer(value -> config.refreshMode = value)
                .build());
        refresh.addEntry(entries.startLongField(Text.translatable("text.ominous-vault-track.option.refresh_minutes"), config.refreshCooldownMinutes)
                .setDefaultValue(1440L)
                .setMin(1L)
                .setSaveConsumer(value -> config.refreshCooldownMinutes = value)
                .build());
        refresh.addEntry(entries.startIntSlider(Text.translatable("text.ominous-vault-track.option.daily_hour"), config.dailyResetHour, 0, 23)
                .setDefaultValue(0)
                .setSaveConsumer(value -> config.dailyResetHour = value)
                .build());

        return builder.build();
    }
}
