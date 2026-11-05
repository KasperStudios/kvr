package com.kasperstudios.kvr.spatial.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * The only place where the spatial UI touches version-specific render API. Everything else
 * (windows, anchors, input, UI panels) is version-neutral and calls these helpers.
 *
 * <p>Design choice: the world pass goes through vanilla {@link MultiBufferSource} + {@link RenderType}
 * exactly like entity renderers do (camera-relative vertices, the pose stack handed in by the
 * loader event), so there are no raw {@code RenderSystem}/{@code Tesselator} calls to version.
 * Classes that move between versions are referenced by full name inside their branch only.
 *
 * <p>Nodes: 1.20.1, 1.20.4, 1.21.1 — implemented. 1.21.5+ (GPU pipeline rewrite, our 1.21.11 node) —
 * {@link #SUPPORTED} is false and every method is a no-op until the 1.21.11 backend is written;
 * {@code KVRSpatial} then stays inert and the mixin plugin skips the client mixins.
 */
public final class RenderCompat {

    //? if <1.21.5 {
    public static final boolean SUPPORTED = true;
    //?} else {
    /*public static final boolean SUPPORTED = false;
    *///?}

    /** Lightmap value for full brightness (LightTexture.FULL_BRIGHT). */
    private static final int FULL_BRIGHT = 0xF000F0;

    private RenderCompat() {}

    public static Surface surface(ResourceLocation id) {
        return new Surface(id);
    }

    /**
     * Offscreen colour target a window paints into with plain {@link GuiGraphics}. Registered in the
     * TextureManager under {@link #texture()}, so the world pass can use it like any texture.
     */
    public static final class Surface implements AutoCloseable {

        private final ResourceLocation id;
        //? if <1.21.5 {
        private com.mojang.blaze3d.pipeline.TextureTarget target;
        //?}

        private Surface(ResourceLocation id) {
            this.id = id;
        }

        public ResourceLocation texture() {
            return id;
        }

        /** Paint {@code w×h} GUI pixels at {@code scale}× resolution. */
        public void paint(int w, int h, int scale, Consumer<GuiGraphics> painter) {
            //? if <1.21.5 {
            Minecraft mc = Minecraft.getInstance();
            int fw = w * scale;
            int fh = h * scale;
            if (target == null) {
                target = new com.mojang.blaze3d.pipeline.TextureTarget(fw, fh, true, Minecraft.ON_OSX);
                target.setFilterMode(org.lwjgl.opengl.GL11.GL_LINEAR);
                mc.getTextureManager().register(id, new FboTexture(this));
            } else if (target.width != fw || target.height != fh) {
                target.resize(fw, fh, Minecraft.ON_OSX);
            }
            target.setClearColor(0f, 0f, 0f, 0f);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);

            org.joml.Matrix4f oldProjection = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
            com.mojang.blaze3d.vertex.VertexSorting oldSorting = com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(
                new org.joml.Matrix4f().setOrtho(0f, w, h, 0f, 1000f, 21000f), com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);
            //? if >=1.20.5 {
            org.joml.Matrix4fStack mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
            mv.pushMatrix();
            mv.translation(0f, 0f, -11000f);
            //?} else {
            /*PoseStack mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
            mv.pushPose();
            mv.setIdentity();
            mv.translate(0f, 0f, -11000f);
            *///?}
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            try {
                GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
                painter.accept(g);
                g.flush();
            } finally {
                //? if >=1.20.5 {
                mv.popMatrix();
                //?} else {
                /*mv.popPose();
                *///?}
                com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
                mc.getMainRenderTarget().bindWrite(true);
            }
            //?}
        }

        @Override
        public void close() {
            //? if <1.21.5 {
            if (target == null) return;
            Minecraft.getInstance().getTextureManager().release(id);
            target.destroyBuffers();
            target = null;
            //?}
        }
    }

    //? if <1.21.5 {
    /** Exposes the framebuffer's colour attachment as a normal texture; the target owns the GL id. */
    private static final class FboTexture extends net.minecraft.client.renderer.texture.AbstractTexture {
        private final Surface surface;

        FboTexture(Surface surface) {
            this.surface = surface;
        }

        @Override
        public int getId() {
            return surface.target == null ? 0 : surface.target.getColorTextureId();
        }

        @Override
        public void load(net.minecraft.server.packs.resources.ResourceManager manager) {
            // nothing to load: pixels come from the framebuffer
        }

        @Override
        public void releaseId() {
            // the TextureTarget deletes its own texture in destroyBuffers()
        }
    }
    //?}

    /**
     * A texture fed from outside the game (desktop app frames). Uploads straight from a direct
     * {@link java.nio.ByteBuffer} (the mapped shared-memory slot) with {@code glTexSubImage2D} — one copy,
     * CPU → GPU, only the damaged rectangles, BGRA accepted as is. Always linear-filtered.
     */
    public static final class StreamTexture implements AutoCloseable {

        private final ResourceLocation id;
        private int width;
        private int height;
        //? if <1.21.5 {
        private int glId = -1;
        //?}

        public StreamTexture(ResourceLocation id) {
            this.id = id;
        }

        public ResourceLocation texture() { return id; }
        public int width() { return width; }
        public int height() { return height; }
        public boolean ready() { return width > 0; }

        /**
         * @param px     pixels of the frame, row {@code stride} bytes, position 0 = top-left
         * @param rects  damaged rectangles as {x, y, w, h}; ignored when {@code full}
         */
        public void upload(java.nio.ByteBuffer px, int w, int h, int stride, boolean bgra, java.util.List<int[]> rects, boolean full) {
            //? if <1.21.5 {
            com.mojang.blaze3d.systems.RenderSystem.assertOnRenderThread();
            if (glId == -1) {
                glId = com.mojang.blaze3d.platform.TextureUtil.generateTextureId();
                Minecraft.getInstance().getTextureManager().register(id, new IdTexture(this));
            }
            if (w != width || h != height) {
                com.mojang.blaze3d.platform.TextureUtil.prepareImage(glId, w, h);
                width = w;
                height = h;
                full = true;
            }
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(glId);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
            org.lwjgl.opengl.GL11.glTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11.GL_LINEAR);
            int format = bgra ? org.lwjgl.opengl.GL12.GL_BGRA : org.lwjgl.opengl.GL11.GL_RGBA;
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT, 4);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_ROW_LENGTH, stride / 4);
            try {
                if (full || rects.isEmpty()) {
                    sub(px, 0, 0, w, h, format);
                } else {
                    for (int[] r : rects) {
                        int x = Math.max(0, r[0]);
                        int y = Math.max(0, r[1]);
                        int rw = Math.min(w - x, r[2]);
                        int rh = Math.min(h - y, r[3]);
                        if (rw > 0 && rh > 0) sub(px, x, y, rw, rh, format);
                    }
                }
            } finally {
                org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_ROW_LENGTH, 0);
                org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_PIXELS, 0);
                org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_ROWS, 0);
            }
            //?}
        }

        //? if <1.21.5 {
        private static void sub(java.nio.ByteBuffer px, int x, int y, int w, int h, int format) {
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_PIXELS, x);
            org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_UNPACK_SKIP_ROWS, y);
            org.lwjgl.opengl.GL11.glTexSubImage2D(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, x, y, w, h, format, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, px);
        }
        //?}

        @Override
        public void close() {
            //? if <1.21.5 {
            if (glId == -1) return;
            Minecraft.getInstance().getTextureManager().release(id);
            com.mojang.blaze3d.platform.TextureUtil.releaseTextureId(glId);
            glId = -1;
            width = 0;
            height = 0;
            //?}
        }
    }

    //? if <1.21.5 {
    /** GL id owned by a {@link StreamTexture}; vanilla must not touch filtering or delete it. */
    private static final class IdTexture extends net.minecraft.client.renderer.texture.AbstractTexture {
        private final StreamTexture owner;

        IdTexture(StreamTexture owner) {
            this.owner = owner;
        }

        @Override
        public int getId() {
            return owner.glId == -1 ? 0 : owner.glId;
        }

        @Override
        public void setFilter(boolean blur, boolean mipmap) {
            // keep GL_LINEAR: entity render types ask for nearest, desktop text would turn to mush at an angle
        }

        @Override
        public void load(net.minecraft.server.packs.resources.ResourceManager manager) {
            // pixels come from kvr-desktopd
        }

        @Override
        public void releaseId() {
            // StreamTexture.close() deletes the id
        }
    }
    //?}

    /**
     * One full-bright, translucent, double-sided quad in the world. Corners are camera-relative,
     * given top-left → bottom-left → bottom-right → top-right.
     */
    public static void quad(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
                            Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr,
                            float u0, float v0, float u1, float v1, int argb) {
        //? if <1.21.5 {
        VertexConsumer vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(texture));
        Vec3 n = bl.subtract(tl).cross(tr.subtract(tl)).normalize();
        PoseStack.Pose p = pose.last();
        vertex(vc, p, tl, u0, v0, argb, n);
        vertex(vc, p, bl, u0, v1, argb, n);
        vertex(vc, p, br, u1, v1, argb, n);
        vertex(vc, p, tr, u1, v0, argb, n);
        //?}
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose p, Vec3 v, float u, float vv, int argb, Vec3 n) {
        //? if >=1.21 {
        vc.addVertex(p, (float) v.x, (float) v.y, (float) v.z)
            .setColor(argb)
            .setUv(u, vv)
            .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .setLight(FULL_BRIGHT)
            .setNormal(p, (float) n.x, (float) n.y, (float) n.z);
        //?} else {
        /*vc.vertex(p.pose(), (float) v.x, (float) v.y, (float) v.z)
            .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
            .uv(u, vv)
            .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
            .uv2(FULL_BRIGHT)
            .normal(p.normal(), (float) n.x, (float) n.y, (float) n.z)
            .endVertex();
        *///?}
    }

    /** Flush what the world pass queued (call once after all quads). */
    public static void flush(MultiBufferSource.BufferSource buffers) {
        buffers.endBatch();
    }

    /**
     * Text in the world (like sign text): {@code matrix} maps GUI pixels (x right, y down) onto a plane.
     * Full-bright, depth-tested.
     */
    public static void text(MultiBufferSource buffers, org.joml.Matrix4f matrix, String text, float x, float y, int argb) {
        //? if <1.21.5 {
        Minecraft.getInstance().font.drawInBatch(text, x, y, argb, false, matrix, buffers,
            net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, FULL_BRIGHT);
        //?}
    }

    public static int textWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    /** GUI sprite from the gui atlas ({@code kvr:panel} → textures/gui/sprites/panel.png). */
    public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        //? if >=1.21.6 {
        /*g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
        *///?} else if >=1.21.2 {
        /*g.blitSprite(net.minecraft.client.renderer.RenderType::guiTextured, sprite, x, y, w, h);
        *///?} else if >=1.20.2 {
        g.blitSprite(sprite, x, y, w, h);
        //?} else {
        /*nineSlice(g, sprite, x, y, w, h);
        *///?}
    }

    //? if <1.20.2 {
    /*// 1.20.1 has no GUI sprite atlas: draw the same PNG as a plain texture, nine-slicing by hand
    private static void nineSlice(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        ResourceLocation tex = new ResourceLocation(sprite.getNamespace(), "textures/gui/sprites/" + sprite.getPath() + ".png");
        int b = GlassStyle.nineSliceBorder(sprite);
        int s = 16;
        if (b == 0) {
            g.blit(tex, x, y, w, h, 0f, 0f, s, s, s, s);
            return;
        }
        int m = s - 2 * b;
        int iw = w - 2 * b;
        int ih = h - 2 * b;
        g.blit(tex, x, y, b, b, 0f, 0f, b, b, s, s);
        g.blit(tex, x + w - b, y, b, b, s - b, 0f, b, b, s, s);
        g.blit(tex, x, y + h - b, b, b, 0f, s - b, b, b, s, s);
        g.blit(tex, x + w - b, y + h - b, b, b, s - b, s - b, b, b, s, s);
        g.blit(tex, x + b, y, iw, b, b, 0f, m, b, s, s);
        g.blit(tex, x + b, y + h - b, iw, b, b, s - b, m, b, s, s);
        g.blit(tex, x, y + b, b, ih, 0f, b, b, m, s, s);
        g.blit(tex, x + w - b, y + b, b, ih, s - b, b, b, m, s, s);
        g.blit(tex, x + b, y + b, iw, ih, b, b, m, m, s, s);
    }
    *///?}

    public static void pushTranslate(GuiGraphics g, float dx, float dy) {
        //? if >=1.21.6 {
        /*g.pose().pushMatrix();
        g.pose().translate(dx, dy);
        *///?} else {
        g.pose().pushPose();
        g.pose().translate(dx, dy, 0f);
        //?}
    }

    public static void pop(GuiGraphics g) {
        //? if >=1.21.6 {
        /*g.pose().popMatrix();
        *///?} else {
        g.pose().popPose();
        //?}
    }
}
