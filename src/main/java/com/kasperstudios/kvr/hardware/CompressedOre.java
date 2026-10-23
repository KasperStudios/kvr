package com.kasperstudios.kvr.hardware;

/** A compressed UpOre block carries 64 ore, which is what makes the 256-ore step four blocks. */
public final class CompressedOre {

    public static final int ORE_PER_BLOCK = 64;

    private CompressedOre() {
    }

    public static int oreFor(int blocks) {
        if (blocks < 1) {
            throw new IllegalArgumentException("compressed UpOre needs at least one block, got " + blocks);
        }
        return blocks * ORE_PER_BLOCK;
    }

    public static int blocksFor(int ore) {
        if (ore < 0) {
            throw new IllegalArgumentException("ore amount must not be negative, got " + ore);
        }
        return ore / ORE_PER_BLOCK;
    }
}
