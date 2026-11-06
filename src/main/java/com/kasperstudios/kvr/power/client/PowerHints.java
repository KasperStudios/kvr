package com.kasperstudios.kvr.power.client;

import com.kasperstudios.kvr.power.PowerData;
import com.kasperstudios.kvr.spatial.KVRSpatial;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Says in the action bar why KVR OS is not there yet — the first live test showed that silence
 * ("Alt+RMB does nothing") is the worst possible answer. A hint appears:
 * <ul>
 *   <li>when the glasses' state changes (put on → "off, press O", booting countdown, "ready" with the keys);</li>
 *   <li>when the player asks for the OS while it is not running: Alt pressed, RMB in Alt mode, O pressed.</li>
 * </ul>
 * No hint while the OS runs normally, none during an overload (the alarm handles that).
 * Client tick, registered by {@code KVRClientBootstrap} after {@code KVRSpatial.initClient}.
 */
public final class PowerHints {

    enum Status { NONE, NO_GLASSES, EMPTY, OFF, BOOTING, HUD_OFF, ON, OVERLOAD }

    private static Status last = Status.NONE;
    private static int lastBootSeconds = -1;
    private static boolean lastAlt;
    private static boolean lastUse;
    private static boolean lastPower;

    private PowerHints() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !KVRSpatial.ready()) {
            last = Status.NONE;
            return;
        }
        long now = mc.level.getGameTime();
        Status s = status(now);

        boolean alt = KVRSpatial.cursor().active();
        boolean use = mc.options.keyUse.isDown();
        KeyMapping powerKey = KVRSpatial.powerKey().key();
        boolean power = powerKey.isDown();
        boolean asked = (alt && !lastAlt) || (alt && use && !lastUse) || (power && !lastPower);
        lastAlt = alt;
        lastUse = use;
        lastPower = power;

        if (s == Status.BOOTING) {
            int sec = bootSecondsLeft(now);
            if (sec != lastBootSeconds) show(Component.translatable("kvr.hint.booting", sec));
            lastBootSeconds = sec;
        } else {
            lastBootSeconds = -1;
            boolean changed = s != last && s != Status.NO_GLASSES; // taking the glasses off needs no comment
            if (changed || (asked && s != Status.ON)) show(message(s));
        }
        last = s;
    }

    static Status status(long now) {
        Optional<PowerClient.Snapshot> snap = PowerClient.snapshot();
        if (snap.isEmpty()) return Status.NO_GLASSES;
        PowerData d = snap.get().data();
        if (d.overloading()) return Status.OVERLOAD;
        return switch (d.state()) {
            case OFF -> d.percent(snap.get().build()) <= 0 ? Status.EMPTY : Status.OFF;
            case ON -> d.booting(now) ? Status.BOOTING : Status.ON;
            case HUD_OFF -> d.booting(now) ? Status.BOOTING : Status.HUD_OFF;
        };
    }

    private static int bootSecondsLeft(long now) {
        return PowerClient.snapshot().map(s -> (int) Math.max(1, Math.ceil((s.data().bootUntil() - now) / 20.0))).orElse(0);
    }

    private static Component message(Status s) {
        Component power = KVRSpatial.powerKey().key().getTranslatedKeyMessage();
        Component alt = KVRSpatial.cursor().toggleKey().getTranslatedKeyMessage();
        return switch (s) {
            case NO_GLASSES -> Component.translatable("kvr.hint.no_glasses");
            case EMPTY -> Component.translatable("kvr.hint.empty");
            case OFF -> Component.translatable("kvr.hint.off", power);
            case HUD_OFF -> Component.translatable("kvr.hint.hud_off", power);
            case ON -> Component.translatable("kvr.hint.ready", alt, alt, power);
            default -> Component.empty();
        };
    }

    private static void show(Component c) {
        if (c.getString().isEmpty()) return;
        Minecraft.getInstance().gui.setOverlayMessage(c, false);
    }
}
