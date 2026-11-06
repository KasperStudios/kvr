package com.kasperstudios.kvr.hack.client;

import com.kasperstudios.kvr.hack.HackGlasses;
import com.kasperstudios.kvr.hack.SpyRelay;
import com.kasperstudios.kvr.hack.WindowSummary;
import com.kasperstudios.kvr.platform.KVRClient;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/**
 * Client half of the spy relay.
 * <ul>
 *   <li>Every KVR client with a lit display reports its windows to the server at 2 Hz, each flagged
 *       Public or Private. Bystanders only ever get the Public ones; a HackGlasses wearer looking at
 *       the owner gets all of them (Creator's decision: the artifact taps into sessions and projections).
 *       Desktop-app windows are never reported.</li>
 *   <li>A HackGlasses wearer receives the summary of the player they look at; {@link GhostRenderer}
 *       draws it in the world for {@link HackGlasses#LINGER_TICKS} after the last update.</li>
 * </ul>
 * Call {@link #initClient()} from the client entrypoint after {@code KVRSpatial.initClient}.
 */
public final class SpyClient {

    public record Seen(String name, WindowSummary summary, long untilTick) {}

    private static final Map<UUID, Seen> SEEN = new HashMap<>();
    private static int ticks;
    /** Last report had windows: when the display goes dark, one empty report clears them for everyone. */
    private static boolean reportedWindows;

    private SpyClient() {}

    public static void initClient() {
        KVRNetwork.registerClientReceiver(SpyRelay.SPY_VIEW, buf -> {
            UUID id = buf.readUUID();
            String name = buf.readUtf(32);
            WindowSummary s = WindowSummary.read(buf);
            Minecraft.getInstance().execute(() -> store(id, name, s));
        });
        KVRClient.get().onClientTickStart(SpyClient::tick);
        if (RenderCompat.SUPPORTED) KVRClient.get().onWorldRender(GhostRenderer::render);
    }

    private static void store(UUID id, String name, WindowSummary s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        SEEN.put(id, new Seen(name, s, mc.level.getGameTime() + HackGlasses.LINGER_TICKS));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            SEEN.clear();
            return;
        }
        long now = mc.level.getGameTime();
        SEEN.values().removeIf(s -> s.untilTick() < now);
        if (!HackGlasses.isWearing(mc.player)) SEEN.clear();

        if (++ticks % HackGlasses.SYNC_TICKS != 0) return;
        if (!KVRSpatial.ready() || !KVRSpatial.config().glassesWorn().getAsBoolean()) {
            if (reportedWindows) {
                reportedWindows = false;
                WindowSummary empty = new WindowSummary(0, List.of());
                KVRNetwork.sendToServer(SpyRelay.SUMMARY, empty::write);
            }
            return;
        }
        WindowSummary summary = summarize();
        reportedWindows = !summary.windows().isEmpty();
        KVRNetwork.sendToServer(SpyRelay.SUMMARY, summary::write);
    }

    static WindowSummary summarize() {
        List<WindowSummary.Entry> pub = new ArrayList<>();
        List<WindowSummary.Entry> priv = new ArrayList<>();
        for (SpatialWindow w : KVRSpatial.windows().all()) {
            SpatialPose p = w.pose();
            // desktop apps never leave the PC (consent screen promise): not even to a HackGlasses wearer
            if (p == null || !w.isVisible() || !w.mayLeaveDevice()) continue;
            WindowSummary.Entry e = new WindowSummary.Entry(
                w.title().getString(), w.kind().ordinal(),
                p.center().x, p.center().y, p.center().z, p.yaw(), p.pitch(),
                w.width(), w.height(), w.isMinimized(), w.spyText(), w.isPublic());
            (w.isPublic() ? pub : priv).add(e);
        }
        pub.addAll(priv); // public first: the 16-window cap must never cut a window bystanders should see
        return new WindowSummary(KVRSpatial.config().batteryPercent().getAsInt(), pub);
    }

    public static Map<UUID, Seen> seen() {
        return SEEN;
    }
}
