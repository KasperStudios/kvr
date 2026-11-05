package com.kasperstudios.kvr.spatial;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything the spatial UI needs from the rest of the mod, passed in explicitly so the UI layer
 * does not guess how hardware or apps are stored. Wire it from the existing Block 2 code
 * (glasses codec / HardwareTable) and, later, from the QuickJS app runtime (Block 5).
 *
 * @param ramWindows     RAM limit of the worn glasses (1/2/4/8/16, SPEC 2.5)
 * @param glassesWorn    glasses in the helmet / Curios / Trinkets slot and powered on
 * @param batteryPercent 0..100 for the dock status
 * @param apps           installed apps and system actions
 */
public record SpatialConfig(IntSupplier ramWindows, BooleanSupplier glassesWorn, IntSupplier batteryPercent, AppHost apps) {

    public interface AppHost {
        /** Pinned + running apps for the dock, in display order. */
        List<DockItem> dockItems();

        void launch(String appId);

        /** Gesture 1: the launcher with every installed .vrapp (itself a spatial window). */
        void openLauncher();

        /** Gesture 4: dock + virtual mirror + personal space around the player. */
        void enterVirtualSpace();

        /** Gesture 5: a new empty floating screen. */
        void createSpace();
    }

    public record DockItem(String appId, ResourceLocation icon, Component name, boolean running) {}
}
