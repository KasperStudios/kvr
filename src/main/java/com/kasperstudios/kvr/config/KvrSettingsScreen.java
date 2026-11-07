package com.kasperstudios.kvr.config;

import com.kasperstudios.kvr.desktop.client.DesktopConsentScreen;
import com.kasperstudios.kvr.desktop.client.DesktopDaemon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * KVR settings (ModMenu on Fabric, "Config" button in NeoForge's mod list), two sections:
 * <ul>
 *   <li>Windows: default Public/Private, show other players' public windows, window background
 *       opacity (readability, 0018);</li>
 *   <li>PC apps: the desktop apps switch, clipboard sharing with PC apps (D2) and the kvr-desktopd consent.</li>
 * </ul>
 * Writes {@code kvr-client.toml} key by key. The opacity slider writes once, when the screen closes
 * (not on every drag step); windows pick the new value up on the next frame.
 *
 * <pre>{@code
 * // Fabric (ModMenu):   public ConfigScreenFactory<?> getModConfigScreenFactory() { return KvrSettingsScreen::new; }
 * // NeoForge 1.21:      container.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new KvrSettingsScreen(parent));
 * }</pre>
 */
public final class KvrSettingsScreen extends Screen {

    private static final int W = 310;
    private static final int ROW = 24;

    @Nullable private final Screen parent;
    /** Hashing the binary every frame would be wasteful: refreshed on init (also after the consent dialog). */
    private DesktopDaemon.Info info;
    private int opacity = -1;
    private int windowsY;
    private int desktopY;
    private int statusY;

    public KvrSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("kvr.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        KvrConfig.Client c = KvrConfig.client();
        info = DesktopDaemon.inspect();
        if (opacity < 0) opacity = c.windowOpacity();
        int x = width / 2 - W / 2;

        // --- windows
        windowsY = 34;
        int y = windowsY + 12;
        addRenderableWidget(CycleButton.onOffBuilder(c.defaultWindowPublic())
            .create(x, y, W, 20, Component.translatable("kvr.settings.default_public"),
                (b, v) -> KvrConfig.updateClient("default_window_public", v)));
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(c.showPublicWindows())
            .create(x, y, W, 20, Component.translatable("kvr.settings.show_public"),
                (b, v) -> KvrConfig.updateClient("show_public_windows", v)));
        y += ROW;
        addRenderableWidget(new OpacitySlider(x, y, W, 20));
        y += ROW;

        // --- PC apps
        desktopY = y + 8;
        y = desktopY + 12;
        addRenderableWidget(CycleButton.onOffBuilder(c.hostDesktopWindows())
            .create(x, y, W, 20, Component.translatable("kvr.settings.desktop"), (b, v) -> {
                KvrConfig.updateClient("enable_host_desktop_windows", v);
                if (!v) DesktopDaemon.stop();
                // switching it on without consent goes straight to the consent dialog
                if (v && !info.consented() && minecraft != null) minecraft.setScreen(new DesktopConsentScreen(this, null));
            }));
        y += ROW;
        addRenderableWidget(CycleButton.onOffBuilder(c.desktopClipboard())
            .create(x, y, W, 20, Component.translatable("kvr.settings.desktop_clipboard"),
                (b, v) -> KvrConfig.updateClient("desktop_clipboard_sync", v)));
        y += ROW;
        addRenderableWidget(Button.builder(Component.translatable("kvr.settings.consent"),
            b -> { if (minecraft != null) minecraft.setScreen(new DesktopConsentScreen(this, null)); })
            .bounds(x, y, W, 20).build());
        statusY = y + ROW + 2;

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        //? if <1.20.2 {
        /*renderBackground(g);
        *///?}
        super.render(g, mx, my, partialTick);
        int x = width / 2 - W / 2;
        g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("kvr.settings.section.windows"), x, windowsY, 0xFF67E8F9);
        g.drawString(font, Component.translatable("kvr.settings.section.desktop"), x, desktopY, 0xFF67E8F9);
        g.drawCenteredString(font, Component.translatable("kvr.settings.desktop_note"), width / 2, statusY, 0xFFA0AEC0);
        Component status = Component.translatable(info.consented() ? "kvr.settings.consent_yes" : "kvr.settings.consent_no");
        g.drawCenteredString(font, status, width / 2, statusY + 12, info.consented() ? 0xFF4ADE80 : 0xFFFBBF24);
    }

    @Override
    public void onClose() {
        saveOpacity();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        saveOpacity(); // Esc / window closed / switched to the consent dialog
        super.removed();
    }

    private void saveOpacity() {
        if (opacity >= 0 && opacity != KvrConfig.client().windowOpacity()) KvrConfig.updateClient("window_opacity", opacity);
    }

    private final class OpacitySlider extends AbstractSliderButton {

        OpacitySlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), opacity / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("kvr.settings.window_opacity", Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            opacity = (int) Math.round(value * 100);
        }
    }
}
