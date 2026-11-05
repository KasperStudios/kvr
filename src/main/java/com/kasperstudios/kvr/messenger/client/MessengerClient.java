package com.kasperstudios.kvr.messenger.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kasperstudios.kvr.messenger.MessengerProtocol;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.script.ScriptHost;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.ui.ToastPanel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client side of the messenger. Incoming messages always raise a toast; if the Messenger app runs,
 * it gets a {@code {type:'msg'}} event, otherwise the message waits in a small queue that the app
 * reads with {@code kvr.msg.pending()} on start. Client init: {@code MessengerClient.initClient()}.
 */
public final class MessengerClient {

    public static final String APP_ID = "messenger";
    private static final int MAX_QUEUE = 50;
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final Map<Integer, Consumer<MessengerProtocol.Ack>> PENDING = new ConcurrentHashMap<>();
    private static final Deque<MessengerProtocol.Receive> QUEUE = new ArrayDeque<>();

    private MessengerClient() {}

    public static void initClient() {
        KVRNetwork.registerClientReceiver(MessengerProtocol.ACK, buf -> {
            MessengerProtocol.Ack ack = MessengerProtocol.Ack.read(buf);
            Minecraft.getInstance().execute(() -> {
                Consumer<MessengerProtocol.Ack> cb = PENDING.remove(ack.req());
                if (cb != null) cb.accept(ack);
            });
        });
        KVRNetwork.registerClientReceiver(MessengerProtocol.RECEIVE, buf -> {
            MessengerProtocol.Receive msg = MessengerProtocol.Receive.read(buf);
            Minecraft.getInstance().execute(() -> receive(msg));
        });
    }

    /** Thread-safe: called from script threads. */
    public static void send(String to, String text, String attach, Consumer<MessengerProtocol.Ack> callback) {
        int req = SEQ.incrementAndGet();
        PENDING.put(req, callback);
        // writeUtf(max) throws on overflow, so clamp here instead of crashing the network thread
        MessengerProtocol.Send msg = new MessengerProtocol.Send(req, clamp(to, 16), clamp(text, MessengerProtocol.MAX_TEXT), clamp(attach, 128));
        Minecraft.getInstance().execute(() -> KVRNetwork.sendToServer(MessengerProtocol.SEND, msg::write));
    }

    private static String clamp(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static void receive(MessengerProtocol.Receive msg) {
        String body = msg.file().isEmpty() ? msg.text() : msg.text() + " [" + msg.file().substring(msg.file().lastIndexOf('/') + 1) + "]";
        if (KVRSpatial.ready()) {
            KVRSpatial.toasts().push(new ToastPanel.Toast(Component.literal(msg.from()), Component.literal(body),
                GlassStyle.id("gesture/launcher"), 0xFFA78BFA, 4000));
        }
        var app = ScriptHost.running(APP_ID);
        if (app.isPresent()) app.get().post(event(msg).toString());
        else synchronized (QUEUE) {
            QUEUE.addLast(msg);
            while (QUEUE.size() > MAX_QUEUE) QUEUE.removeFirst();
        }
    }

    /** {@code kvr.msg.pending()}: queued messages as a JSON array, then cleared. Thread-safe. */
    public static String drainPending() {
        JsonArray arr = new JsonArray();
        synchronized (QUEUE) {
            for (MessengerProtocol.Receive m : QUEUE) arr.add(event(m));
            QUEUE.clear();
        }
        return arr.toString();
    }

    private static JsonObject event(MessengerProtocol.Receive m) {
        JsonObject e = new JsonObject();
        e.addProperty("type", "msg");
        e.addProperty("from", m.from());
        e.addProperty("text", m.text());
        e.addProperty("file", m.file());
        return e;
    }
}
