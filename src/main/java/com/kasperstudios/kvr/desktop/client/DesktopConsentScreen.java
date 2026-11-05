package com.kasperstudios.kvr.desktop.client;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.jetbrains.annotations.Nullable;

/**
 * Consent before kvr-desktopd — a separate native program — is downloaded or started. Shows exactly
 * which file (path + SHA-256) or which download (URL + pinned SHA-256) the player is approving and what
 * the program does. The consent is stored as that hash: a different binary asks again.
 * Opened from the KVR settings screen and automatically on the first use of the desktop dock item.
 */
public final class DesktopConsentScreen extends Screen {

    @Nullable private final Screen parent;
    @Nullable private final Runnable onAllow;
    private final DesktopDaemon.Info info;

    public DesktopConsentScreen(@Nullable Screen parent, @Nullable Runnable onAllow) {
        super(Component.translatable("kvr.desktop.consent.title"));
        this.parent = parent;
        this.onAllow = onAllow;
        this.info = DesktopDaemon.inspect();
    }

    private boolean approvable() {
        return info.exists() || info.canDownload();
    }

    @Override
    protected void init() {
        int y = height - 34;
        Button allow = Button.builder(Component.translatable(info.exists() ? "kvr.desktop.consent.allow" : "kvr.desktop.consent.allow_download"), b -> {
            DesktopDaemon.grantConsent(info);
            onClose();
            if (onAllow != null) onAllow.run();
        }).bounds(width / 2 - 154, y, 150, 20).build();
        allow.active = approvable();
        addRenderableWidget(allow);
        addRenderableWidget(Button.builder(Component.translatable(info.consented() ? "kvr.desktop.consent.revoke" : "kvr.desktop.consent.deny"), b -> {
            if (info.consented()) DesktopDaemon.revokeConsent();
            onClose();
        }).bounds(width / 2 + 4, y, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        //? if <1.20.2 {
        /*renderBackground(g);
        *///?}
        super.render(g, mx, my, partialTick);
        int w = Math.min(420, width - 40);
        int x = (width - w) / 2;
        int y = 20;
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, y, 0xFFFFFF);
        y += 18;
        for (String key : List.of("kvr.desktop.consent.what", "kvr.desktop.consent.hidden", "kvr.desktop.consent.private", "kvr.desktop.consent.stop")) {
            FormattedText line = Component.translatable(key);
            g.drawWordWrap(font, line, x, y, w, 0xE2E8F0);
            y += font.wordWrapHeight(line, w) + 4;
        }
        y += 6;
        g.drawString(font, Component.translatable("kvr.desktop.consent.file", info.binary().toAbsolutePath().toString()), x, y, 0xA0AEC0, false);
        y += 12;
        if (info.exists()) {
            g.drawString(font, Component.literal("SHA-256: " + info.sha256()), x, y, 0x67E8F9, false);
        } else if (info.canDownload()) {
            g.drawWordWrap(font, Component.translatable("kvr.desktop.consent.download", info.downloadUrl()), x, y, w, 0xFBBF24);
            y += font.wordWrapHeight(Component.translatable("kvr.desktop.consent.download", info.downloadUrl()), w) + 2;
            g.drawString(font, Component.literal("SHA-256: " + info.downloadSha256()), x, y, 0x67E8F9, false);
        } else {
            g.drawWordWrap(font, Component.translatable("kvr.desktop.consent.missing"), x, y, w, 0xF43F5E);
        }
        if (info.consented()) {
            g.drawCenteredString(font, Component.translatable("kvr.desktop.consent.already"), width / 2, height - 50, 0x4ADE80);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
