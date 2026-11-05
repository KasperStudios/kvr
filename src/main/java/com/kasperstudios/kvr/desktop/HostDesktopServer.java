package com.kasperstudios.kvr.desktop;

import com.kasperstudios.kvr.config.KvrConfig;
import com.kasperstudios.kvr.platform.KVRNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Server half of desktop apps in KVR (kvr-desktopd on the player's PC): the server only decides
 * whether its players may run them; pixels never leave the player's machine (rendered on that client
 * only, always private). Advisory by nature: a modified client can ignore it.
 * Common init: {@code HostDesktopServer.init()}.
 */
public final class HostDesktopServer {

    public static final ResourceLocation QUERY = id("desktop_query");
    public static final ResourceLocation POLICY = id("desktop_policy");

    public record Policy(boolean enabled, int maxWindows) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(enabled);
            buf.writeVarInt(maxWindows);
        }

        public static Policy read(FriendlyByteBuf buf) {
            return new Policy(buf.readBoolean(), buf.readVarInt());
        }
    }

    private HostDesktopServer() {}

    public static void init() {
        KVRNetwork.registerServerReceiver(QUERY, (player, buf) -> {
            var server = player.getServer();
            if (server == null) return;
            server.execute(() -> {
                KvrConfig.Server cfg = KvrConfig.server();
                Policy p = new Policy(cfg.hostDesktopWindows(), cfg.hostDesktopMaxWindows());
                KVRNetwork.sendToPlayer(player, POLICY, p::write);
            });
        });
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }
}
