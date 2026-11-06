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

    private WornGlassesModels() {}

    /** Client init, before the first resource load ({@code KVRClientBootstrap}). */
    public static void register(KVRClientProvider provider) {
        provider.registerExtraModel(GLASSES);
        provider.registerExtraModel(HACKGLASSES);
    }

    /** The baked 3D model for this stack on the head, or null to keep the item's own model. */
    @Nullable
    public static Object forHead(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!GlassStyle.NAMESPACE.equals(item.getNamespace())) return null;
        ResourceLocation model = switch (item.getPath()) {
            case "glasses" -> GLASSES;
            case "hackglasses" -> HACKGLASSES;
            default -> null;
        };
        return model == null ? null : KVRClient.get().extraModel(model);
    }
}
