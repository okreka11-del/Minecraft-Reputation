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
    private int panelWidth() { return Math.max(1, Math.min(290, width - 12)); }
    private int panelHeight() { return Math.max(1, Math.min(212, height - 12)); }
    private int panelX() { return (width - panelWidth()) / 2; }
    private int panelY() { return (height - panelHeight()) / 2; }

    @Override protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Закрыть"), b -> onClose())
            .bounds(panelX() + Math.max(0, (panelWidth() - Math.min(110, panelWidth())) / 2),
                panelY() + Math.max(0, panelHeight() - 27), Math.min(110, panelWidth()),
                Math.min(20, panelHeight())).build());
    }
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        g.fill(0, 0, width, height, 0xB0100B10);
        int x = panelX(), y = panelY(), w = panelWidth(), h = panelHeight();
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF35241D);
        g.fill(x, y, x + w, y + h, 0xFFD4B584);
        if (w > 12 && h > 12) g.fill(x + 6, y + 6, x + w - 6, y + h - 6, 0xFFF1DFB8);
        // Draw native-size bitmap glyphs once, without an offset shadow pass.
        // Fit long translations into the panel rather than resampling the font.
        int titleWidth = Math.max(0, w - 24);
        String heading = font.plainSubstrByWidth(title.getString(), titleWidth);
        g.drawString(font, heading, x + (w - font.width(heading)) / 2, y + 16, 0xFF56331C, false);
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
        // Keep glyphs at native 1:1 pixel size and reserve space for the close button.
        int availableWidth = Math.max(0, w - 24);
        int lineHeight = 18;
        int visible = Math.max(0, Math.min(lines.length, (h - 75) / lineHeight));
        for (int i = 0; i < visible; i++) {
            String line = lines[i];
            if (font.width(line) > availableWidth) {
                String suffix = font.width("...") <= availableWidth ? "..." : "";
                line = font.plainSubstrByWidth(line, Math.max(0, availableWidth - font.width(suffix))) + suffix;
            }
            g.drawString(font, line, x + 12, y + 43 + i * lineHeight, 0xFF49301F, false);
        }
        super.render(g, mx, my, tick);
    }
    @Override public boolean isPauseScreen() { return false; }
}
