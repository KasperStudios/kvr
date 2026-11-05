package com.kasperstudios.kvr.power.client;

import com.kasperstudios.kvr.power.GlassesPower;
import com.kasperstudios.kvr.power.PowerBuild;
import com.kasperstudios.kvr.power.PowerData;
import com.kasperstudios.kvr.power.PowerState;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Client view of the worn glasses' power. Reads the synced stack (the server writes the power data
 * every second, vanilla resyncs the slot), so overload and battery are known without extra packets.
 */
public final class PowerClient {

    /** Strobe period: 4 ticks red / 4 ticks normal. */
    private static final int STROBE_TICKS = 4;

    private static Consumer<PowerState> sender = s -> {};

    private PowerClient() {}

    /** {@code state -> KVRNetwork.sendToServer(new SetPowerStatePayload(state))} */
    public static void installSender(Consumer<PowerState> send) {
        sender = send;
    }

    public static void request(PowerState state) {
        sender.accept(state);
    }

    public static ItemStack worn() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        ItemStack head = mc.player.getItemBySlot(EquipmentSlot.HEAD);
        return GlassesPower.isGlasses(head) ? head : ItemStack.EMPTY;
    }

    public record Snapshot(PowerBuild build, PowerData data, long gameTime) {}

    public static Optional<Snapshot> snapshot() {
        Minecraft mc = Minecraft.getInstance();
        ItemStack g = worn();
        if (g.isEmpty() || mc.level == null) return Optional.empty();
        return GlassesPower.build(g).map(b -> new Snapshot(b, GlassesPower.data(g), mc.level.getGameTime()));
    }

    public static boolean overloading() {
        return snapshot().map(s -> s.data().overloading()).orElse(false);
    }

    /** True on the "red" half of the strobe while the worn glasses overload. */
    public static boolean strobeOn() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && overloading() && (mc.level.getGameTime() / STROBE_TICKS) % 2 == 0;
    }
}
