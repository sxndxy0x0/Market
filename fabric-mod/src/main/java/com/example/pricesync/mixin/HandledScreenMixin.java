package com.example.pricesync.mixin;

import com.example.pricesync.event.ScreenOpenCallback;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into every AbstractContainerScreen's init() (Mojang mapping for
 * Yarn's "HandledScreen") — fires once per container GUI open (chest,
 * generic_9x6, custom menus, etc.), including the server's /worth screen.
 * We inject at TAIL so the screen (and its slots) is fully constructed
 * before ScreenOpenCallback listeners run — important since GuiReader reads
 * containerMenu.slots, which must already be populated.
 *
 * NOTE: AbstractContainerScreen.init() is protected in vanilla; Mixin can
 * still target it via @Inject regardless of visibility.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {

    @Inject(method = "init", at = @At("TAIL"))
    private void priceSync$onInit(CallbackInfo ci) {
        AbstractContainerScreen<?> self = (AbstractContainerScreen<?>) (Object) this;
        String title = self.getTitle().getString();
        ScreenOpenCallback.EVENT.invoker().onScreenOpen(self, title);
    }
}
