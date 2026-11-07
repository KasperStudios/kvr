package com.kasperstudios.kvr.hack.client;

import com.kasperstudios.kvr.hack.HackGlasses;
import com.kasperstudios.kvr.hack.HackProtocol;
import com.kasperstudios.kvr.hack.WindowSummary;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.input.SpatialRaycast;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * Raycasts against ghost windows of spied players and sends click/char injection packets.
 */
public final class GhostRaycast {

    public record GhostHit(
        UUID victim,
        int windowIndex,
        WindowSummary.Entry entry,
        double distance,
        double px,
        double py
    ) {}

    private GhostRaycast() {}

    public static Optional<GhostHit> pick(SpatialRaycast.Ray ray) {
        Map<UUID, SpyClient.Seen> seen = SpyClient.seen();
        if (seen.isEmpty()) return Optional.empty();

        GhostHit best = null;
        for (Map.Entry<UUID, SpyClient.Seen> e : seen.entrySet()) {
            UUID victim = e.getKey();
            var windows = e.getValue().summary().windows();
            for (int i = 0; i < windows.size(); i++) {
                WindowSummary.Entry w = windows.get(i);
                if (w.minimized()) continue;

                SpatialPose pose = new SpatialPose(new Vec3(w.x(), w.y(), w.z()), w.yaw(), w.pitch());
                SpatialRaycast.PlaneHit planeHit = SpatialRaycast.intersectPlane(ray, pose);
                if (planeHit == null) continue;

                double pxPerM = SpatialWindow.PX_PER_METER;
                double px = planeHit.localX() * pxPerM + w.width() / 2.0;
                double py = planeHit.localY() * pxPerM + w.height() / 2.0;

                if (px >= 0 && py >= 0 && px < w.width() && py < w.height()) {
                    if (best == null || planeHit.distance() < best.distance()) {
                        best = new GhostHit(victim, i, w, planeHit.distance(), px, py);
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static void inject(GhostHit hit, HackProtocol.Action action, int buttonOrChar) {
        HackProtocol.InputInjection p = new HackProtocol.InputInjection(
            hit.victim(),
            hit.windowIndex(),
            hit.entry().title(),
            action,
            hit.px(),
            hit.py(),
            buttonOrChar
        );
        KVRNetwork.sendToServer(HackProtocol.C2S_HACK_INPUT, p::write);
    }
}
