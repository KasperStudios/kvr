package com.kasperstudios.kvr.rednet;

import com.kasperstudios.kvr.power.PowerNbt;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The expansion slot of the glasses (SPEC 6.2, CONCEPT §1: "Wireless Modem / Ender Modem из ComputerCraft
 * превращает очки в сетевой узел Rednet"). Stored on the glasses stack ({@code custom_data → kvr_modem}):
 * {@code kind} (wireless / ender) and a Rednet ID that survives removing and re-inserting the modem.
 *
 * <p>The CC items are recognised by registry id only — no ComputerCraft class is touched here, so this works
 * (and simply finds nothing) without CC installed.
 */
public final class GlassesModem {

    /** Wireless = 64 blocks in one dimension; Ender = unlimited range, across dimensions (as in CC). */
    public enum Kind {
        WIRELESS("wireless_modem_normal"), ENDER("wireless_modem_advanced");

        final String itemPath;

        Kind(String itemPath) {
            this.itemPath = itemPath;
        }
    }

    public record Modem(Kind kind, int id) {}

    static final String KEY = "kvr_modem";
    /** Rednet ids of computers start at 0; glasses take the top of the id-channel space to stay out of their way. */
    static final int ID_MIN = 50_000;
    static final int ID_SPAN = 15_000; // < rednet.MAX_ID_CHANNELS (65500)

    private GlassesModem() {}

    /** Is {@code stack} a CC wireless or ender modem? */
    public static Optional<Kind> kindOf(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"computercraft".equals(id.getNamespace())) return Optional.empty();
        for (Kind k : Kind.values()) {
            if (k.itemPath.equals(id.getPath())) return Optional.of(k);
        }
        return Optional.empty();
    }

    public static Optional<Modem> read(ItemStack glasses) {
        CompoundTag t = PowerNbt.readKey(glasses, KEY);
        String kind = PowerNbt.getString(t, "kind", "");
        if (kind.isEmpty()) return Optional.empty();
        try {
            return Optional.of(new Modem(Kind.valueOf(kind), id(t)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Puts a modem into the slot (the caller consumed the item). */
    public static Modem install(ItemStack glasses, Kind kind) {
        CompoundTag t = PowerNbt.readKey(glasses, KEY);
        int id = id(t);
        if (id < 0) id = ID_MIN + ThreadLocalRandom.current().nextInt(ID_SPAN);
        CompoundTag out = new CompoundTag();
        out.putString("kind", kind.name());
        out.putString("id", Integer.toString(id));
        PowerNbt.writeKey(glasses, KEY, out);
        return new Modem(kind, id);
    }

    /** Rednet id shown in Settings even without a modem; -1 = never had one. */
    public static int rememberedId(ItemStack glasses) {
        return id(PowerNbt.readKey(glasses, KEY));
    }

    /** Takes the modem out; the Rednet id stays on the glasses for the next modem. Empty if none / CC missing. */
    public static ItemStack remove(ItemStack glasses) {
        Optional<Modem> m = read(glasses);
        if (m.isEmpty()) return ItemStack.EMPTY;
        // VERIFY on 1.21.11: Registry#getOptional(ResourceLocation) — if renamed, only this line changes
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(ccId(m.get().kind().itemPath));
        // CC removed from the modpack: keep the slot as it is, the modem must not vanish into thin air
        if (item.isEmpty()) return ItemStack.EMPTY;
        CompoundTag out = new CompoundTag();
        out.putString("id", Integer.toString(m.get().id()));
        PowerNbt.writeKey(glasses, KEY, out);
        return new ItemStack(item.get());
    }

    private static int id(CompoundTag t) {
        try {
            return Integer.parseInt(PowerNbt.getString(t, "id", "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static ResourceLocation ccId(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("computercraft", path);
        //?} else {
        /*return new ResourceLocation("computercraft", path);
        *///?}
    }
}
