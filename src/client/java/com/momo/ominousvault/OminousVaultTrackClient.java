package com.momo.ominousvault;

import com.momo.ominousvault.client.VaultTrackerController;
import com.momo.ominousvault.config.ConfigManager;
import com.momo.ominousvault.storage.VaultStorage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class OminousVaultTrackClient implements ClientModInitializer {
    private static final VaultTrackerController CONTROLLER = new VaultTrackerController();

    @Override
    public void onInitializeClient() {
        ConfigManager.init();
        VaultStorage.init();

        ClientTickEvents.END_CLIENT_TICK.register(CONTROLLER::tick);
        UseBlockCallback.EVENT.register(CONTROLLER::onUseBlock);

        // Render the vault ESP in the HUD instead of the world render pipeline.
        // Iris shader packs can replace/composite the world framebuffer after custom
        // world geometry was submitted, making RenderType-based ESP disappear.
        // The HUD is rendered after the final world image, so this remains visible
        // with or without Iris/shader packs and is always on top of terrain.
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(OminousVaultTrack.MOD_ID, "vault_esp_overlay"),
                CONTROLLER::renderHud
        );
    }

    public static boolean isComboOpenConfigPressed(Minecraft client) {
        if (client.getWindow() == null) return false;
        long handle = client.getWindow().handle();
        int key = ConfigManager.get().configHotkey;
        return key >= 0 && GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
    }
}
