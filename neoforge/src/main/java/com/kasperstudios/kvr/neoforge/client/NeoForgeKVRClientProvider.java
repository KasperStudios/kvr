package com.kasperstudios.kvr.neoforge.client;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRClientProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge side of {@link KVRClientProvider}. Key mappings are collected and handed to
 * {@link RegisterKeyMappingsEvent} on the mod bus; everything else listens on the game bus.
 * Tick and level-render events changed shape between 20.4, 21.1 and 21.9+, so they are referenced
 * by full name inside their Stonecutter branch.
 *
 * <pre>{@code
 * // in the client-only part of KVRNeoForge (Dist.CLIENT)
 * KVRClient.install(new NeoForgeKVRClientProvider(modBus));
 * KVRSpatial.initClient(config);
 * }</pre>
 */
public final class NeoForgeKVRClientProvider implements KVRClientProvider {

    private final List<KeyMapping> keys = new ArrayList<>();

    public NeoForgeKVRClientProvider(IEventBus modBus) {
        modBus.addListener(RegisterKeyMappingsEvent.class, e -> keys.forEach(e::register));
    }

    @Override
    public void registerKeyMapping(KeyMapping key) {
        keys.add(key);
    }

    @Override
    public void onClientTickStart(Runnable task) {
        //? if >=1.20.5 {
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Pre.class, e -> task.run());
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.TickEvent.ClientTickEvent.class, e -> {
            if (e.phase == net.neoforged.neoforge.event.TickEvent.Phase.START) task.run();
        });
        *///?}
    }

    @Override
    public void onWorldRender(WorldRender callback) {
        //? if >=1.21.5 {
        /*KVR.logger().warn("KVR spatial UI: no world render backend for this Minecraft version yet");
        *///?} else if >=1.21 {
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, e -> {
            if (e.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                callback.render(e.getPoseStack(), e.getCamera(), e.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        });
        //?} else {
        /*NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, e -> {
            if (e.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                callback.render(e.getPoseStack(), e.getCamera(), e.getPartialTick());
            }
        });
        *///?}
    }

    @Override
    public void onScreenInit(ScreenInit callback) {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, e -> callback.init(e.getScreen(), e::addListener));
    }

    @Override
    public void onHudRender(java.util.function.Consumer<net.minecraft.client.gui.GuiGraphics> callback) {
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RenderGuiEvent.Post.class, e -> callback.accept(e.getGuiGraphics()));
    }
}
