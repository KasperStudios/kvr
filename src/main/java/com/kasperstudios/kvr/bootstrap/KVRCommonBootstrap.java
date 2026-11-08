package com.kasperstudios.kvr.bootstrap;

import com.kasperstudios.kvr.KVR;
import com.kasperstudios.kvr.desktop.HostDesktopServer;
import com.kasperstudios.kvr.hack.SpyRelay;
import com.kasperstudios.kvr.messenger.MessengerServer;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.power.KVRPower;
import com.kasperstudios.kvr.power.PowerServerHandler;
import com.kasperstudios.kvr.power.SetPowerStatePayload;
import com.kasperstudios.kvr.share.PublicWindowRelay;
import com.kasperstudios.kvr.vfs.VfsServer;

/**
 * Every common (both sides, server logic) init of Blocks 3–5 in the one correct order. Call it ONCE from
 * the common entrypoint of each loader, after your own {@code KVRServer.install(...)},
 * {@code KVRNetwork} setup and content registration ({@code GlassesPower.install}, {@code KVRDock.install},
 * {@code HackGlasses.install}, {@code KVRLoot}):
 *
 * <pre>{@code
 * KVRCommonBootstrap.init();
 * }</pre>
 *
 * <b>Remove</b> the individual calls listed below from your entrypoints if you already have them —
 * a channel registered twice may throw in your {@code KVRNetwork}.
 */
public final class KVRCommonBootstrap {

    private static boolean done;

    private KVRCommonBootstrap() {}

    public static synchronized void init() {
        if (done) return;
        done = true;
        KVRPower.init();                       // /kvr power + Block 3 commands
        KVRNetwork.registerServerReceiver(SetPowerStatePayload.CHANNEL, (player, buf) -> {
            SetPowerStatePayload p = SetPowerStatePayload.read(buf); // decode now: the buffer lives only for this call
            var server = com.kasperstudios.kvr.compat.VersionCompat.server(player);
            if (server != null) server.execute(() -> PowerServerHandler.handle(player, p));
        });
        VfsServer.init();                      // kvr:vfs
        com.kasperstudios.kvr.apps.AppsServer.init(); // kvr:apps (0025: .vrapp packages live in the VFS)
        MessengerServer.init();                // kvr:msg_* (after VfsServer: attachments live in the VFS)
        SpyRelay.init();                       // kvr:window_summary, kvr:spy_view
        PublicWindowRelay.init();              // kvr:public_windows (reads SpyRelay's summaries)
        HostDesktopServer.init();              // kvr:desktop_query / kvr:desktop_policy
        com.kasperstudios.kvr.rednet.RednetServer.init(); // kvr:rednet_* (0029; inert without CC: Tweaked)
        com.kasperstudios.kvr.inspect.InspectServer.init(); // 0034a: kvr:inspect_* (block inspector chest slots)
        com.kasperstudios.kvr.voice.CallServer.init();    // kvr:call_* (0029; answers NO_VOICE without Simple Voice Chat)
        // 0026: every S2C channel known on both sides before the first packet (dedicated server!).
        // Agent: add your HackProtocol / HandPoseRelay S2C channels (inject_input, hack_targets, hand_view) here too.
        KVRNetwork.declareS2C(
            com.kasperstudios.kvr.vfs.VfsProtocol.REPLY,
            com.kasperstudios.kvr.apps.AppsProtocol.REPLY,
            com.kasperstudios.kvr.messenger.MessengerProtocol.ACK,
            com.kasperstudios.kvr.messenger.MessengerProtocol.RECEIVE,
            com.kasperstudios.kvr.hack.SpyRelay.SPY_VIEW,
            com.kasperstudios.kvr.share.PublicWindowRelay.VIEW,
            HostDesktopServer.POLICY,
            com.kasperstudios.kvr.rednet.RednetProtocol.RECEIVE,
            com.kasperstudios.kvr.rednet.RednetProtocol.STATE,
            com.kasperstudios.kvr.voice.CallProtocol.STATE,
            com.kasperstudios.kvr.inspect.InspectServer.REPLY);
        KVR.logger().info("KVR common systems ready (power, VFS, apps, messenger, spy/public relays, desktop policy, rednet, calls)");
    }
}
