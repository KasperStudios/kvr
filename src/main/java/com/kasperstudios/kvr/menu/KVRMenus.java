package com.kasperstudios.kvr.menu;

import com.kasperstudios.kvr.core.KVRIds;
import com.kasperstudios.kvr.platform.KVRRegistryProvider;
import net.minecraft.world.inventory.MenuType;

/** Menu types KVR registers. Creation goes through the loader facade (vanilla's ctor is private). */
public final class KVRMenus {

    private static MenuType<ChassisAssemblyMenu> chassisAssembly;
    private static boolean registered;

    private KVRMenus() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        chassisAssembly = KVRRegistryProvider.get().menuType(ChassisAssemblyMenu::forPlayer);
        KVRRegistryProvider.get().menu(KVRIds.id("chassis_assembly"), () -> chassisAssembly);
    }

    public static MenuType<ChassisAssemblyMenu> chassisAssembly() {
        if (chassisAssembly == null) {
            throw new IllegalStateException("KVR menus are not registered yet - call KVRMenus.init() first");
        }
        return chassisAssembly;
    }

    /** Test/reload hook. */
    public static void reset() {
        registered = false;
        chassisAssembly = null;
    }
}
