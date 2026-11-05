package com.kasperstudios.kvr.config;

import com.kasperstudios.kvr.desktop.client.DesktopConsentScreen;
import com.kasperstudios.kvr.desktop.client.DesktopDaemon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * KVR settings (ModMenu on Fabric, "Config" button in NeoForge's mod list): window defaults and the
 * desktop apps switch with the kvr-desktopd consent. Writes {@code kvr-client.toml} key by key.
 *
 * <pre>{@code
 * // Fabric (ModMenu):   public ConfigScreenFactory<?> getModConfigScreenFactory() { return KvrSettingsScreen::new; }
 * // NeoForge 1.21:      container.registerExtensionPoint(IConfigScreenFactory.class, (c, parent) -> new KvrSettingsScreen(parent));
 * }</pre>
 */
public final class KvrSettingsScreen extends Screen {

    @Nullable private final Screen parent;
    /** Hashing the binary every frame would be wasteful: refreshed on init (also after the consent dialog). */
    private DesktopDaemon.Info info;

    public KvrSettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("kvr.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        KvrConfig.Client c = KvrConfig.client();
        info = DesktopDaemon.inspect();
        int x = width / 2 - 155;
        int y = 44;
        addRenderableWidget(CycleButton.onOffBuilder(c.defaultWindowPublic())
            .create(x, y, 310, 20, Component.translatable("kvr.settings.default_public"),
                (b, v) -> KvrConfig.updateClient("default_window_public", v)));
        y += 24;
        addRenderableWidget(CycleButton.onOffBuilder(c.showPublicWindows())
            .create(x, y, 310, 20, Component.translatable("kvr.settings.show_public"),
                (b, v) -> KvrConfig.updateClient("show_public_windows", v)));
        y += 36;
        addRenderableWidget(CycleButton.onOffBuilder(c.hostDesktopWindows())
            .create(x, y, 310, 20, Component.translatable("kvr.settings.desktop"), (b, v) -> {
                KvrConfig.updateClient("enable_host_desktop_windows", v);
                if (!v) DesktopDaemon.stop();
                // switching it on without consent goes straight to the consent dialog
                if (v && !info.consented() && minecraft != null) minecraft.setScreen(new DesktopConsentScreen(this, null));
            }));
        y += 24;
        addRenderableWidget(Button.builder(Component.translatable("kvr.settings.consent"),
            b -> { if (minecraft != null) minecraft.setScreen(new DesktopConsentScreen(this, null)); })
            .bounds(x, y, 310, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 100, height - 28, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        //? if <1.20.2 {
        /*renderBackground(g);
        *///?}
        super.render(g, mx, my, partialTick);
        g.drawCenteredString(font, title, width / 2, 18, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("kvr.settings.desktop_note"), width / 2, 90, 0xA0AEC0);
        Component status = Component.translatable(info.consented() ? "kvr.settings.consent_yes" : "kvr.settings.consent_no");
        g.drawCenteredString(font, status, width / 2, 158, info.consented() ? 0x4ADE80 : 0xFBBF24);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
