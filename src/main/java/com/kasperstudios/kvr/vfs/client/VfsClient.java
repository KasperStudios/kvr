package com.kasperstudios.kvr.vfs.client;

import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.vfs.VfsProtocol;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Client side of the VFS: sends requests, matches replies by id and hands them to the caller on the
 * client thread. Thread-safe — the script threads call {@link #request} directly.
 * Client init: {@code VfsClient.initClient()}.
 */
public final class VfsClient {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final Map<Integer, Consumer<VfsProtocol.Reply>> PENDING = new ConcurrentHashMap<>();
    private static final int MAX_PENDING = 256;

    private VfsClient() {}

    public static void initClient() {
        KVRNetwork.registerClientReceiver(VfsProtocol.REPLY, buf -> {
            VfsProtocol.Reply r = VfsProtocol.Reply.read(buf);
            Minecraft.getInstance().execute(() -> {
                Consumer<VfsProtocol.Reply> cb = PENDING.remove(r.req());
                if (cb != null) cb.accept(r);
            });
        });
    }

    public static void request(VfsProtocol.Op op, String appId, String path, String text, Consumer<VfsProtocol.Reply> callback) {
        if (PENDING.size() >= MAX_PENDING) {
            callback.accept(new VfsProtocol.Reply(-1, false, "RATE_LIMIT", "too many pending requests"));
            return;
        }
        int id = SEQ.incrementAndGet();
        PENDING.put(id, callback);
        VfsProtocol.Request req = new VfsProtocol.Request(id, op, appId, path, text);
        Minecraft.getInstance().execute(() -> KVRNetwork.sendToServer(VfsProtocol.REQUEST, req::write));
    }
}
