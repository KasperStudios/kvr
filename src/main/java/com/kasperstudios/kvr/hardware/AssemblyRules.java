package com.kasperstudios.kvr.hardware;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether the six items a player put into the assembly menu build a valid pair of glasses.
 *
 * <p>Pure logic on purpose: the container menu only moves items, the rules live here and are covered by
 * tests, so a dupe or a lost part cannot hide inside slot handling.
 */
public final class AssemblyRules {

    /** Outcome of one assembly attempt: a build when it is valid, otherwise every problem found. */
    public record Result(GlassesBuild build, List<String> problems) {

        public boolean ok() {
            return problems.isEmpty() && build != null;
        }
    }

    private AssemblyRules() {
    }

    /**
     * @param chassis chassis tier, or {@code null} when the chassis slot is empty
     * @param parts   tiers of the modules present in their slots (missing types may be absent or null)
     */
    public static Result evaluate(ComponentTier chassis, Map<ComponentType, ComponentTier> parts) {
        List<String> problems = new ArrayList<>();
        if (chassis == null) {
            problems.add("chassis");
        }

        Map<ComponentType, ComponentTier> installed = new EnumMap<>(ComponentType.class);
        for (ComponentType type : ComponentType.values()) {
            ComponentTier tier = parts == null ? null : parts.get(type);
            if (tier == null) {
                problems.add(type.id());
                continue;
            }
            if (chassis != null && !ChassisRules.canInstall(chassis, tier)) {
                problems.add(type.id() + " tier " + tier.level() + " does not fit chassis tier " + chassis.level());
                continue;
            }
            installed.put(type, tier);
        }

        if (!problems.isEmpty()) {
            return new Result(null, List.copyOf(problems));
        }
        return new Result(new GlassesBuild(chassis, installed), List.of());
    }
}
