package com.kasperstudios.kvr.spatial.window;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

/**
 * Offscreen framebuffer a window paints into. The window keeps using plain {@link GuiGraphics}
 * (sprites, text, vanilla widgets) in GUI pixels; we only swap the projection to an ortho box of
 * the window's size and bind our target instead of the main one.
 *
 * <p>Rendered at 2× so text stays crisp when the panel is close to the face. Written for the
 * active 1.21.1 branch: {@code TextureTarget}'s constructor and {@code GuiGraphics#flush} are the
 * Stonecutter seams for 1.21.2+.
 */
final class WindowSurface implements AutoCloseable {

    private static final int SUPERSAMPLE = 2;
    private TextureTarget target;

    void render(SpatialWindow window, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int w = window.width();
        int h = window.height();
        ensureSize(w * SUPERSAMPLE, h * SUPERSAMPLE);

        target.setClearColor(0f, 0f, 0f, 0f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, w, h, 0f, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0f, 0f, -11000f);
        RenderSystem.applyModelViewMatrix();

        try {
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            window.renderContent(g, mouseX, mouseY, partialTick);
            g.flush();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private void ensureSize(int w, int h) {
        if (target == null) {
            target = new TextureTarget(w, h, true, Minecraft.ON_OSX);
        } else if (target.width != w || target.height != h) {
            target.resize(w, h, Minecraft.ON_OSX);
        }
    }

    int textureId() {
        return target == null ? 0 : target.getColorTextureId();
    }

    @Override
    public void close() {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
