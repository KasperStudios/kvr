package com.kasperstudios.kvr.mixin.client;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Alt-mode keyboard capture: while the hand hovers a window that wants the keyboard (desktop app,
 * JS app with {@code "keyboard": true}), raw GLFW key/char events go to that window instead of
 * vanilla keybinds. Screens are never touched (chat, inventory and our own TextInputScreen work as
 * before). Signatures of 1.20.1–1.21.4; 1.21.9+ switched to KeyEvent/CharacterEvent, but there the
 * mixin plugin skips every client mixin anyway (no render backend yet).
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {

    // 0026: no `window != mc.getWindow().getWindow()` test any more — Window#getWindow() was renamed on 1.21.9+
    // and vanilla itself ignores events of other windows right after our HEAD inject (there is only one).
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void kvr$keyToWindow(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!KVRSpatial.ready() || mc.screen != null) return;
        if (KVRSpatial.input().keyboardKey(key, scancode, action, modifiers)) ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void kvr$charToWindow(long window, int codepoint, int modifiers, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!KVRSpatial.ready() || mc.screen != null) return;
        if (KVRSpatial.input().keyboardChar(codepoint, modifiers)) ci.cancel();
    }
}
