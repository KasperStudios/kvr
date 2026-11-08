package com.kasperstudios.kvr.inspect;

import com.kasperstudios.kvr.platform.KVRNetwork;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 0034a — Block Inspector, server half: "how full is this container?". Chest contents are not synced to clients,
 * so the visor asks. The server answers only for a loaded block within {@link #MAX_DISTANCE} of the eyes whose
 * block entity is a vanilla {@link Container}; at most one query per {@link #MIN_INTERVAL_MS} per player.
 * Only counts are sent (used / total slots), never items.
 *
 * <p>Common init: {@code InspectServer.init()} (next to {@code RednetServer.init()} in the common bootstrap).
 */
public final class InspectServer {

    /** C2S: BlockPos. */
    public static final ResourceLocation QUERY = id("inspect_query");
    /** S2C: BlockPos, varint used, varint total. */
    public static final ResourceLocation REPLY = id("inspect_reply");

    public static final double MAX_DISTANCE = 10.0;
    private static final long MIN_INTERVAL_MS = 150;
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    public record Reply(BlockPos pos, int used, int total) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBlockPos(pos);
            buf.writeVarInt(used);
            buf.writeVarInt(total);
        }

        public static Reply read(FriendlyByteBuf buf) {
            return new Reply(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt());
        }
    }

    private InspectServer() {}

    public static void init() {
        KVRNetwork.registerServerReceiver(QUERY, (player, buf) -> {
            BlockPos pos = buf.readBlockPos();
            var server = player.getServer();
            if (server == null) return;
            long now = System.currentTimeMillis();
            Long last = LAST.get(player.getUUID());
            if (last != null && now - last < MIN_INTERVAL_MS) return;
            LAST.put(player.getUUID(), now);
            server.execute(() -> answer(player, pos));
        });
    }

    private static void answer(ServerPlayer player, BlockPos pos) {
        if (!player.isAlive()) return;
        Level level = player.level();
        if (!level.isLoaded(pos)) return;
        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE * MAX_DISTANCE) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container c)) return;
        int total = c.getContainerSize();
        int used = 0;
        for (int i = 0; i < total; i++) if (!c.getItem(i).isEmpty()) used++;
        Reply r = new Reply(pos.immutable(), used, total);
        KVRNetwork.sendIfAccepted(player, REPLY, r::write);
    }

    /** Player left: drop the rate-limit entry (call from the disconnect hook if there is one; harmless if not). */
    public static void forget(UUID player) {
        LAST.remove(player);
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("kvr", path);
        //?} else {
        /*return new ResourceLocation("kvr", path);
        *///?}
    }
}
