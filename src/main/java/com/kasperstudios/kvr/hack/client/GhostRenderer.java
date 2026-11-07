package com.kasperstudios.kvr.hack.client;

import com.kasperstudios.kvr.hack.HackGlasses;
import com.kasperstudios.kvr.hack.WindowSummary;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The hacker's view: every window of the victim as a ghost panel exactly where the victim sees it,
 * covered by the green hack grid ({@code textures/misc/hack_grid.png}, tiled every 32 GUI px),
 * with "[name] title" above and the window's text dump inside; the victim's battery floats over
 * their head. Same world pass conventions as {@code SpatialRenderer}.
 */
public final class GhostRenderer {

    private static final ResourceLocation GRID = GlassStyle.id("textures/misc/hack_grid.png");
    private static final int GREEN = 0xFF4ADE80;
    private static final int PALE = 0xFFBBF7D0;
    private static final int LINES = 6;
    private static final int CHARS = 46;

    private GhostRenderer() {}

    public static void render(PoseStack pose, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !HackGlasses.isWearing(mc.player)) return;
        Map<UUID, SpyClient.Seen> seen = SpyClient.seen();
        if (seen.isEmpty()) return;

        Vec3 cam = com.kasperstudios.kvr.compat.client.ClientCompat.cameraPos(camera);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Map.Entry<UUID, SpyClient.Seen> e : seen.entrySet()) {
            SpyClient.Seen s = e.getValue();
            for (WindowSummary.Entry w : s.summary().windows()) {
                if (!w.minimized()) ghost(pose, buffers, cam, s.name(), w);
            }
            Player victim = mc.level.getPlayerByUUID(e.getKey());
            if (victim != null) battery(pose, buffers, camera, cam, victim, s.summary().battery());
        }
        RenderCompat.flush(buffers);
    }

    private static void ghost(PoseStack pose, MultiBufferSource buffers, Vec3 cam, String name, WindowSummary.Entry w) {
        SpatialPose p = new SpatialPose(new Vec3(w.x(), w.y(), w.z()), w.yaw(), w.pitch());
        double hw = w.width() / SpatialWindow.PX_PER_METER / 2;
        double hh = w.height() / SpatialWindow.PX_PER_METER / 2;
        Vec3 c = p.center().subtract(cam);
        Vec3 r = p.right().scale(hw);
        Vec3 u = p.up().scale(hh);
        RenderCompat.quad(pose, buffers, GRID, c.subtract(r).add(u), c.subtract(r).subtract(u), c.add(r).subtract(u), c.add(r).add(u),
            0f, 0f, w.width() / 32f, w.height() / 32f, 0xD8FFFFFF);

        Matrix4f m = plane(pose, c.subtract(r).add(u).add(p.normal().scale(0.004)), p.right(), p.up().reverse(), p.normal(), 1.0 / SpatialWindow.PX_PER_METER);
        RenderCompat.text(buffers, m, "[" + name + "] " + w.title(), 2, -11, GREEN);
        List<String> lines = wrap(w.text());
        for (int i = 0; i < lines.size(); i++) RenderCompat.text(buffers, m, lines.get(i), 6, 6 + i * 10, PALE);
    }

    private static void battery(PoseStack pose, MultiBufferSource buffers, Camera camera, Vec3 cam, Player victim, int pct) {
        Vec3 at = victim.getEyePosition().add(0, 0.75, 0).subtract(cam);
        Vec3 right = com.kasperstudios.kvr.compat.client.ClientCompat.cameraLeft(camera).reverse();
        Vec3 down = com.kasperstudios.kvr.compat.client.ClientCompat.cameraUp(camera).reverse();
        Vec3 normal = com.kasperstudios.kvr.compat.client.ClientCompat.cameraLook(camera).reverse();
        String label = "\u26a1 " + pct + "%";
        Matrix4f m = plane(pose, at, right, down, normal, 0.025);
        RenderCompat.text(buffers, m, label, -RenderCompat.textWidth(label) / 2f, 0, pct <= 20 ? 0xFFF43F5E : GREEN);
    }

    /**
     * Matrix mapping GUI pixels onto a world plane: columns right·s, down·s, normal·s, origin at
     * {@code topLeft} (camera-relative). Same handedness as vanilla sign text.
     */
    private static Matrix4f plane(PoseStack pose, Vec3 topLeft, Vec3 right, Vec3 down, Vec3 normal, double s) {
        Matrix4f basis = new Matrix4f(
            (float) (right.x * s), (float) (right.y * s), (float) (right.z * s), 0f,
            (float) (down.x * s), (float) (down.y * s), (float) (down.z * s), 0f,
            (float) (normal.x * s), (float) (normal.y * s), (float) (normal.z * s), 0f,
            (float) topLeft.x, (float) topLeft.y, (float) topLeft.z, 1f);
        return new Matrix4f(pose.last().pose()).mul(basis);
    }

    private static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw;
            while (!line.isEmpty() && out.size() < LINES) {
                int cut = Math.min(CHARS, line.length());
                out.add(line.substring(0, cut));
                line = line.substring(cut);
            }
            if (out.size() >= LINES) break;
        }
        return out;
    }
}
