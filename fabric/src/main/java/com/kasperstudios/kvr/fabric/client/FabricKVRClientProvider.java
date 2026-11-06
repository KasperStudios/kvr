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

    private final java.util.List<net.minecraft.resources.ResourceLocation> extraModels = new java.util.ArrayList<>();

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

    @Override
    public void registerExtraModel(net.minecraft.resources.ResourceLocation id) {
        //? if <1.21.4 {
        // one plugin; it reads the list when models load, so later registrations are included
        if (extraModels.isEmpty()) {
            net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(ctx ->
                ctx.addModels(extraModels.toArray(new net.minecraft.resources.ResourceLocation[0])));
        }
        //?}
        extraModels.add(id);
    }

    @Override
    public Object extraModel(net.minecraft.resources.ResourceLocation id) {
        //? if <1.21 {
        /*return net.fabricmc.fabric.api.client.model.BakedModelManagerHelper.getModel(net.minecraft.client.Minecraft.getInstance().getModelManager(), id);
        *///?} else if <1.21.4 {
        // VERIFY: FabricBakedModelManager#getModel(ResourceLocation), interface-injected into ModelManager by Fabric API
        return net.minecraft.client.Minecraft.getInstance().getModelManager().getModel(id);
        //?} else {
        /*return null; // 1.21.4+: assets/kvr/items/*.json picks the head model by display context
        *///?}
    }
}
