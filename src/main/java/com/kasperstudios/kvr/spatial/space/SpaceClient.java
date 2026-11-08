package com.kasperstudios.kvr.spatial.space;

import com.kasperstudios.kvr.platform.KVRClientProvider;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.ar.ArRenderer;
import com.kasperstudios.kvr.spatial.ar.ArScanner;
import com.kasperstudios.kvr.spatial.render.RenderCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 0033 — client wiring of Virtual Spaces + AR vision. {@code KVRSpatial.initClient} calls {@link #init} BEFORE it
 * registers {@code SpatialRenderer}, so the cocoon and AR layers are drawn first and the windows blend over them.
 */
public final class SpaceClient {

    private SpaceClient() {}

    public static void init(KVRClientProvider client) {
        client.onClientTickStart(() -> ArScanner.tick(displayOn()));
        client.onWorldRender(SpaceClient::render);
    }

    /** Glasses worn, powered and the display lit (not HUD_OFF). AR vision runs whenever this is true. */
    public static boolean displayOn() {
        return KVRSpatial.ready()
            && Minecraft.getInstance().player != null
            && KVRSpatial.config().glassesWorn().getAsBoolean()
            && !KVRSpatial.windows().hidden();
    }

    private static void render(PoseStack pose, Camera camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (camera.getEntity() == null || mc.options.hideGui || !displayOn()) return;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        CocoonRenderer.render(pose, buffers, camera, partialTick);
        ArRenderer.render(pose, buffers, camera, partialTick);
        RenderCompat.flush(buffers);
    }
}
