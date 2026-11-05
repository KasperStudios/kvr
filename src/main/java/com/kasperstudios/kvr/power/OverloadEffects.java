package com.kasperstudios.kvr.power;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Server side of the "Note 7" (SPEC 2.8, Creator decisions): server-wide raid-horn siren, chat
 * broadcast, and the outcome by where the glasses are at detonation. The red strobe of the victim's
 * windows arrives with the 3b network packet.
 */
public final class OverloadEffects {

    public static final ResourceKey<DamageType> BATTERY_EXPLOSION = ResourceKey.create(Registries.DAMAGE_TYPE, id("battery_explosion"));

    private OverloadEffects() {}

    /** Glasses lying on a dock plate started an overload: same broadcast as glasses on the ground. */
    public static void startAt(ServerLevel level, net.minecraft.core.BlockPos pos) {
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("kvr.overload.broadcast_ground", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        siren(level);
    }

    /** Detonation on a dock plate: like the ground outcome, centred on the plate. */
    public static void detonateAt(ServerLevel level, ItemStack glasses, net.minecraft.world.phys.Vec3 at) {
        List<ItemStack> parts = GlassesPowerTicker.survivors(level, glasses);
        glasses.setCount(0);
        level.explode(null, at.x, at.y, at.z, PowerTable.GROUND_EXPLOSION_POWER, Level.ExplosionInteraction.MOB);
        for (ItemStack part : parts) level.addFreshEntity(new ItemEntity(level, at.x, at.y + 0.5, at.z, part));
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("kvr.overload.detonated_dock", (int) at.x, (int) at.y, (int) at.z).withStyle(ChatFormatting.DARK_RED), false);
    }

    static void start(ServerLevel level, Entity holder, GlassesPowerTicker.Location where) {
        Component msg = where == GlassesPowerTicker.Location.GROUND
            ? Component.translatable("kvr.overload.broadcast_ground", holder.getBlockX(), holder.getBlockY(), holder.getBlockZ())
            : Component.translatable("kvr.overload.broadcast", holder.getDisplayName());
        level.getServer().getPlayerList().broadcastSystemMessage(msg.copy().withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
        siren(level);
    }

    /** Vanilla raid horn for every player on the server, played at their own position. */
    public static void siren(ServerLevel level) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSoundPacket(SoundEvents.RAID_HORN, SoundSource.NEUTRAL, p.getX(), p.getY(), p.getZ(), 64f, 1f, level.random.nextLong()));
        }
    }

    static void detonate(ServerLevel level, ItemStack glasses, Entity holder, GlassesPowerTicker.Location where) {
        List<ItemStack> parts = GlassesPowerTicker.survivors(level, glasses);
        double x = holder.getX();
        double y = holder.getY();
        double z = holder.getZ();

        switch (where) {
            case HEAD -> {
                glasses.setCount(0);
                if (holder instanceof LivingEntity living) living.hurt(damage(level), Float.MAX_VALUE);
                boom(level, x, y + 1.6, z);
            }
            case CARRIED -> {
                glasses.setCount(0);
                if (holder instanceof LivingEntity living) {
                    living.setHealth(Math.min(living.getHealth(), PowerTable.HELD_HEALTH_LEFT));
                    contuse(living);
                }
                boom(level, x, y + 1.0, z);
            }
            case GROUND -> {
                holder.discard();
                level.explode(holder, x, y, z, PowerTable.GROUND_EXPLOSION_POWER, Level.ExplosionInteraction.MOB);
            }
        }

        // spawn after the explosion so the blast does not destroy the surviving parts
        for (ItemStack part : parts) level.addFreshEntity(new ItemEntity(level, x, y + 0.5, z, part));
        level.getServer().getPlayerList().broadcastSystemMessage(
            Component.translatable("kvr.overload.detonated", holder.getDisplayName()).withStyle(ChatFormatting.DARK_RED), false);
    }

    /** Tells the player their Power Off worked. */
    public static void saved(Player player) {
        player.sendSystemMessage(Component.translatable("kvr.overload.saved").withStyle(ChatFormatting.GREEN));
    }

    private static void contuse(LivingEntity e) {
        //? if >=1.21.5 {
        /*e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.NAUSEA, PowerTable.CONTUSION_TICKS, 0));
        e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, PowerTable.CONTUSION_TICKS, 1));
        *///?} else {
        e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.CONFUSION, PowerTable.CONTUSION_TICKS, 0));
        e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, PowerTable.CONTUSION_TICKS, 1));
        //?}
        e.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, PowerTable.DARKNESS_TICKS, 0));
    }

    /** Visual + sound only: a real explosion would also finish the half-heart player. */
    private static void boom(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
        level.playSound(null, x, y, z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 4f, 1f);
    }

    private static DamageSource damage(ServerLevel level) {
        //? if >=1.21.2 {
        /*Holder<DamageType> type = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BATTERY_EXPLOSION);
        *///?} else {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(BATTERY_EXPLOSION);
        //?}
        return new DamageSource(type);
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }
}
