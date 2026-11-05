package com.kasperstudios.kvr.neoforge.client;

import com.kasperstudios.kvr.platform.KVRClientProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge side of {@link KVRClientProvider}. Key mappings are collected and handed to
 * {@link RegisterKeyMappingsEvent} on the mod bus; everything else listens on the game bus.
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
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, e -> task.run());
    }

    @Override
    public void onWorldRender(WorldRender callback) {
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, e -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                callback.render(e.getModelViewMatrix(), e.getCamera(), e.getPartialTick().getGameTimeDeltaPartialTick(false));
            }
        });
    }

    @Override
    public void onScreenInit(ScreenInit callback) {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Init.Post.class, e -> callback.init(e.getScreen(), e::addListener));
    }
}
