package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.WorldAnchor;
import com.kasperstudios.kvr.spatial.input.SpatialRaycast;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Radial gesture wheel (ADR-001, Alt + RMB with glasses on). Spawns in the world at the hand,
 * 0.7 m along the ray and facing the eye — a short-lived spatial window, not an overlay. Direction
 * from the centre picks one of the five gestures; releasing RMB applies it.
 */
public final class GestureWheel extends SpatialWindow {

    public enum Gesture {
        LAUNCHER("launcher"),
        MINIMIZE_ALL("minimize_all"),
        MINIMIZE_FOCUSED("minimize_focused"),
        VIRTUAL_SPACE("virtual_space"),
        NEW_SPACE("new_space");

        private final String key;

        Gesture(String key) {
            this.key = key;
        }

        public Component label() {
            return Component.translatable("kvr.gesture." + key);
        }

        public ResourceLocation icon() {
            return GlassStyle.id("gesture/" + key);
        }
    }

    private static final int SIZE = 200;
    private static final int RADIUS = 70;
    private static final int BUTTON = 44;
    private static final int DEAD_ZONE = 30;
    private static final double SPAWN_DISTANCE = 0.7;
    private static final double SEG = Math.PI * 2 / Gesture.values().length;

    @Nullable private Gesture hovered;

    private GestureWheel(SpatialPose pose) {
        super("kvr:gesture_wheel", Kind.SYSTEM, SIZE, SIZE, new WorldAnchor(pose));
    }

    public static GestureWheel spawn(SpatialRaycast.Ray ray, PlayerView view) {
        Vec3 center = ray.origin().add(ray.dir().scale(SPAWN_DISTANCE));
        return new GestureWheel(SpatialPose.facing(center, view.eye()));
    }

    /** Pick the sector by angle around the centre (top = gesture 1, clockwise). */
    public void hover(SpatialRaycast.Ray ray) {
        SpatialPose pose = pose();
        SpatialRaycast.PlaneHit h = pose == null ? null : SpatialRaycast.intersectPlane(ray, pose);
        if (h == null) {
            hovered = null;
            return;
        }
        double x = h.localX() * PX_PER_METER;
        double y = h.localY() * PX_PER_METER;
        if (Math.hypot(x, y) < DEAD_ZONE) {
            hovered = null;
            return;
        }
        double a = Math.atan2(y, x) + Math.PI / 2 + SEG / 2;
        a = ((a % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
        hovered = Gesture.values()[(int) (a / SEG) % Gesture.values().length];
    }

    @Nullable
    public Gesture hovered() {
        return hovered;
    }

    @Override
    public Component title() {
        return Component.translatable("kvr.gesture.title");
    }

    @Override
    public boolean hasGrabBar() {
        return false;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        int c = SIZE / 2;
        Gesture[] all = Gesture.values();
        for (int i = 0; i < all.length; i++) {
            double angle = -Math.PI / 2 + i * SEG;
            int bx = (int) Math.round(c + Math.cos(angle) * RADIUS) - BUTTON / 2;
            int by = (int) Math.round(c + Math.sin(angle) * RADIUS) - BUTTON / 2;
            boolean hot = all[i] == hovered;
            GlassStyle.panel(g, bx, by, BUTTON, BUTTON, hot);
            GlassStyle.sprite(g, all[i].icon(), bx + (BUTTON - 16) / 2, by + (BUTTON - 16) / 2, 16, 16);
        }
        Component center = hovered != null ? hovered.label() : Component.translatable("kvr.gesture.hint");
        GlassStyle.centered(g, center, c, c - 4, hovered != null ? GlassStyle.TEXT : GlassStyle.MUTED);
    }
}
