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
            var server = player.getServer();
            if (server != null) server.execute(() -> PowerServerHandler.handle(player, p));
        });
        VfsServer.init();                      // kvr:vfs
        MessengerServer.init();                // kvr:msg_* (after VfsServer: attachments live in the VFS)
        SpyRelay.init();                       // kvr:window_summary, kvr:spy_view
        PublicWindowRelay.init();              // kvr:public_windows (reads SpyRelay's summaries)
        HostDesktopServer.init();              // kvr:desktop_query / kvr:desktop_policy
        KVR.logger().info("KVR common systems ready (power, VFS, messenger, spy/public relays, desktop policy)");
    }
}
