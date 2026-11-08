package com.vova.reputation.client;

import com.vova.reputation.ReputationNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Pixel-art, command-free villager conversation panel. */
public final class ReputationDialogueScreen extends Screen {
    private final int entityId;
    private final String greeting;
    private final int score;
    private String response = "";
    public ReputationDialogueScreen(int entityId, int score, String greeting) {
        super(Component.literal("Разговор с жителем"));
        this.entityId = entityId;
        this.score = score;
        this.greeting = greeting;
    }
    private int panelWidth() { return Math.max(1, Math.min(250, width - 12)); }
    private int panelHeight() { return Math.max(1, Math.min(155, height - 12)); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }

    @Override protected void init() {
        int available = Math.max(2, panelWidth() - 20);
        int gap = 6;
        int buttonWidth = Math.max(1, (available - gap) / 2);
        int x = panelX() + 10, y = panelY() + panelHeight() - 57;
        addRenderableWidget(Button.builder(Component.literal("Поговорить"), b -> {
            response = score <= -80 ? "Не причиняй вреда нашей деревне!"
                : score >= 151 ? "Спасибо, что защищаешь нас!"
                : score >= 100 ? "Мы рады видеть нашего союзника!"
                : "Наша деревня живёт торговлей и трудом.";
        }).bounds(x, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Торговля"), b -> {
            PacketDistributor.sendToServer(new ReputationNetwork.DialogueAction(entityId, 1));
            onClose();
        }).bounds(x + buttonWidth + gap, y, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Отношения"), b -> {
            response = score < 0 ? "Жители относятся к тебе с опаской."
                : score >= 151 ? "Жители доверяют тебе свою безопасность."
                : "Отношения зависят от твоих поступков.";
        }).bounds(x, y + 26, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(x + buttonWidth + gap, y + 26, buttonWidth, 20).build());
    }
    // Constrain server-provided text without fractional font transforms.
    private void drawFittedCentered(GuiGraphics g, String value, int y) {
        String safe = value == null ? "" : value;
        int maxWidth = Math.max(1, panelWidth() - 30);
        if (font.width(safe) > maxWidth) {
            String ellipsis = font.width("...") <= maxWidth ? "..." : "";
            safe = font.plainSubstrByWidth(safe, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
        }
        g.drawCenteredString(font, safe, panelX() + panelWidth() / 2, y, 0xFF49301F);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0100B10);
        int x = panelX(), y = panelY();
        int w = panelWidth(), h = panelHeight();
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF302018);
        g.fill(x, y, x + w, y + h, 0xFFD3B483);
        g.fill(x + 5, y + 5, x + w - 5, y + h - 5, 0xFFF1DEB6);
        drawFittedCentered(g, title.getString(), y + 13);
        drawFittedCentered(g, greeting, y + 45);
        drawFittedCentered(g, response, y + 66);
        super.render(g, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
