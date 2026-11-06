package com.kasperstudios.kvr.desktop.client;

import com.kasperstudios.kvr.config.KvrConfig;
import com.kasperstudios.kvr.desktop.DesktopProtocol;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Clipboard between PC apps in KVR and the real system clipboard (D2). Text only. Client thread only
 * (GLFW clipboard calls must run on the main thread).
 *
 * <ul>
 *   <li>App copies (Ctrl+C, menu "Copy") → the daemon sends CLIPBOARD → {@link #fromApp} puts it into the
 *       system clipboard: paste it in Minecraft chat, a KVR note or any program on the PC.</li>
 *   <li>Player copied something outside → {@link #offer} hands it to the daemon right before the app can
 *       paste: when the keyboard capture starts on a desktop window and on every Ctrl+V / Shift+Insert.</li>
 * </ul>
 * Off with {@code desktop_clipboard_sync = false} (settings → PC apps). Copy/paste between apps inside the
 * daemon works regardless (Wayland handles it, rich formats included).
 */
public final class DesktopClipboard {

    /** Last text that is already on the daemon's side (offered by us or copied by an app): never sent twice. */
    @Nullable private static String known;

    private DesktopClipboard() {}

    public static boolean enabled() {
        return KvrConfig.client().desktopClipboard();
    }

    /** An app copied text. */
    public static void fromApp(String text) {
        if (!enabled() || text == null || text.isEmpty()) return;
        known = text; // the app owns the selection: offering it back would only drop its rich formats
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
    }

    /** The app may paste now: give it the current system clipboard, if it is new to the daemon. */
    public static void offer(@Nullable DesktopBridge bridge) {
        if (!enabled() || bridge == null || bridge.state() != DesktopBridge.State.READY) return;
        if ((bridge.caps() & DesktopProtocol.CAP_CLIPBOARD) == 0) return;
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (text == null || text.isEmpty() || text.equals(known)) return;
        known = text;
        bridge.send(new DesktopProtocol.ClipboardSet(text));
    }

    /** New daemon (or disconnect): it knows nothing yet. */
    public static void reset() {
        known = null;
    }
}
