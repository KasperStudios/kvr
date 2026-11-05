package com.kasperstudios.kvr.vfs;

/** SPEC 2.5 Storage column: 256 KB (2 files) / 1 MB (5 files) / 8 MB / 64 MB / 512 MB. */
public record VfsQuota(long bytes, int files) {

    private static final long KB = 1024;
    private static final long MB = 1024 * KB;
    private static final long[] BYTES = {256 * KB, MB, 8 * MB, 64 * MB, 512 * MB};
    /** SPEC fixes the file count only for T1/T2; higher tiers are limited by bytes. */
    private static final int[] FILES = {2, 5, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};

    public static VfsQuota forStorageTier(int tier) {
        if (tier < 1 || tier > 5) throw new IllegalArgumentException("storage tier 1..5, got " + tier);
        return new VfsQuota(BYTES[tier - 1], FILES[tier - 1]);
    }
}
