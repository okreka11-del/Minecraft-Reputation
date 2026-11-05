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
    @Override protected void init() {
        int x = width / 2 - 110, y = height / 2 + 24;
        addRenderableWidget(Button.builder(Component.literal("Поговорить"), b -> {
            response = score <= -80 ? "Не причиняй вреда нашей деревне!"
                : score >= 151 ? "Спасибо, что защищаешь нас!"
                : score >= 100 ? "Мы рады видеть нашего союзника!"
                : "Наша деревня живёт торговлей и трудом.";
        }).bounds(x, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Торговля"), b -> {
            PacketDistributor.sendToServer(new ReputationNetwork.DialogueAction(entityId, 1));
            onClose();
        }).bounds(x + 115, y, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Отношения"), b -> {
            response = score < 0 ? "Жители относятся к тебе с опаской."
                : score >= 151 ? "Жители доверяют тебе свою безопасность."
                : "Отношения зависят от твоих поступков.";
        }).bounds(x, y + 26, 105, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(x + 115, y + 26, 105, 20).build());
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0100B10);
        int x = width / 2 - 125, y = height / 2 - 78;
        g.fill(x - 3, y - 3, x + 253, y + 161, 0xFF302018);
        g.fill(x, y, x + 250, y + 155, 0xFFD3B483);
        g.fill(x + 5, y + 5, x + 245, y + 150, 0xFFF1DEB6);
        g.drawCenteredString(font, title, width / 2, y + 13, 0xFF59361E);
        g.drawCenteredString(font, greeting, width / 2, y + 45, 0xFF49301F);
        g.drawCenteredString(font, response, width / 2, y + 66, 0xFF49301F);
        super.render(g, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
