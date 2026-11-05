package com.kasperstudios.kvr.spatial.window;

import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;

/**
 * A window's offscreen texture. Rendered at 2× so text stays crisp close to the face. All
 * framebuffer/projection work is version-specific and lives in {@link RenderCompat.Surface}.
 */
final class WindowSurface implements AutoCloseable {

    private static final int SUPERSAMPLE = 2;
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final RenderCompat.Surface surface;

    WindowSurface(String windowId) {
        String safe = windowId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        this.surface = RenderCompat.surface(GlassStyle.id("window/" + safe + "_" + COUNTER.incrementAndGet()));
    }

    void render(SpatialWindow window, int mouseX, int mouseY, float partialTick) {
        surface.paint(window.width(), window.height(), SUPERSAMPLE, g -> {
            window.renderContent(g, mouseX, mouseY, partialTick);
            window.renderChrome(g, mouseX, mouseY);
        });
    }

    ResourceLocation texture() {
        return surface.texture();
    }

    @Override
    public void close() {
        surface.close();
    }
}
