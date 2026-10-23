package com.kasperstudios.kvr.hardware;

/**
 * The chassis bottleneck: a shell can never host a module above its own tier (ADR-003 3.1).
 */
public final class ChassisRules {

    private ChassisRules() {
    }

    public static boolean canInstall(ComponentTier chassisTier, ComponentTier componentTier) {
        return componentTier.level() <= chassisTier.level();
    }

    /**
     * @throws IllegalArgumentException when the component does not physically fit the chassis
     */
    public static void requireInstallable(ComponentTier chassisTier, ComponentTier componentTier) {
        if (!canInstall(chassisTier, componentTier)) {
            throw new IllegalArgumentException("component " + componentTier + " (" + componentTier.displayName()
                    + ") does not fit a " + chassisTier + " (" + chassisTier.displayName()
                    + ") chassis - upgrade the shell first");
        }
    }
}
