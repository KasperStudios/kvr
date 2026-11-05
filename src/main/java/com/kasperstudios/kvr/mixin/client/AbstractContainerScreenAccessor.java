package com.kasperstudios.kvr.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Container image bounds, needed to crop a detached screen and to place the pin button. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {

    @Accessor("leftPos")
    int kvr$leftPos();

    @Accessor("topPos")
    int kvr$topPos();

    @Accessor("imageWidth")
    int kvr$imageWidth();

    @Accessor("imageHeight")
    int kvr$imageHeight();
}
