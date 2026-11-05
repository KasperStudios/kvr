package com.kasperstudios.kvr.hack;

import java.util.function.Predicate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * HackGlasses (SPEC 4, CONCEPT §1.4): a secret artifact — not craftable, not in any creative tab,
 * hidden from JEI/REI/EMI (tag {@code c:hidden_from_recipe_viewers}), found with 0.5% in any
 * generated chest or via {@code /give @p kvr:hackglasses}.
 *
 * <p>Part 1: looking at a player who wears KVR glasses shows their floating windows (titles + text)
 * and battery as ghost panels with a green hack grid. Click injection and Rednet sniffing come later.
 *
 * <pre>{@code
 * // KVRContent: register kvr:hackglasses WITHOUT adding it to a creative tab, then
 * HackGlasses.install(stack -> stack.is(HACKGLASSES));
 * KVRLoot.addToEveryChest(() -> HACKGLASSES, HackGlasses.CHEST_CHANCE);
 * }</pre>
 */
public final class HackGlasses {

    public static final float CHEST_CHANCE = 0.005f;
    public static final double SPY_RANGE = 32.0;
    /** Half-angle of the look cone that picks the victim. */
    public static final double SPY_CONE_DEG = 8.0;
    /** Ghost windows stay this long after the hacker looks away. */
    public static final int LINGER_TICKS = 60;
    /** Summaries / spy views are exchanged at 2 Hz. */
    public static final int SYNC_TICKS = 10;

    private static Predicate<ItemStack> isHackGlasses = s -> false;

    private HackGlasses() {}

    public static void install(Predicate<ItemStack> predicate) {
        isHackGlasses = predicate;
    }

    public static boolean isHackGlasses(ItemStack stack) {
        return !stack.isEmpty() && isHackGlasses.test(stack);
    }

    public static boolean isWearing(Player player) {
        return player != null && isHackGlasses(player.getItemBySlot(EquipmentSlot.HEAD));
    }
}
