package com.kasperstudios.kvr.spatial.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * 0034a — custom RenderType for the space cocoon shell.
 * Uses standard RenderCompat / entityTranslucentEmissive to remain completely cross-version safe.
 */
public final class CocoonRenderType {

    private CocoonRenderType() {}

    public static void quad(PoseStack pose, MultiBufferSource buffers,
                            Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr, int argb) {
        if ((argb >>> 24) == 0) return;
        RenderCompat.quad(pose, buffers, GlassStyle.GRAB_HANDLE_TEXTURE,
            tl, bl, br, tr, 0.5f, 0.5f, 0.5f, 0.5f, argb);
    }

    public static void quad(PoseStack pose, MultiBufferSource buffers,
                            Vec3 tl, Vec3 bl, Vec3 br, Vec3 tr,
                            int ca, int cb, int cc, int cd) {
        // Fallback to top-left color
        quad(pose, buffers, tl, bl, br, tr, ca);
    }

    public static void line(PoseStack pose, MultiBufferSource buffers,
                            Vec3 a, Vec3 b, double width, int argb) {
        Vec3 d = b.subtract(a);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 side = d.cross(mid);
        double len = side.length();
        if (len < 1e-9) return;
        side = side.scale(width / 2 / len);
        quad(pose, buffers, a.add(side), a.subtract(side), b.subtract(side), b.add(side), argb);
    }
}
