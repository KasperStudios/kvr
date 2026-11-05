package com.kasperstudios.kvr.mixin.server;

import com.kasperstudios.kvr.power.GlassesPowerTicker;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla never ticks the stack of an item on the ground; overloaded glasses must keep counting there. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void kvr$tickGlassesOnGround(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.isRemoved()) GlassesPowerTicker.tickOnGround(self);
    }
}
