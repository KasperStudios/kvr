package com.kasperstudios.kvr.voice;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * Optional voice backend for Calls (SPEC 6.3). Simple Voice Chat installs one through its plugin
 * ({@code voice.svc.KvrVoicechatPlugin}, found by SVC itself: Fabric entrypoint {@code "voicechat"},
 * NeoForge annotation {@code @ForgeVoicechatPlugin}). Without SVC the facade stays empty and a call answers
 * {@code NO_VOICE}. Plasmo Voice can plug in later through the same {@link Backend}.
 */
public final class KVRVoice {

    /** What a call needs from a voice mod. Server thread only. */
    public interface Backend {
        String name();

        /** The player has the voice mod and is connected to the voice server right now. */
        boolean connected(UUID player);

        /** A new private (isolated, hidden, password) group; returns its id. */
        UUID createGroup(String name);

        /** Put the player into a group, or out of every group ({@code null}). */
        void join(UUID player, @Nullable UUID group);

        /** The group the player is in now, or null. */
        @Nullable
        UUID groupOf(UUID player);

        /** Delete a group we created (no-op if already gone). */
        void removeGroup(UUID group);
    }

    /** A packet younger than this means "speaking" (SVC sends ~50 packets/s while you talk). */
    static final long SPEAKING_MS = 350;
    private static final Map<UUID, Long> LAST_MIC = new ConcurrentHashMap<>();
    @Nullable private static volatile Backend backend;

    private KVRVoice() {}

    public static void install(@Nullable Backend b) {
        backend = b;
        if (b == null) LAST_MIC.clear();
    }

    public static Optional<Backend> backend() {
        return Optional.ofNullable(backend);
    }

    /** Microphone packet from the voice mod (its own thread). */
    public static void markSpeaking(UUID player) {
        LAST_MIC.put(player, System.currentTimeMillis());
    }

    public static boolean speaking(UUID player) {
        Long t = LAST_MIC.get(player);
        return t != null && System.currentTimeMillis() - t < SPEAKING_MS;
    }

    /** 0030: forget players who are no longer online (the map is filled from the voice mod's thread). */
    public static void retainOnly(java.util.Set<UUID> online) {
        LAST_MIC.keySet().retainAll(online);
    }
}
