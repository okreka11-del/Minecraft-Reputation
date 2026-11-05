package com.vova.reputation.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Server-synchronized village overview; never changes the world on the client. */
public final class ReputationVillageScreen extends Screen {
    private final String status;
    private final int stage, buildings, villagers, standing, score;
    public ReputationVillageScreen(String status, int stage, int buildings, int villagers, int standing, int score) {
        super(Component.literal("Деревня"));
        this.status = status;
        this.stage = stage;
        this.buildings = buildings;
        this.villagers = villagers;
        this.standing = standing;
        this.score = score;
    }
    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(width / 2 - 55, height / 2 + 76, 110, 20).build());
    }
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        renderBackground(g, mx, my, tick);
        int x = width / 2 - 145, y = height / 2 - 112;
        g.fill(x - 3, y - 3, x + 293, y + 218, 0xFF35241D);
        g.fill(x, y, x + 290, y + 212, 0xFFD4B584);
        g.fill(x + 6, y + 6, x + 284, y + 206, 0xFFF1DFB8);
        g.drawCenteredString(font, title, width / 2, y + 16, 0xFF56331C);
        String[] lines = {
            "Статус: " + status,
            "Развитие: " + stage + " / 3",
            "Построек мода: " + buildings,
            "Жителей поблизости: " + villagers,
            "Репутация: " + score,
            "Отношения с жителями: " + standing,
            "Торговля: " + (score < 0 || standing <= -40 ? "Недоступна" :
                standing >= 100 ? "Особые скидки" : standing >= 40 ? "Скидки" : "Обычные условия")
        };
        for (int i = 0; i < lines.length; i++)
            g.drawString(font, lines[i], x + 17, y + 43 + i * 18, 0xFF49301F, false);
        super.render(g, mx, my, tick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
