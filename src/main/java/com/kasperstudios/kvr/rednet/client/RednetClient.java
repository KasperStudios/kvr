package com.kasperstudios.kvr.rednet.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.kasperstudios.kvr.apps.AppPermissions;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.rednet.RednetPayload;
import com.kasperstudios.kvr.rednet.RednetProtocol;
import com.kasperstudios.kvr.script.ScriptHost;
import net.minecraft.client.Minecraft;

/**
 * Client side of the Rednet bridge: keeps the modem state for {@code kvr.rednet.*} and Settings, hands every
 * incoming message to each running app that may use the network ({@code {type:'rednet'}} event; the prelude
 * also turns protocol {@code kvr_events} into {@code kvr.on(type)} events). Client init: {@code RednetClient.initClient()}.
 */
public final class RednetClient {

    private static final int MAX_PER_SECOND = 10;
    private static volatile RednetProtocol.State state = RednetProtocol.State.NONE;
    private static long rateSecond = -1;
    private static int rateCount;

    private RednetClient() {}

    public static void initClient() {
        KVRNetwork.registerClientReceiver(RednetProtocol.STATE, buf -> {
            RednetProtocol.State s = RednetProtocol.State.read(buf);
            Minecraft.getInstance().execute(() -> state = s);
        });
        KVRNetwork.registerClientReceiver(RednetProtocol.RECEIVE, buf -> {
            RednetProtocol.Receive r = RednetProtocol.Receive.read(buf);
            Minecraft.getInstance().execute(() -> deliver(r));
        });
    }

    public static RednetProtocol.State state() {
        return state;
    }

    /**
     * Thread-safe (script threads). {@code to} = computer id or {@link RednetProtocol#BROADCAST}.
     * False when offline (no CC / no modem), the message is too big or more than 10 per second.
     */
    public static boolean send(int to, String protocol, String json) {
        if (!state.online() || json.length() > RednetPayload.MAX_JSON) return false;
        synchronized (RednetClient.class) {
            long second = System.currentTimeMillis() / 1000L;
            if (second != rateSecond) {
                rateSecond = second;
                rateCount = 0;
            }
            if (++rateCount > MAX_PER_SECOND) return false;
        }
        String p = protocol.length() > 64 ? protocol.substring(0, 64) : protocol;
        RednetProtocol.Send msg = new RednetProtocol.Send(to < 0 ? RednetProtocol.BROADCAST : to, p, json);
        Minecraft.getInstance().execute(() -> KVRNetwork.sendToServer(RednetProtocol.SEND, msg::write));
        return true;
    }

    /** {@code kvr.rednet.status()} as JSON. */
    public static JsonObject statusJson() {
        RednetProtocol.State s = state;
        JsonObject o = new JsonObject();
        o.addProperty("available", s.available());
        o.addProperty("modem", s.modem());
        o.addProperty("ender", s.ender());
        o.addProperty("id", s.modem() ? s.id() : -1);
        return o;
    }

    private static void deliver(RednetProtocol.Receive r) {
        JsonElement message;
        try {
            message = JsonParser.parseString(r.json());
        } catch (JsonParseException e) {
            return;
        }
        JsonObject e = new JsonObject();
        e.addProperty("type", "rednet");
        e.addProperty("sender", r.sender());
        e.addProperty("protocol", r.protocol());
        e.add("message", message);
        e.addProperty("broadcast", r.broadcast());
        e.addProperty("distance", r.distance());
        String event = e.toString();
        for (ScriptHost h : ScriptHost.allRunning()) {
            if (AppPermissions.allowed(h.app().id(), AppPermissions.NETWORK)) h.post(event);
        }
    }
}
