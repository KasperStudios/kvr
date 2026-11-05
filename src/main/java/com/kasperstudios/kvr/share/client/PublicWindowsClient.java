package com.kasperstudios.kvr.share.client;

import com.kasperstudios.kvr.config.KvrConfig;
import com.kasperstudios.kvr.hack.WindowSummary;
import com.kasperstudios.kvr.platform.KVRClient;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.share.PublicWindowRelay;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Client half of public windows: keeps the latest Public windows of each nearby player and lets
 * {@link PublicWindowRenderer} draw them. An owner's windows vanish {@link #LINGER_TICKS} after the
 * last update (they walked away, turned the display off, or switched the window to Private).
 *
 * <p>Call {@link #initClient()} from the client entrypoint after {@code KVRNetwork} is ready.
 * Works without glasses on the viewer. Opt-out: {@code show_public_windows = false}.
 */
public final class PublicWindowsClient {

    public record Seen(String owner, WindowSummary summary, long untilTick) {}

    static final int LINGER_TICKS = 30;
    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    private PublicWindowsClient() {}

    public static void initClient() {
        KVRNetwork.registerClientReceiver(PublicWindowRelay.VIEW, buf -> {
            UUID id = buf.readUUID();
            String name = buf.readUtf(32);
            WindowSummary s = WindowSummary.read(buf);
            Minecraft.getInstance().execute(() -> store(id, name, s));
        });
        KVRClient.get().onClientTickStart(PublicWindowsClient::tick);
        if (RenderCompat.SUPPORTED) KVRClient.get().onWorldRender(PublicWindowRenderer::render);
    }

    private static void store(UUID id, String name, WindowSummary s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || id.equals(mc.player.getUUID())) return;
        if (!KvrConfig.client().showPublicWindows()) return;
        if (s.windows().isEmpty()) SEEN.remove(id);
        else SEEN.put(id, new Seen(name, s, mc.level.getGameTime() + LINGER_TICKS));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            if (!SEEN.isEmpty()) SEEN.clear();
            PublicWindowRenderer.releaseAll();
            return;
        }
        long now = mc.level.getGameTime();
        SEEN.values().removeIf(s -> s.untilTick() < now);
    }

    public static Map<UUID, Seen> seen() {
        return SEEN;
    }
}
