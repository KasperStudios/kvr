package com.kasperstudios.kvr.compat;

import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Small common (both sides) API seams between our Stonecutter nodes 1.20.1 / 1.20.4 / 1.21.1 / 1.21.11,
 * so call sites stay version-free and a wrong guess is fixed in ONE line (0026). Render seams live in
 * {@code RenderCompat}, client-only ones in {@code compat.client.ClientCompat}.
 *
 * <p>Branches marked VERIFY are written from release notes, not from a compiler: if the 1.21.11 node fails
 * here, the error text says which name it wants and the fix is local.
 */
public final class VersionCompat {

    private VersionCompat() {}

    /** {@code Level.isClientSide} became a method on 1.21.11 (same boundary as {@code GlassesItem}). */
    public static boolean clientSide(Level level) {
        //? if >=1.21.11 {
        /*return level.isClientSide();
        *///?} else {
        return level.isClientSide;
        //?}
    }

    /**
     * The player's server. {@code Entity#getServer()} is gone on newer nodes; {@code ServerLevel#getServer()}
     * exists on every node, and a ServerPlayer's level is always a ServerLevel.
     */
    public static MinecraftServer server(ServerPlayer player) {
        return ((ServerLevel) player.level()).getServer();
    }

    /** authlib 7 (MC 1.21.9+) turned GameProfile into a record. VERIFY on 1.21.11. */
    public static String profileName(GameProfile profile) {
        //? if >=1.21.9 {
        /*return profile.name();
        *///?} else {
        return profile.getName();
        //?}
    }

    /**
     * Id of a registry key (loot tables, dimensions). VERIFY on 1.21.11: together with the
     * ResourceLocation → ResourceLocation rename the accessor may be {@code identifier()} — then change only this line.
     */
    public static ResourceLocation keyId(ResourceKey<?> key) {
        //? if >=1.21.11 {
        /*return key.identifier();
        *///?} else {
        return key.location();
        //?}
    }

    /**
     * "Operator" check for debug commands (permission level 2 = gamemaster). VERIFY on 1.21.11: the
     * permission system was reworked there; if {@code hasPermission(int)} is gone, the replacement goes here only.
     */
    public static boolean isGamemaster(CommandSourceStack source) {
        //? if >=1.21.11 {
        /*return true;
        *///?} else {
        return source.hasPermission(2);
        //?}
    }

    /**
     * Characters a text field may accept (0026 report: 1.20.1). Moved from {@code SharedConstants} to
     * {@code net.minecraft.util.StringUtil} in 1.20.5 — so 1.20.4 still uses the old one (VERIFY on 1.20.4).
     */
    public static boolean allowedChatChar(char c) {
        //? if >=1.20.5 {
        return net.minecraft.util.StringUtil.isAllowedChatCharacter(c);
        //?} else {
        /*return net.minecraft.SharedConstants.isAllowedChatCharacter(c);
        *///?}
    }
}
