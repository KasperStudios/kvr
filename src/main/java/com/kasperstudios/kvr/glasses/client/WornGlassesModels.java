package com.kasperstudios.kvr.glasses.client;

import com.kasperstudios.kvr.platform.KVRClient;
import com.kasperstudios.kvr.platform.KVRClientProvider;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Flat icon everywhere, 3D only on the head (0020, the Creator's call): {@code models/item/glasses.json}
 * is a plain {@code item/generated} icon; the 3D geometry lives in {@code glasses_worn.json} /
 * {@code hackglasses_worn.json}, baked as extra models and swapped in for {@code ItemDisplayContext.HEAD}
 * by {@code ItemRendererMixin} — the same trick vanilla uses for the trident and the spyglass.
 *
 * <p>1.21.4+ (our 1.21.11 node) does not need any of this: {@code assets/kvr/items/glasses.json} can
 * select the model by {@code display_context} (part of 0022).
 */
public final class WornGlassesModels {

    public static final ResourceLocation GLASSES = GlassStyle.id("item/glasses_worn");
    public static final ResourceLocation HACKGLASSES = GlassStyle.id("item/hackglasses_worn");

    /** Render-thread depth counters (bridge methods may enter twice; reset every client tick). */
    private static int inHeadLayer;
    private static int inArmorLayer;
    private static boolean warnedMissing;

    private WornGlassesModels() {}

    /** Client init, before the first resource load ({@code KVRClientBootstrap}). */
    public static void register(KVRClientProvider provider) {
        provider.registerExtraModel(GLASSES);
        provider.registerExtraModel(HACKGLASSES);
    }

    /** The baked 3D model for this stack on the head, or null to keep the item's own model. */
    @Nullable
    public static Object forHead(ItemStack stack) {
        ResourceLocation model = wornModelId(stack);
        if (model == null) return null;
        Object baked = KVRClient.get().extraModel(model);
        if (baked == null && !warnedMissing) {
            warnedMissing = true;
            com.kasperstudios.kvr.KVR.logger().warn("KVR: 3D glasses model {} is not loaded — nothing is drawn on the head (check the loader log)", model);
        }
        return baked;
    }

    public static boolean isKvrGlasses(ItemStack stack) {
        return wornModelId(stack) != null;
    }

    @Nullable
    private static ResourceLocation wornModelId(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!GlassStyle.NAMESPACE.equals(item.getNamespace())) return null;
        return switch (item.getPath()) {
            case "glasses" -> GLASSES;
            case "hackglasses" -> HACKGLASSES;
            default -> null;
        };
    }

    // --- exactly ONE render path on the head (0021: two visors on screen) ---------------------------
    // The only allowed path: vanilla CustomHeadLayer → ItemRenderer.render(HEAD) → our 3D model.
    // Any other render of KVR glasses in HEAD context (an extra layer, a Fabric ArmorRenderer, …) and any
    // glasses render inside HumanoidArmorLayer is cancelled by ItemRendererMixin.

    public static void enterHeadLayer() { inHeadLayer++; }
    public static void exitHeadLayer() { if (inHeadLayer > 0) inHeadLayer--; }
    public static void enterArmorLayer() { inArmorLayer++; }
    public static void exitArmorLayer() { if (inArmorLayer > 0) inArmorLayer--; }

    /** Client tick start: a render that threw cannot leave a counter stuck for longer than a tick. */
    public static void resetGuards() {
        inHeadLayer = 0;
        inArmorLayer = 0;
    }

    /** False = this item render is a duplicate of the glasses on the head and must not be drawn. */
    public static boolean allowRender(ItemStack stack, net.minecraft.world.item.ItemDisplayContext context) {
        if (!isKvrGlasses(stack)) return true;
        if (inArmorLayer > 0) return false;
        return context != net.minecraft.world.item.ItemDisplayContext.HEAD || inHeadLayer > 0;
    }
}
