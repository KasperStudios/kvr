package com.kasperstudios.kvr.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KVRClientBootstrapTest {

    /** SPEC 2.5: RAM T1..T5 → 1/2/4/8/16 app windows; out-of-range tiers are clamped. */
    @Test
    void ramTierToWindowLimit() {
        assertEquals(1, KVRClientBootstrap.ramWindowsForTier(1));
        assertEquals(2, KVRClientBootstrap.ramWindowsForTier(2));
        assertEquals(4, KVRClientBootstrap.ramWindowsForTier(3));
        assertEquals(8, KVRClientBootstrap.ramWindowsForTier(4));
        assertEquals(16, KVRClientBootstrap.ramWindowsForTier(5));
        assertEquals(1, KVRClientBootstrap.ramWindowsForTier(0));
        assertEquals(16, KVRClientBootstrap.ramWindowsForTier(9));
    }
}
