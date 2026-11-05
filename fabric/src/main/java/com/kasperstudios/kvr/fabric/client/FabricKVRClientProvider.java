package com.kasperstudios.kvr.fabric.client;

import com.kasperstudios.kvr.platform.KVRClientProvider;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;

/**
 * Fabric side of {@link KVRClientProvider}. Thin by rule: one Fabric API call per hook.
 *
 * <pre>{@code
 * public final class KVRFabricClient implements ClientModInitializer {
 *     public void onInitializeClient() {
 *         KVRClient.install(new FabricKVRClientProvider());
 *         KVRSpatial.initClient(config);
 *     }
 * }
 * }</pre>
 */
public final class FabricKVRClientProvider implements KVRClientProvider {

    @Override
    public void registerKeyMapping(KeyMapping key) {
        KeyBindingHelper.registerKeyBinding(key);
    }

    @Override
    public void onClientTickStart(Runnable task) {
        ClientTickEvents.START_CLIENT_TICK.register(client -> task.run());
    }

    @Override
    public void onWorldRender(WorldRender callback) {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx ->
            callback.render(ctx.positionMatrix(), ctx.camera(), ctx.tickCounter().getGameTimeDeltaPartialTick(false)));
    }

    @Override
    public void onScreenInit(ScreenInit callback) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
            callback.init(screen, widget -> Screens.getButtons(screen).add(widget)));
    }
}
