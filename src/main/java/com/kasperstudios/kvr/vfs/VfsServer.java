package com.kasperstudios.kvr.vfs;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.power.GlassesPower;
import com.kasperstudios.kvr.power.PowerBuild;
import com.kasperstudios.kvr.power.PowerNbt;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Server side of the VFS (Creator decision: files on the server, bound to the glasses). The glasses
 * stack gets a UUID on first use ({@code custom_data → kvr_vfs.id}); files live in
 * {@code <world>/kvr/vfs/<uuid>/} — steal the glasses, steal the files. Only worn glasses with the
 * OS running can be accessed; the quota comes from their Storage tier.
 *
 * <p>Common init: {@code VfsServer.init()} after {@code KVRNetwork} is installed.
 */
public final class VfsServer {

    private static final String STACK_KEY = "kvr_vfs";
    /** Simple flood guard: at most this many requests per player per second. */
    private static final int MAX_PER_SECOND = 40;
    private static final Map<UUID, int[]> RATE = new HashMap<>();

    private VfsServer() {}

    public static void init() {
        KVRNetwork.registerServerReceiver(VfsProtocol.REQUEST, (player, buf) -> {
            VfsProtocol.Request req = VfsProtocol.Request.read(buf);
            MinecraftServer server = player.getServer();
            if (server != null) server.execute(() -> reply(player, handle(player, req)));
        });
    }

    private static void reply(ServerPlayer player, VfsProtocol.Reply r) {
        KVRNetwork.sendToPlayer(player, VfsProtocol.REPLY, r::write);
    }

    /** Store + quota of the glasses this player wears with a running OS (used by the messenger). */
    public record Context(VfsStore store, VfsQuota quota) {}

    public static Optional<Context> context(ServerPlayer player) {
        ItemStack glasses = player.getItemBySlot(EquipmentSlot.HEAD);
        Optional<PowerBuild> build = GlassesPower.build(glasses);
        if (build.isEmpty() || !GlassesPower.active(glasses, player.level().getGameTime())) return Optional.empty();
        return Optional.of(new Context(new VfsStore(root(player.getServer(), glasses)), VfsQuota.forStorageTier(build.get().storage())));
    }

    static VfsProtocol.Reply handle(ServerPlayer player, VfsProtocol.Request req) {
        try {
            rateLimit(player);
            ItemStack glasses = player.getItemBySlot(EquipmentSlot.HEAD);
            Optional<PowerBuild> build = GlassesPower.build(glasses);
            if (build.isEmpty() || !GlassesPower.active(glasses, player.level().getGameTime())) {
                throw new VfsException(VfsException.Code.NO_GLASSES, "worn glasses with a running OS required");
            }
            String path = VfsPath.normalize(req.path());
            boolean write = req.op() == VfsProtocol.Op.WRITE || req.op() == VfsProtocol.Op.DELETE;
            if (req.op() != VfsProtocol.Op.USAGE && !VfsPath.canAccess(path, req.appId(), write)) {
                throw new VfsException(VfsException.Code.DENIED, path);
            }
            VfsStore store = new VfsStore(root(player.getServer(), glasses));
            VfsQuota quota = VfsQuota.forStorageTier(build.get().storage());
            String data = switch (req.op()) {
                case LIST -> {
                    JsonArray arr = new JsonArray();
                    for (VfsStore.Entry e : store.list(path)) {
                        JsonObject o = new JsonObject();
                        o.addProperty("name", e.name());
                        o.addProperty("dir", e.dir());
                        o.addProperty("size", e.size());
                        arr.add(o);
                    }
                    yield arr.toString();
                }
                case READ -> store.read(path);
                case WRITE -> {
                    store.write(path, req.text(), quota);
                    yield "";
                }
                case DELETE -> {
                    store.delete(path);
                    yield "";
                }
                case USAGE -> {
                    VfsStore.Usage u = store.usage();
                    JsonObject o = new JsonObject();
                    o.addProperty("bytes", u.bytes());
                    o.addProperty("files", u.files());
                    o.addProperty("quotaBytes", quota.bytes());
                    o.addProperty("quotaFiles", quota.files() == Integer.MAX_VALUE ? -1 : quota.files());
                    yield o.toString();
                }
            };
            return new VfsProtocol.Reply(req.req(), true, "", data);
        } catch (VfsException e) {
            return new VfsProtocol.Reply(req.req(), false, e.code().name(), e.getMessage());
        }
    }

    private static void rateLimit(ServerPlayer player) throws VfsException {
        int second = player.getServer().getTickCount() / 20;
        int[] r = RATE.computeIfAbsent(player.getUUID(), k -> new int[] {second, 0});
        if (r[0] != second) {
            r[0] = second;
            r[1] = 0;
        }
        if (++r[1] > MAX_PER_SECOND) throw new VfsException(VfsException.Code.RATE_LIMIT, "too many requests");
    }

    /** Folder of these glasses; assigns the UUID on first access. */
    static Path root(MinecraftServer server, ItemStack glasses) {
        CompoundTag t = PowerNbt.readKey(glasses, STACK_KEY);
        String id = PowerNbt.getString(t, "id", "");
        if (id.isEmpty()) {
            id = UUID.randomUUID().toString();
            t.putString("id", id);
            PowerNbt.writeKey(glasses, STACK_KEY, t);
        }
        return server.getWorldPath(LevelResource.ROOT).resolve("kvr").resolve("vfs").resolve(id);
    }
}
