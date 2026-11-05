package com.kasperstudios.kvr.spatial.ui;

import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.PlayerView;
import com.kasperstudios.kvr.spatial.anchor.BodyAnchor;
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
 * "Закрепить у плеча" (CONCEPT §4, ADR-003 §6): a small read-only text panel body-anchored next to
 * the right shoulder — walk to the diamonds while the coordinates float beside you. System window,
 * so it does not take a RAM slot. Opened by apps through {@code kvr.pin(title, text)}.
 */
public final class PinnedNote extends SpatialWindow {

    private static final int W = 180;
    private static final int LINE = 10;
    private final Component title;
    private final String text;

    private PinnedNote(String id, String title, String text, Vec3 offset) {
        super(id, Kind.SYSTEM, W, 40, new BodyAnchor(offset));
        this.title = Component.literal(title);
        this.text = text;
        Font font = Minecraft.getInstance().font;
        int lines = Math.min(14, font.split(Component.literal(text), W - 16).size());
        this.height = 28 + Math.max(1, lines) * LINE;
    }

    /** Opens (or replaces) a pinned note to the right of the player, a bit below eye level. */
    public static void pin(String title, String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerView v = PlayerView.capture(mc.gameRenderer.getMainCamera(), 1f);
        Vec3 fwd = v.horizontalLook();
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 offset = new Vec3(0, 1.35, 0).add(right.scale(0.55)).add(fwd.scale(0.45));
        String id = "pin:" + Integer.toHexString(title.hashCode());
        KVRSpatial.windows().all().stream().filter(w -> w.id().equals(id)).findFirst().ifPresent(KVRSpatial.windows()::close);
        KVRSpatial.windows().open(new PinnedNote(id, title, text, offset));
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
        GlassStyle.centered(g, Component.literal("\u00d7"), width - 12, 8, GlassStyle.MUTED);
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(Component.literal(text), W - 16);
        int y = 24;
        for (int i = 0; i < lines.size() && i < 14; i++, y += LINE) g.drawString(font, lines.get(i), 8, y, GlassStyle.TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (GlassStyle.inside(mx, my, width - 18, 4, 14, 14)) {
            KVRSpatial.windows().close(this);
            return true;
        }
        return false;
    }
}
