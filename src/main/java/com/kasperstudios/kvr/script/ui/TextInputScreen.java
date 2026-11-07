package com.kasperstudios.kvr.script.ui;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Keyboard capture for a text field inside a floating window. Typing needs the keyboard, so while a
 * field is focused this invisible screen takes the keys (the player stands still, like with chat)
 * and forwards them to the field; the field itself is drawn in the world by {@link ScriptWindow}.
 * Enter = done (multiline: Ctrl+Enter), Esc = done, Ctrl+V = paste. Never pauses the game.
 *
 * <p>Key/char handlers are written for 1.20.x–1.21.8 ({@code keyPressed(int,int,int)},
 * {@code charTyped(char,int)}) without {@code @Override}, so the 1.21.9+ node (KeyEvent/CharacterEvent)
 * still compiles; the spatial UI is inert there anyway.
 */
public final class TextInputScreen extends Screen {

    public interface Target {
        String value();
        void set(String value);
        void submit();
        int maxLength();
        boolean multiline();
    }

    private final Target target;
    private String value;

    public TextInputScreen(Target target) {
        super(Component.empty());
        this.target = target;
        this.value = target.value();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the field is in the world; only a one-line hint at the bottom edge
        g.drawCenteredString(font, Component.translatable(target.multiline() ? "kvr.input.hint_multi" : "kvr.input.hint"), width / 2, height - 14, 0xB0FFFFFF);
    }

    /** No dim/blur: the world and the window with the field stay visible. */
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    public boolean charTyped(char c, int modifiers) {
        //? if >=1.21 {
        boolean allowed = net.minecraft.util.StringUtil.isAllowedChatCharacter(c);
        //? } else {
        /*boolean allowed = net.minecraft.SharedConstants.isAllowedChatCharacter(c);
        *///? }
        if (allowed && value.length() < target.maxLength()) {
            value += c;
            target.set(value);
        }
        return true;
    }

    public boolean keyPressed(int key, int scan, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!value.isEmpty()) {
                    value = ctrl ? value.replaceAll("\\S*\\s*$", "") : value.substring(0, value.length() - 1);
                    target.set(value);
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (target.multiline() && !ctrl) {
                    if (value.length() < target.maxLength()) {
                        value += "\n";
                        target.set(value);
                    }
                } else {
                    onClose();
                }
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard().replace("\r", "");
                    if (!target.multiline()) clip = clip.replace("\n", " ");
                    value = (value + clip).substring(0, Math.min(target.maxLength(), value.length() + clip.length()));
                    target.set(value);
                    return true;
                }
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                onClose();
                return true;
            }
            default -> {
                return true; // swallow everything else so WASD etc. do not leak while typing
            }
        }
    }

    @Override
    public void onClose() {
        target.submit();
        super.onClose();
    }
}
