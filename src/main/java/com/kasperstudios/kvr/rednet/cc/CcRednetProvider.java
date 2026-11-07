package com.kasperstudios.kvr.rednet.cc;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.rednet.KVRRednet;
import com.kasperstudios.kvr.rednet.RednetProtocol;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The real bridge to the wireless network of CC: Tweaked (0030), written with reflection only: no CC class
 * is referenced at compile time, so every Stonecutter node compiles without the CC API jar and a node whose CC
 * version renamed something fails at runtime with one log line ({@code KVRRednet} catches it) instead of a
 * compile error or a crash.
 *
 * <p>CC: Tweaked API used (1.20.1 … 1.21.x, package {@code dan200.computercraft.api.network}):
 * <ul>
 *   <li>{@code ComputerCraftAPI.getWirelessNetwork(MinecraftServer)} (older builds: no argument) → {@code PacketNetwork};</li>
 *   <li>{@code PacketNetwork.addReceiver/removeReceiver(PacketReceiver)}, {@code transmitSameDimension(Packet, double)},
 *       {@code transmitInterdimensional(Packet)};</li>
 *   <li>record {@code Packet(int channel, int replyChannel, Object payload, PacketSender sender)};</li>
 *   <li>{@code PacketReceiver}: {@code getLevel, getPosition, getRange, isInterdimensional, receiveSameDimension(Packet, double),
 *       receiveDifferentDimension(Packet)}; {@code PacketSender}: {@code getLevel, getPosition, getSenderID}.</li>
 * </ul>
 * Receiver and sender are {@link Proxy} instances of CC's interfaces. CC calls the receiver on its computer threads;
 * we filter by channel there (our id channel + broadcast) and hand the rest to {@link KVRRednet.Inbound}, which
 * copies the payload and hops to the server thread.
 *
 * <p>Range of a wireless modem follows CC's default config: 64 blocks, growing linearly above Y 96 up to 384 at the
 * build limit (approximated as Y 320). An ender modem reaches everything, in every dimension.
 */
public final class CcRednetProvider implements KVRRednet.Provider {

    private static final String API = "dan200.computercraft.api.ComputerCraftAPI";
    private static final String NET = "dan200.computercraft.api.network.";
    private static final double RANGE = 64;
    private static final double HIGH_RANGE = 384;
    private static final double HIGH_FROM_Y = 96;
    private static final double TOP_Y = 320;

    private final Class<?> receiverType;
    private final Class<?> senderType;
    private final Method getWirelessNetwork;
    private final boolean networkTakesServer;
    private final Constructor<?> packetCtor;
    private final Method packetChannel;
    private final Method packetReply;
    private final Method packetPayload;
    private final Method packetSender;

    public CcRednetProvider() throws ReflectiveOperationException {
        ClassLoader cl = CcRednetProvider.class.getClassLoader();
        Class<?> api = Class.forName(API, true, cl);
        Class<?> packet = Class.forName(NET + "Packet", true, cl);
        receiverType = Class.forName(NET + "PacketReceiver", true, cl);
        senderType = Class.forName(NET + "PacketSender", true, cl);

        Method withServer = null;
        Method noArg = null;
        for (Method m : api.getMethods()) {
            if (!m.getName().equals("getWirelessNetwork")) continue;
            if (m.getParameterCount() == 1) withServer = m;
            else if (m.getParameterCount() == 0) noArg = m;
        }
        if (withServer == null && noArg == null) throw new NoSuchMethodException(API + ".getWirelessNetwork");
        getWirelessNetwork = withServer != null ? withServer : noArg;
        networkTakesServer = withServer != null;

        Constructor<?> ctor = null;
        for (Constructor<?> c : packet.getConstructors()) {
            if (c.getParameterCount() == 4) ctor = c;
        }
        if (ctor == null) throw new NoSuchMethodException(packet.getName() + "(int, int, Object, PacketSender)");
        packetCtor = ctor;
        packetChannel = packet.getMethod("channel");
        packetReply = packet.getMethod("replyChannel");
        packetPayload = packet.getMethod("payload");
        packetSender = packet.getMethod("sender");
    }

    @Override
    public String name() {
        return "CC: Tweaked";
    }

    @Override
    public KVRRednet.Endpoint attach(ServerPlayer player, KVRRednet.Placement placement, KVRRednet.Inbound inbound) {
        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        Object network = invoke(getWirelessNetwork, null, networkTakesServer ? new Object[] {server} : new Object[0]);
        String senderId = "kvr_glasses:" + player.getUUID();

        Object sender = proxy(senderType, (self, m, args) -> switch (m.getName()) {
            case "getLevel" -> placement.level();
            case "getPosition" -> placement.position();
            case "getSenderID" -> senderId;
            default -> fallback(self, m, args);
        });

        Object receiver = proxy(receiverType, (self, m, args) -> switch (m.getName()) {
            case "getLevel" -> placement.level();
            case "getPosition" -> placement.position();
            case "getRange" -> range(placement);
            case "isInterdimensional" -> placement.ender();
            case "receiveSameDimension" -> {
                receive(args[0], sender, placement, inbound, ((Number) args[1]).doubleValue());
                yield null;
            }
            case "receiveDifferentDimension" -> {
                receive(args[0], sender, placement, inbound, -1);
                yield null;
            }
            default -> fallback(self, m, args);
        });

        Method add = method(network, "addReceiver", 1);
        Method remove = method(network, "removeReceiver", 1);
        Method same = method(network, "transmitSameDimension", 2);
        Method inter = method(network, "transmitInterdimensional", 1);
        invoke(add, network, receiver);

        return new KVRRednet.Endpoint() {
            @Override
            public void transmit(int channel, int replyChannel, Object payload) {
                Object p = invokeCtor(channel, replyChannel, payload, sender);
                if (placement.ender()) invoke(inter, network, p);
                else invoke(same, network, p, range(placement));
            }

            @Override
            public void detach() {
                invoke(remove, network, receiver);
            }
        };
    }

    // --- receiving (CC computer threads) ----------------------------------------------------------

    private void receive(Object packet, Object ownSender, KVRRednet.Placement placement, KVRRednet.Inbound inbound, double distance) {
        try {
            if (invoke(packetSender, packet) == ownSender) return; // our own transmission (or its repeat)
            int channel = ((Number) invoke(packetChannel, packet)).intValue();
            int id = placement.rednetId();
            if (channel != RednetProtocol.CHANNEL_BROADCAST && channel != id % RednetProtocol.MAX_ID_CHANNELS) return;
            int reply = ((Number) invoke(packetReply, packet)).intValue();
            inbound.packet(channel, reply, invoke(packetPayload, packet), distance);
        } catch (RuntimeException e) {
            // never throw into CC's computer thread
            KVR.logger().debug("KVR Rednet: dropped packet: {}", e.toString());
        }
    }

    /** CC default config: 64 blocks, rising above Y 96 to 384 at the top of the world. */
    static double range(KVRRednet.Placement placement) {
        if (placement.ender()) return Integer.MAX_VALUE;
        double y = placement.position().y;
        if (y <= HIGH_FROM_Y) return RANGE;
        double t = Math.min(1, (y - HIGH_FROM_Y) / (TOP_Y - HIGH_FROM_Y));
        return RANGE + (HIGH_RANGE - RANGE) * t;
    }

    // --- reflection helpers -----------------------------------------------------------------------

    private Object invokeCtor(int channel, int reply, Object payload, Object sender) {
        try {
            return packetCtor.newInstance(channel, reply, payload, sender);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("CC Packet rejected: " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("CC Packet constructor: " + e, e);
        }
    }

    private static Object invoke(Method m, Object target, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException r) throw r;
            if (c instanceof Error err) throw err;
            throw new IllegalStateException(m.getName() + ": " + c, c);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(m.getName() + ": " + e, e);
        }
    }

    /** Public method by name and arity on the network's interfaces (the implementation class may be hidden). */
    private static Method method(Object target, String name, int params) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                for (Method m : i.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == params) return m;
                }
            }
        }
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new IllegalStateException("CC PacketNetwork has no " + name + "/" + params);
    }

    private static Object proxy(Class<?> type, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    /** equals/hashCode/toString by identity (CC keeps receivers in a set), defaults for the rest. */
    private static Object fallback(Object self, Method m, Object[] args) throws Throwable {
        switch (m.getName()) {
            case "equals" -> {
                return args != null && args.length == 1 && self == args[0];
            }
            case "hashCode" -> {
                return System.identityHashCode(self);
            }
            case "toString" -> {
                return "KVR glasses node@" + Integer.toHexString(System.identityHashCode(self));
            }
            default -> {}
        }
        if (m.isDefault()) return InvocationHandler.invokeDefault(self, m, args);
        Class<?> r = m.getReturnType();
        if (r == boolean.class) return false;
        if (r == double.class) return 0d;
        if (r == float.class) return 0f;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        return null;
    }
}
