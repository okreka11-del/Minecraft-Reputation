package com.vova.reputation.client;

import com.vova.reputation.ReputationNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * First server-controlled squad orders: only the player's own nearby tamed wolves.
 * This deliberately does not claim to implement villager/illager recruitment.
 */
public final class ReputationArmyScreen extends Screen {
    private final int available;
    public ReputationArmyScreen(int available) {
        super(Component.literal("Отряд — приказы"));
        this.available = available;
    }

    @Override protected void init() {
        int w = Math.min(238, Math.max(90, width - 16));
        int x = (width - w) / 2;
        int y = panelTop();
        int buttonHeight = commandButtonHeight();
        int buttonGap = commandButtonGap();
        int firstButtonY = y + headerHeight();
        Button follow = Button.builder(Component.literal("За мной"), b -> issue(0))
            .bounds(x + 8, firstButtonY, w - 16, buttonHeight).build();
        follow.active = available > 0;
        addRenderableWidget(follow);
        Button guard = Button.builder(Component.literal("Охранять позицию"), b -> issue(1))
            .bounds(x + 8, firstButtonY + buttonHeight + buttonGap, w - 16, buttonHeight).build();
        guard.active = available > 0;
        addRenderableWidget(guard);
        Button rally = Button.builder(Component.literal("Собрать рядом"), b -> issue(2))
            .bounds(x + 8, firstButtonY + 2 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        rally.active = available > 0;
        addRenderableWidget(rally);
        Button disengage = Button.builder(Component.literal("Прекратить бой"), b -> issue(3))
            .bounds(x + 8, firstButtonY + 3 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        disengage.active = available > 0;
        addRenderableWidget(disengage);
        Button attack = Button.builder(Component.literal("Атаковать отмеченного монстра"), b -> issue(4))
            .bounds(x + 8, firstButtonY + 4 * (buttonHeight + buttonGap), w - 16, buttonHeight).build();
        attack.active = available > 0;
        addRenderableWidget(attack);
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(x + 8, firstButtonY + 5 * (buttonHeight + buttonGap), w - 16, buttonHeight).build());
    }

    // Fit all six commands into short GUI-scaled windows without fractional font scaling.
    private int headerHeight() { return height < 120 ? 30 : height < 165 ? 37 : 48; }
    private int commandButtonGap() { return height < 165 ? 1 : 6; }
    private int commandButtonHeight() {
        if (height >= 165) return 20;
        return Math.max(10, Math.min(16, (height - 12 - headerHeight() - 5 * commandButtonGap()) / 6));
    }

    private int panelTop() {
        int buttonHeight = commandButtonHeight();
        int buttonGap = commandButtonGap();
        int contentHeight = headerHeight() + 6 * buttonHeight + 5 * buttonGap + 8;
        return Math.max(4, (height - contentHeight) / 2);
    }

    private void issue(int order) {
        PacketDistributor.sendToServer(new ReputationNetwork.ArmyOrder(order));
        onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Keep the world fully visible behind squad commands; no fullscreen dim layer.
        int w = Math.min(238, Math.max(90, width - 16));
        int x = (width - w) / 2, y = panelTop();
        int buttonHeight = commandButtonHeight();
        int buttonGap = commandButtonGap();
        int firstButtonY = y + headerHeight();
        int panelBottom = Math.min(height - 2, firstButtonY + 6 * buttonHeight + 5 * buttonGap + 8);
        g.fill(x - 3, y - 3, x + w + 3, panelBottom, 0xFF30251A);
        g.fill(x, y, x + w, panelBottom - 3, 0xFFE2C796);
        // Pixel-sharp inset frame and faction-neutral gold accent.
        g.fill(x + 2, y + 2, x + w - 2, y + 4, 0xFFD4A64E);
        g.fill(x + 3, panelBottom - 7, x + w - 3, panelBottom - 5, 0xFF8B6334);
        String heading = font.plainSubstrByWidth(title.getString(), w - 16);
        g.drawString(font, heading, x + (w - font.width(heading)) / 2, y + 8, 0xFF45301B, false);
        String count = available == 0 ? "Нет ваших волков поблизости" : "Ваши волки рядом: " + available;
        if (height >= 120) {
            g.drawString(font, font.plainSubstrByWidth(count, w - 16), x + 8, y + 25, 0xFF45301B, false);
        }
        // On compact GUI heights the first button starts at y + 37.
        // Omit this optional hint rather than drawing text over a clickable command.
        if (height >= 165) {
            String range = "1–5 / Num 1–5 · Радиус 24 блока";
            g.drawString(font, font.plainSubstrByWidth(range, w - 16), x + 8, y + 35, 0xFF705438, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    // Number row and numeric keypad mirror the five visible squad commands.
    // Disabled commands must never send packets when no owned squad is nearby.
    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int order = -1;
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
            order = keyCode - GLFW.GLFW_KEY_1;
        } else if (keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode <= GLFW.GLFW_KEY_KP_5) {
            order = keyCode - GLFW.GLFW_KEY_KP_1;
        }
        if (order >= 0) {
            if (available > 0) issue(order);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean isPauseScreen() { return false; }
}
