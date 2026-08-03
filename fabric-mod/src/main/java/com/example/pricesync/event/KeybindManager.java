package com.example.pricesync.event;

import com.example.pricesync.util.Logger;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Registers a keybind (default: unbound — player sets it in Controls) that
 * calls EventManager.runNow(), for `updateMode: "refresh_button"`.
 *
 * NOTE: named "refresh_button" in the spec's UPDATE MODE section, but a
 * keybind is the simplest way to implement a manual trigger without also
 * building custom HUD/GUI widgets. A literal on-screen button can replace
 * this later (e.g. rendered via HudElementRegistry) if wanted.
 */
public final class KeybindManager {

    private static KeyMapping refreshKey;

    private KeybindManager() {}

    public static void register(Runnable onPressed) {
        refreshKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.price_sync.refresh",
                GLFW.GLFW_KEY_UNKNOWN, // unbound by default, set in Controls menu
                "category.price_sync"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (refreshKey.consumeClick()) {
                Logger.debug("Refresh keybind pressed.");
                onPressed.run();
            }
        });
    }
}
