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
        int y = Math.max(4, (height - 146) / 2);
        int buttonHeight = height < 165 ? 16 : 20;
        int buttonGap = height < 165 ? 2 : 6;
        int firstButtonY = y + (height < 165 ? 37 : 42);
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
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(x + 8, firstButtonY + 3 * (buttonHeight + buttonGap), w - 16, buttonHeight).build());
    }

    private void issue(int order) {
        PacketDistributor.sendToServer(new ReputationNetwork.ArmyOrder(order));
        onClose();
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0100B10);
        int w = Math.min(238, Math.max(90, width - 16));
        int x = (width - w) / 2, y = Math.max(4, (height - 146) / 2);
        int panelBottom = Math.min(height - 3, y + (height < 165 ? 113 : 147));
        g.fill(x - 3, y - 3, x + w + 3, panelBottom, 0xFF30251A);
        g.fill(x, y, x + w, panelBottom - 3, 0xFFE2C796);
        String heading = font.plainSubstrByWidth(title.getString(), w - 16);
        g.drawString(font, heading, x + (w - font.width(heading)) / 2, y + 8, 0xFF45301B, false);
        String count = available == 0 ? "Нет ваших волков поблизости" : "Ваши волки рядом: " + available;
        g.drawString(font, font.plainSubstrByWidth(count, w - 16), x + 8, y + 25, 0xFF45301B, false);
        // Show the actual server-side command range, including on narrow GUI scales.
        String range = "Радиус приказов: 24 блока";
        g.drawString(font, font.plainSubstrByWidth(range, w - 16), x + 8, y + 35, 0xFF705438, false);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
