package com.kasperstudios.kvr.neoforge;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.platform.KVRNetwork;
import io.netty.buffer.Unpooled;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;

/**
 * NeoForge implementation of {@link KVRNetwork.Provider} for all three NeoForge nodes (0026 — REFERENCE:
 * this tree never had your NeoForge provider; compare with yours, keep whichever already works on 21.1, and
 * take the 20.4 / 21.11 branches and {@link #declareS2C} from here).
 *
 * <p>NeoForge only accepts payload types registered in its registration event (mod bus), on BOTH sides —
 * also on a dedicated server, where client receivers are never registered. So every channel is collected
 * first (server receivers, client receivers, {@code KVRNetwork.declareS2C}) and registered once, bidirectional,
 * when the event fires. Everything is called from the mod constructor ({@code KVRCommonBootstrap.init()} /
 * {@code KVRNeoForgeClient.init}), i.e. before the event. A channel added later is a bug and is logged.
 *
 * <pre>{@code
 * KVRNetwork.install(new NeoForgeKVRNetworkProvider(modBus));   // before KVRCommonBootstrap.init()
 * }</pre>
 * Packets carry raw bytes (our protocols encode/decode {@code FriendlyByteBuf} themselves).
 */
public final class NeoForgeKVRNetworkProvider implements KVRNetwork.Provider {

    private static final String VERSION = "1";

    private final Set<ResourceLocation> channels = new LinkedHashSet<>();
    private final Map<ResourceLocation, KVRNetwork.PacketHandler> clientHandlers = new ConcurrentHashMap<>();
    private final Map<ResourceLocation, KVRNetwork.ServerPacketHandler> serverHandlers = new ConcurrentHashMap<>();
    private volatile boolean registered;

    public NeoForgeKVRNetworkProvider(IEventBus modBus) {
        //? if >=1.20.5 {
        modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class, e -> {
            net.neoforged.neoforge.network.registration.PayloadRegistrar r = e.registrar(VERSION);
            synchronized (channels) {
                for (ResourceLocation ch : channels) {
                    var type = new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload>(ch);
                    r.playBidirectional(type, RawPayload.codec(type), (payload, ctx) -> dispatch(ch, payload.data(),
                        ctx.flow() == net.minecraft.network.protocol.PacketFlow.SERVERBOUND,
                        ctx.player() instanceof ServerPlayer sp ? sp : null));
                }
                registered = true;
            }
        });
        //?} else {
        /*modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent.class, e -> {
            var r = e.registrar("kvr").versioned(VERSION);
            synchronized (channels) {
                for (ResourceLocation ch : channels) {
                    r.play(ch, buf -> new RawPayload(ch, readBytes(buf)), h -> h
                        .client((payload, ctx) -> dispatch(ch, payload.data(), false, null))
                        .server((payload, ctx) -> dispatch(ch, payload.data(), true,
                            ctx.player().orElse(null) instanceof ServerPlayer sp ? sp : null)));
                }
                registered = true;
            }
        });
        *///?}
    }

    // --- payload ---------------------------------------------------------------------------------

    //? if >=1.20.5 {
    public record RawPayload(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> type, byte[] data)
        implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        static net.minecraft.network.codec.StreamCodec<FriendlyByteBuf, RawPayload> codec(
            net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<RawPayload> type) {
            return net.minecraft.network.protocol.common.custom.CustomPacketPayload.codec(
                (p, buf) -> buf.writeByteArray(p.data),
                buf -> new RawPayload(type, buf.readByteArray()));
        }
    }

    private static RawPayload payload(ResourceLocation ch, byte[] data) {
        return new RawPayload(new net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<>(ch), data);
    }
    //?} else {
    /*// 1.20.4: vanilla CustomPacketPayload = write(buf) + id()
    public record RawPayload(ResourceLocation id, byte[] data) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        @Override
        public void write(FriendlyByteBuf buf) {
            buf.writeByteArray(data);
        }
    }

    private static byte[] readBytes(FriendlyByteBuf buf) {
        return buf.readByteArray();
    }

    private static RawPayload payload(ResourceLocation ch, byte[] data) {
        return new RawPayload(ch, data);
    }
    *///?}

    // --- KVRNetwork.Provider ---------------------------------------------------------------------

    @Override
    public void sendToServer(ResourceLocation channel, Consumer<FriendlyByteBuf> encoder) {
        RawPayload p = payload(channel, encode(encoder));
        //? if >=1.21.5 {
        /*// VERIFY on 21.11: client-side sending moved to ClientPacketDistributor
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(p);
        *///?} else if >=1.20.5 {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(p);
        //?} else {
        /*net.neoforged.neoforge.network.PacketDistributor.SERVER.noArg().send(p);
        *///?}
    }

    @Override
    public void sendToPlayer(ServerPlayer player, ResourceLocation channel, Consumer<FriendlyByteBuf> encoder) {
        RawPayload p = payload(channel, encode(encoder));
        //? if >=1.20.5 {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, p);
        //?} else {
        /*net.neoforged.neoforge.network.PacketDistributor.PLAYER.with(player).send(p);
        *///?}
    }

    @Override
    public void registerClientReceiver(ResourceLocation channel, KVRNetwork.PacketHandler handler) {
        clientHandlers.put(channel, handler);
        add(channel);
    }

    @Override
    public void registerServerReceiver(ResourceLocation channel, KVRNetwork.ServerPacketHandler handler) {
        serverHandlers.put(channel, handler);
        add(channel);
    }

    @Override
    public void declareS2C(ResourceLocation channel) {
        add(channel);
    }

    private void add(ResourceLocation channel) {
        synchronized (channels) {
            if (channels.add(channel) && registered) {
                KVR.logger().error("KVR network: channel {} added after payload registration — call it from the mod constructor", channel);
            }
        }
    }

    /** Our receivers decode at once and hop to their own thread, so this may run on the network or main thread. */
    private void dispatch(ResourceLocation ch, byte[] data, boolean serverbound, ServerPlayer player) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        if (serverbound) {
            KVRNetwork.ServerPacketHandler h = serverHandlers.get(ch);
            if (h != null && player != null) h.handle(player, buf);
        } else {
            KVRNetwork.PacketHandler h = clientHandlers.get(ch);
            if (h != null) h.handle(buf);
        }
    }

    private static byte[] encode(Consumer<FriendlyByteBuf> encoder) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        encoder.accept(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
