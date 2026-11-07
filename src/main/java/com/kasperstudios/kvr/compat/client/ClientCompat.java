package com.kasperstudios.kvr.compat.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client-only version seams (0026). Kept apart from {@code VersionCompat} so a dedicated server never
 * loads a class whose signatures mention client types.
 *
 * <p>Camera (0026b, 1.21.11 report): {@code Camera}'s getters are private/renamed there. Up to 1.21.4 we read
 * the camera itself. From 1.21.5 the spatial UI has no render backend ({@code RenderCompat.SUPPORTED = false}),
 * so nothing that needs a precise camera runs; the accessors still return meaningful values, derived from the
 * camera ENTITY ({@code Entity} eye position / yaw / pitch exist on every node) — no guessed Camera names.
 */
public final class ClientCompat {

    private ClientCompat() {}

    /** Camera position in the world. */
    public static Vec3 cameraPos(Camera camera) {
        //? if <1.21.5 {
        return camera.getPosition();
        //?} else {
        /*Entity e = viewEntity();
        return e == null ? Vec3.ZERO : e.getEyePosition();
        *///?}
    }

    /** Entity the camera follows; null right after joining a world. */
    @Nullable
    public static Entity cameraEntity(Camera camera) {
        //? if <1.21.5 {
        return camera.getEntity();
        //?} else {
        /*return viewEntity();
        *///?}
    }

    /** Yaw in degrees (Minecraft convention: 0 = south). */
    public static float cameraYaw(Camera camera) {
        //? if <1.21.5 {
        return camera.getYRot();
        //?} else {
        /*Entity e = viewEntity();
        return e == null ? 0f : e.getYRot();
        *///?}
    }

    /** Pitch in degrees (positive = looking down). */
    public static float cameraPitch(Camera camera) {
        //? if <1.21.5 {
        return camera.getXRot();
        //?} else {
        /*Entity e = viewEntity();
        return e == null ? 0f : e.getXRot();
        *///?}
    }

    /** Unit vector the camera looks along. */
    public static Vec3 cameraLook(Camera camera) {
        //? if <1.21.5 {
        return new Vec3(camera.getLookVector());
        //?} else {
        /*return Vec3.directionFromRotation(cameraPitch(camera), cameraYaw(camera));
        *///?}
    }

    /** Unit vector to the camera's left (same as vanilla {@code getLeftVector}). */
    public static Vec3 cameraLeft(Camera camera) {
        //? if <1.21.5 {
        return new Vec3(camera.getLeftVector());
        //?} else {
        /*return Vec3.directionFromRotation(0f, cameraYaw(camera) - 90f); // pitch never tilts vanilla's left vector
        *///?}
    }

    /** Unit vector up from the camera's point of view (same as vanilla {@code getUpVector}). */
    public static Vec3 cameraUp(Camera camera) {
        //? if <1.21.5 {
        return new Vec3(camera.getUpVector());
        //?} else {
        /*Vec3 look = cameraLook(camera);
        return cameraUpFrom(look, Vec3.directionFromRotation(0f, cameraYaw(camera) - 90f));
        *///?}
    }

    //? if >=1.21.5 {
    /*// look × horizontal-left = up (level look: (0,0,1) × (1,0,0) = (0,1,0))
    private static Vec3 cameraUpFrom(Vec3 look, Vec3 horizontalLeft) {
        return look.cross(horizontalLeft).normalize();
    }

    @Nullable
    private static Entity viewEntity() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
    }
    *///?}

    /**
     * Creative-like inventory (vanilla {@code MultiPlayerGameMode#hasInfiniteItems}, renamed on 1.21.11):
     * the player's {@code instabuild} ability is the same flag and exists on every node.
     */
    public static boolean hasInfiniteItems() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getAbilities().instabuild;
    }

    /**
     * The inventory-style live entity (virtual mirror), called with the 1.20.2+ arguments everywhere:
     * box {@code x1,y1 – x2,y2} in GUI px, {@code scale} px per block, {@code yOffset} in blocks, absolute mouse.
     *
     * <p>1.20.1 has only {@code (g, x, y, scale, mouseX, mouseY, entity)}: {@code x,y} = the FEET, mouse = offset
     * from the look-at point. Converted so the entity sits and turns the same as on 1.20.2+, where it is centred
     * in the box ({@code bbHeight / 2 + yOffset} below the centre) and looks from the box centre.
     */
    public static void renderEntityFollowsMouse(GuiGraphics g, int x1, int y1, int x2, int y2, int scale, float yOffset,
                                                float mouseX, float mouseY, LivingEntity entity) {
        //? if >=1.20.2 {
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, yOffset, mouseX, mouseY, entity);
        //?} else {
        /*int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;
        int bottom = cy + (int) ((entity.getBbHeight() / 2f + yOffset) * scale);
        InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, bottom, scale, cx - mouseX, cy - mouseY, entity);
        *///?}
    }

    /**
     * A player's face (skin face + hat layer) in GUI pixels (0030: was one unbranched body that only compiled on
     * 1.20.2–1.21.1). Unknown / offline player → the default skin for that UUID.
     * <ul>
     *   <li>1.20.1: {@code PlayerInfo#getSkinLocation()} + {@code PlayerFaceRenderer.draw(g, ResourceLocation, …)};</li>
     *   <li>1.20.2 … 1.21.4: {@code PlayerInfo#getSkin()} ({@code PlayerSkin}) + {@code PlayerFaceRenderer.draw(g, PlayerSkin, …)};</li>
     *   <li>1.21.5+ (our 1.21.11 node): the spatial UI has no render backend there ({@code RenderCompat.SUPPORTED = false})
     *       and PlayerSkin moved packages in 1.21.9 — a neutral tile, no guessed skin API.</li>
     * </ul>
     */
    public static void drawFace(GuiGraphics g, UUID uuid, int x, int y, int size) {
        //? if >=1.21.5 {
        /*g.fill(x, y, x + size, y + size, 0xFF475569);
        *///?} else if >=1.20.2 {
        net.minecraft.client.multiplayer.ClientPacketListener conn = Minecraft.getInstance().getConnection();
        net.minecraft.client.multiplayer.PlayerInfo info = conn == null ? null : conn.getPlayerInfo(uuid);
        net.minecraft.client.resources.PlayerSkin skin = info != null ? info.getSkin() : net.minecraft.client.resources.DefaultPlayerSkin.get(uuid);
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, skin, x, y, size);
        //?} else {
        /*net.minecraft.client.multiplayer.ClientPacketListener conn = Minecraft.getInstance().getConnection();
        net.minecraft.client.multiplayer.PlayerInfo info = conn == null ? null : conn.getPlayerInfo(uuid);
        net.minecraft.resources.ResourceLocation skin = info != null ? info.getSkinLocation() : net.minecraft.client.resources.DefaultPlayerSkin.getDefaultSkin(uuid);
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, skin, x, y, size);
        *///?}
    }
}
