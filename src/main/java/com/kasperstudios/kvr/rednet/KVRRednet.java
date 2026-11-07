package com.kasperstudios.kvr.rednet;

import com.kasperstudios.kvr.KVR;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Optional bridge to the Rednet of CC: Tweaked (SPEC 6.2). KVR never needs CC: when it is not installed the
 * facade stays empty, the modem slot simply finds no modem item and {@code kvr.rednet.available()} is false.
 *
 * <p>The only code that talks to ComputerCraft is {@code rednet.cc.CcRednetProvider}; it is created by
 * reflection after {@code Class.forName("dan200.computercraft.api.ComputerCraftAPI")} succeeds, so this class,
 * {@link RednetServer} and everything else load fine without CC on the classpath. Since 0030 the provider itself
 * uses only reflection + {@link java.lang.reflect.Proxy}: no CC API jar is needed at compile time on any node.
 */
public final class KVRRednet {

    /** Where a glasses node is right now (refreshed every server tick, read by CC on its computer threads). */
    public interface Placement {
        Level level();
        Vec3 position();
        boolean ender();
        /** Rednet id of these glasses: the provider passes on only our id channel and broadcast (0030). */
        int rednetId();
    }

    /**
     * Raw modem packet for a node. Called on ANY thread (CC computer threads): copy, then hop to the server.
     * {@code distance} in blocks, -1 = from another dimension (ender modem).
     */
    @FunctionalInterface
    public interface Inbound {
        void packet(int channel, int replyChannel, Object payload, double distance);
    }

    /** One pair of glasses attached to the wireless network. */
    public interface Endpoint {
        /** {@code payload} = plain Java values only: Map / String / Double / Boolean (see {@link RednetPayload#toLua}). */
        void transmit(int channel, int replyChannel, Object payload);

        void detach();
    }

    public interface Provider {
        /** Short name for logs, e.g. "CC: Tweaked". */
        String name();

        Endpoint attach(ServerPlayer player, Placement placement, Inbound inbound);
    }

    private static volatile Provider provider;
    private static boolean probed;

    private KVRRednet() {}

    public static boolean available() {
        return provider != null;
    }

    public static Optional<Provider> provider() {
        return Optional.ofNullable(provider);
    }

    /** For another network mod later (or tests). */
    public static void install(Provider p) {
        provider = p;
    }

    /** Called once from {@link RednetServer#init()}: CC on the classpath → our bridge. Never throws. */
    static synchronized void tryInstallComputerCraft() {
        if (probed) return;
        probed = true;
        ClassLoader cl = KVRRednet.class.getClassLoader();
        try {
            Class.forName("dan200.computercraft.api.ComputerCraftAPI", false, cl);
        } catch (ClassNotFoundException | LinkageError e) {
            KVR.logger().info("KVR Rednet: CC: Tweaked not installed, modem slot inactive");
            return;
        }
        try {
            Object p = Class.forName("com.kasperstudios.kvr.rednet.cc.CcRednetProvider", true, cl).getDeclaredConstructor().newInstance();
            install((Provider) p);
            KVR.logger().info("KVR Rednet: bridge to {} ready", ((Provider) p).name());
        } catch (ClassNotFoundException e) {
            // this node was built without the CC API (gradle excludes rednet/cc/** there)
            KVR.logger().warn("KVR Rednet: CC: Tweaked is installed, but this KVR build has no CC bridge for this Minecraft version");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            KVR.logger().warn("KVR Rednet: CC: Tweaked found, bridge failed to start (API changed?): {}", e.toString());
        }
    }
}
