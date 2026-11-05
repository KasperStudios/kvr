package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.SpatialPose;
import com.kasperstudios.kvr.spatial.anchor.SpatialAnchor;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.window.SpatialWindow;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;

/**
 * Note window (CONCEPT §4): a small read-only text panel opened by apps through
 * {@code kvr.pin(title, text)} — e.g. coordinates to walk to. System window, so it does not take a
 * RAM slot.
 *
 * <p>Since 0013 it is an ordinary window, not a panel glued to the shoulder: it opens in front of the
 * player, a little to the right and below the eyes, anchored to the Body (it travels with you);
 * World/Body, Public/Private and Lock are in its gear menu, the × closes it.
 */
public final class PinnedNote extends SpatialWindow {

    private static final int W = 180;
    private static final int LINE = 10;
    /** Room for the gear menu (4 rows) above the gear. */
    private static final int MIN_H = 76;
    private final Component title;
    private final String text;

    private PinnedNote(String id, String title, String text, SpatialAnchor anchor) {
        super(id, Kind.SYSTEM, W, MIN_H, anchor);
        this.title = Component.literal(title);
        this.text = text;
        Font font = Minecraft.getInstance().font;
        int lines = Math.min(14, font.split(Component.literal(text), W - 16).size());
        this.height = Math.max(MIN_H, 30 + Math.max(1, lines) * LINE);
    }

    /** Opens (or replaces) a note in front of the player, slightly right and below eye level. */
    public static void pin(String title, String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerView v = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        Vec3 fwd = v.horizontalLook();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 center = v.eye().add(fwd.scale(0.9)).add(right.scale(0.35)).add(0, -0.25, 0);
        SpatialAnchor anchor = SpatialAnchor.of(SpatialAnchor.Mode.BODY, SpatialPose.facing(center, v.eye()), v);
        String id = "pin:" + Integer.toHexString(title.hashCode());
        KVRSpatial.windows().all().stream().filter(w -> w.id().equals(id)).findFirst().ifPresent(KVRSpatial.windows()::close);
        KVRSpatial.windows().open(new PinnedNote(id, title, text, anchor));
    }

    @Override
    public Component title() {
        return title;
    }

    @Override
    public String spyText() {
        return text;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        GlassStyle.panel(g, 0, 0, width, height, false);
        GlassStyle.text(g, title.copy().withStyle(ChatFormatting.BOLD), 8, 8, GlassStyle.ACCENT);
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(Component.literal(text), W - 16);
        int y = 24;
        for (int i = 0; i < lines.size() && i < 14; i++, y += LINE) g.drawString(font, lines.get(i), 8, y, GlassStyle.TEXT, false);
    }
}
