package com.vova.reputation.client;

import com.vova.reputation.ReputationNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

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
        int y = height / 2 - 54;
        addRenderableWidget(Button.builder(Component.literal("За мной"), b -> issue(0))
            .bounds(x + 8, y + 42, w - 16, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Охранять позицию"), b -> issue(1))
            .bounds(x + 8, y + 68, w - 16, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(x + 8, y + 94, w - 16, 20).build());
    }

    private void issue(int order) {
        PacketDistributor.sendToServer(new ReputationNetwork.ArmyOrder(order));
        onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0100B10);
        int w = Math.min(238, Math.max(90, width - 16));
        int x = (width - w) / 2, y = height / 2 - 54;
        g.fill(x - 3, y - 3, x + w + 3, y + 121, 0xFF30251A);
        g.fill(x, y, x + w, y + 118, 0xFFE2C796);
        String heading = font.plainSubstrByWidth(title.getString(), w - 16);
        g.drawString(font, heading, x + (w - font.width(heading)) / 2, y + 8, 0xFF45301B, false);
        String count = "Ваши волки рядом: " + available;
        g.drawString(font, font.plainSubstrByWidth(count, w - 16), x + 8, y + 25, 0xFF45301B, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
