package com.kasperstudios.kvr.voice.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kasperstudios.kvr.apps.AppPermissions;
import com.kasperstudios.kvr.platform.KVRNetwork;
import com.kasperstudios.kvr.script.ScriptHost;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import com.kasperstudios.kvr.spatial.render.GlassStyle;
import com.kasperstudios.kvr.spatial.ui.ToastPanel;
import com.kasperstudios.kvr.voice.CallProtocol;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client side of Calls: the latest personal call state, the native {@link CallWindow} (opens by itself when a
 * call rings or starts) and {@code {type:'call'}} events for running apps with the {@code network} permission
 * ({@code kvr.calls.on}). Client init: {@code CallClient.initClient()}.
 */
public final class CallClient {

    private static volatile CallProtocol.State state = CallProtocol.State.IDLE;
    private static volatile long activeSince;
    private static volatile long endedAt;

    private CallClient() {}

    public static void initClient() {
        KVRNetwork.registerClientReceiver(CallProtocol.STATE, buf -> {
            CallProtocol.State s = CallProtocol.State.read(buf);
            Minecraft.getInstance().execute(() -> apply(s));
        });
    }

    public static CallProtocol.State state() {
        return state;
    }

    /** Wall-clock ms when the current call became ACTIVE (for the timer). */
    public static long activeSince() {
        return activeSince;
    }

    public static long endedAt() {
        return endedAt;
    }

    // --- requests (any thread) -----------------------------------------------------------------

    /** Call one or more players (or add them if a call is already going on). */
    public static void call(List<String> names) {
        boolean inCall = state.inCall();
        send(new CallProtocol.Request(inCall ? CallProtocol.Op.ADD : CallProtocol.Op.INVITE, state.callId(), clamp(names)));
    }

    public static void accept() {
        send(new CallProtocol.Request(CallProtocol.Op.ACCEPT, state.callId(), List.of()));
    }

    public static void decline() {
        send(new CallProtocol.Request(CallProtocol.Op.DECLINE, state.callId(), List.of()));
    }

    public static void hangup() {
        send(new CallProtocol.Request(CallProtocol.Op.HANGUP, state.callId(), List.of()));
    }

    private static List<String> clamp(List<String> names) {
        return names.stream().filter(n -> n != null && !n.isEmpty()).map(n -> n.length() > 16 ? n.substring(0, 16) : n)
            .limit(CallProtocol.MAX_MEMBERS).toList();
    }

    private static void send(CallProtocol.Request r) {
        Minecraft.getInstance().execute(() -> KVRNetwork.sendToServer(CallProtocol.OP, r::write));
    }

    // --- state (client thread) -----------------------------------------------------------------

    private static void apply(CallProtocol.State s) {
        CallProtocol.State prev = state;
        state = s;
        boolean changed = prev.callId() != s.callId() || prev.status() != s.status();
        long now = System.currentTimeMillis();
        if (s.status() == CallProtocol.Status.ACTIVE && changed) activeSince = now;
        if (s.status() == CallProtocol.Status.ENDED) endedAt = now;
        if (changed && KVRSpatial.ready()) {
            switch (s.status()) {
                case RINGING_IN -> {
                    toast(Component.translatable("kvr.calls.incoming"), Component.literal(s.caller()));
                    CallWindow.open();
                }
                case RINGING_OUT, ACTIVE -> CallWindow.open();
                case ENDED, IDLE -> {}
            }
        }
        // errors without a call (callId 0) repeat with the same id, so they are toasted every time;
        // in a running call a non-empty reason is a note for us (e.g. "BUSY" after adding someone)
        if (KVRSpatial.ready() && !s.reason().isEmpty() && (changed || s.callId() == 0 || s.inCall())) {
            toast(Component.translatable(s.inCall() ? "kvr.calls.note" : "kvr.calls.ended"), reasonText(s.reason()));
        }
        String event = event(s).toString();
        for (ScriptHost h : ScriptHost.allRunning()) {
            if (AppPermissions.allowed(h.app().id(), AppPermissions.NETWORK)) h.post(event);
        }
    }

    private static void toast(Component title, Component text) {
        KVRSpatial.toasts().push(new ToastPanel.Toast(title, text, GlassStyle.id("gesture/launcher"), 0xFF4ADE80, 5000));
    }

    /** "PEER_NO_GLASSES" → lang key kvr.calls.reason.peer_no_glasses. */
    public static Component reasonText(String reason) {
        return Component.translatable("kvr.calls.reason." + reason.toLowerCase(Locale.ROOT));
    }

    /** {@code kvr.calls.state()} / the {@code {type:'call'}} event. */
    public static JsonObject event(CallProtocol.State s) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "call");
        o.addProperty("id", s.callId());
        o.addProperty("status", s.status().name().toLowerCase(Locale.ROOT));
        o.addProperty("caller", s.caller());
        o.addProperty("reason", s.reason());
        JsonArray members = new JsonArray();
        for (CallProtocol.Member m : s.members()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", m.name());
            j.addProperty("joined", m.joined());
            j.addProperty("speaking", m.speaking());
            members.add(j);
        }
        o.add("members", members);
        return o;
    }
}
