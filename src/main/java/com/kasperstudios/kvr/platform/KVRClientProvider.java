package com.kasperstudios.kvr.platform;

import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix4f;

/**
 * Client-side loader hooks the spatial UI needs. Implemented once per loader
 * ({@code fabric/} and {@code neoforge/}), installed through {@link KVRClient}. Keeping the list this
 * short is deliberate: input that has no loader event (raw mouse, scroll, crosshair) goes through
 * common mixins instead.
 */
public interface KVRClientProvider {

    /** Must be called during client init (NeoForge collects them for RegisterKeyMappingsEvent). */
    void registerKeyMapping(KeyMapping key);

    /** Start of every client tick — before vanilla handles key bindings. */
    void onClientTickStart(Runnable task);

    /** Level render, after translucent blocks. */
    void onWorldRender(WorldRender callback);

    /** After a screen's init(); {@code addWidget} adds a renderable, clickable widget to it. */
    void onScreenInit(ScreenInit callback);

    @FunctionalInterface
    interface WorldRender {
        /** @param view camera rotation matrix (vertices are given relative to the camera position) */
        void render(Matrix4f view, Camera camera, float partialTick);
    }

    @FunctionalInterface
    interface ScreenInit {
        void init(Screen screen, Consumer<AbstractWidget> addWidget);
    }
}
