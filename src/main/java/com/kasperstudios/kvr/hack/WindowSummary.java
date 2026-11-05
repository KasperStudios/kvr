package com.kasperstudios.kvr.hack;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * What a KVR client tells the server about its own <b>Public</b> floating windows (private ones are
 * never sent): world pose + size (enough to draw the panel exactly where the owner sees it), title
 * and a short text dump. Used by the HackGlasses spy view and by the public-window relay.
 * Hard limits keep a malicious client from flooding the relay.
 */
public record WindowSummary(int battery, List<Entry> windows) {

    public static final int MAX_WINDOWS = 16;
    public static final int MAX_TITLE = 64;
    public static final int MAX_TEXT = 256;

    public record Entry(String title, int kind, double x, double y, double z, float yaw, float pitch,
                        int width, int height, boolean minimized, String text) {}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(0, Math.min(100, battery)));
        int n = Math.min(MAX_WINDOWS, windows.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = windows.get(i);
            buf.writeUtf(clip(e.title(), MAX_TITLE), MAX_TITLE);
            buf.writeVarInt(e.kind());
            buf.writeDouble(e.x());
            buf.writeDouble(e.y());
            buf.writeDouble(e.z());
            buf.writeFloat(e.yaw());
            buf.writeFloat(e.pitch());
            buf.writeVarInt(Math.max(0, Math.min(2048, e.width())));
            buf.writeVarInt(Math.max(0, Math.min(2048, e.height())));
            buf.writeBoolean(e.minimized());
            buf.writeUtf(clip(e.text(), MAX_TEXT), MAX_TEXT);
        }
    }

    public static WindowSummary read(FriendlyByteBuf buf) {
        int battery = Math.max(0, Math.min(100, buf.readVarInt()));
        int n = Math.min(MAX_WINDOWS, buf.readVarInt());
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(
                buf.readUtf(MAX_TITLE), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readFloat(), buf.readFloat(),
                Math.min(2048, buf.readVarInt()), Math.min(2048, buf.readVarInt()),
                buf.readBoolean(), buf.readUtf(MAX_TEXT)));
        }
        return new WindowSummary(battery, List.copyOf(list));
    }

    static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    /**
     * What a bystander gets of the owner's Public windows: no minimised ones, none farther than
     * {@code range} from the owner (a modified client cannot plant panels across the map), and no
     * battery (that stays a HackGlasses read-out).
     */
    public WindowSummary publicView(double ownerX, double ownerY, double ownerZ, double range) {
        double r2 = range * range;
        List<Entry> out = new ArrayList<>();
        for (Entry e : windows) {
            if (e.minimized()) continue;
            double dx = e.x() - ownerX;
            double dy = e.y() - ownerY;
            double dz = e.z() - ownerZ;
            if (dx * dx + dy * dy + dz * dz <= r2) out.add(e);
        }
        return new WindowSummary(0, List.copyOf(out));
    }
}
