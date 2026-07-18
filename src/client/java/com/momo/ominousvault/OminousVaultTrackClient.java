package com.momo.ominousvault;

import com.momo.ominousvault.client.VaultTrackerController;
import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.storage.VaultStorage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public class OminousVaultTrackClient implements ClientModInitializer {
    private static final VaultTrackerController CONTROLLER = new VaultTrackerController();

    @Override
    public void onInitializeClient() {
        ConfigManager.init();
        VaultStorage.init();

        ClientTickEvents.END_CLIENT_TICK.register(CONTROLLER::tick);
        UseBlockCallback.EVENT.register(CONTROLLER::onUseBlock);
        WorldRenderEvents.END_MAIN.register(CONTROLLER::render);
    }

    public static boolean isConfigHotkeyPressed(MinecraftClient client) {
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().getHandle();
        int key = ConfigManager.get().configHotkey;
        return key >= 0 && GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }
}
