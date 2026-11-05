package com.kasperstudios.kvr.power.client;

import com.kasperstudios.kvr.power.PowerState;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * The physical power button of the glasses (default O). With the display off or the glasses powered
 * down there is no window to click, exactly like a real headset:
 * <ul>
 *   <li>short press — OFF → ON (cold boot), ON ⇄ HUD_OFF;</li>
 *   <li>hold 1 s — Power Off (also the Note 7 save).</li>
 * </ul>
 */
public final class PowerKey {

    private static final int HOLD_TICKS = 20;

    //? if >=1.21.9 {
    /*// VERIFY on 1.21.11: KeyMapping.Category
    private final KeyMapping key = new KeyMapping("key.kvr.power", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O,
        KeyMapping.Category.register(com.kasperstudios.kvr.spatial.render.GlassStyle.id("main")));
    *///?} else {
    private final KeyMapping key = new KeyMapping("key.kvr.power", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, "key.categories.kvr");
    //?}

    private int held;
    private boolean firedLong;

    public KeyMapping key() {
        return key;
    }

    /** Client tick start. */
    public void tick() {
        if (key.isDown()) {
            held++;
            if (held == HOLD_TICKS) {
                firedLong = true;
                PowerClient.request(PowerState.OFF);
            }
        } else {
            if (held > 0 && !firedLong) shortPress();
            held = 0;
            firedLong = false;
        }
        while (key.consumeClick()) {
            // state is tracked through isDown(); drain the click queue
        }
    }

    private void shortPress() {
        Optional<PowerClient.Snapshot> s = PowerClient.snapshot();
        if (s.isEmpty()) return;
        PowerState now = s.get().data().state();
        PowerClient.request(now == PowerState.ON ? PowerState.HUD_OFF : PowerState.ON);
    }
}
