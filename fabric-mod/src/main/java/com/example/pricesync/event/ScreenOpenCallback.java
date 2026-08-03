package com.example.pricesync.event;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Fired right after an AbstractContainerScreen (Mojang mapping for Yarn's
 * "HandledScreen" — any container GUI: chest, generic_9x*, etc.) finishes
 * init. Populated by mixin/HandledScreenMixin.
 *
 * We can't filter to "just the /worth GUI" here since that's server-specific —
 * listeners should check the screen's title text themselves
 * (see EventManager for where that check happens).
 */
public final class ScreenOpenCallback {

    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(
            Listener.class,
            listeners -> (screen, title) -> {
                for (Listener listener : listeners) {
                    listener.onScreenOpen(screen, title);
                }
            }
    );

    private ScreenOpenCallback() {}

    @FunctionalInterface
    public interface Listener {
        /**
         * @param screen the opened container screen
         * @param title  plain-text title of the screen (e.g. "Worth", "Item Prices")
         */
        void onScreenOpen(AbstractContainerScreen<?> screen, String title);
    }
}
