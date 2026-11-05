package com.kasperstudios.kvr.fabric.client;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRClientProvider;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;

/**
 * Fabric side of {@link KVRClientProvider}. Thin by rule: one Fabric API call per hook.
 * {@code WorldRenderEvents} is referenced by full name inside its branch because Fabric API
 * reworked world rendering events for 1.21.9+.
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
        //? if >=1.21.5 {
        /*KVR.logger().warn("KVR spatial UI: no world render backend for this Minecraft version yet");
        *///?} else if >=1.21 {
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx ->
            callback.render(ctx.matrixStack(), ctx.camera(), ctx.tickCounter().getGameTimeDeltaPartialTick(false)));
        //?} else {
        /*net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx ->
            callback.render(ctx.matrixStack(), ctx.camera(), ctx.tickDelta()));
        *///?}
    }

    @Override
    public void onScreenInit(ScreenInit callback) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
            callback.init(screen, widget -> Screens.getButtons(screen).add(widget)));
    }

    @Override
    public void onHudRender(java.util.function.Consumer<net.minecraft.client.gui.GuiGraphics> callback) {
        // (GuiGraphics, float) on 1.20.x, (GuiGraphics, DeltaTracker) on 1.21 — the lambda fits both
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((graphics, tick) -> callback.accept(graphics));
    }
}
