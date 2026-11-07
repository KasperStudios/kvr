package com.kasperstudios.kvr.spatial.input;

/**
 * Marker for KVR's own invisible screens that only capture the keyboard for a floating window (0031): the desktop
 * key capture of PC apps, {@code TextInputScreen}. While such a screen is open the mouse wheel must still reach the
 * window under the hand ({@code MouseHandlerMixin}); vanilla screens (inventory, chat …) keep the wheel as before.
 */
public interface SpatialCaptureScreen {
}
