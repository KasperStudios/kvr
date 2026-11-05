package com.kasperstudios.kvr.hack;

import net.minecraft.world.phys.Vec3;

/** Pure targeting math for the spy view (unit-tested). */
public final class SpyMath {

    private SpyMath() {}

    /**
     * How well {@code target} sits in the look cone: the cosine of the angle (higher = more central),
     * or {@code -2} when it is out of range or outside the cone.
     */
    public static double score(Vec3 eye, Vec3 look, Vec3 target, double range, double coneDeg) {
        Vec3 to = target.subtract(eye);
        double dist = to.length();
        if (dist < 1e-3 || dist > range) return -2;
        double cos = to.scale(1 / dist).dot(look.normalize());
        return cos >= Math.cos(Math.toRadians(coneDeg)) ? cos : -2;
    }
}
