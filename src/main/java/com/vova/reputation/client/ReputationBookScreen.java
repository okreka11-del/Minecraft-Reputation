package com.vova.reputation.client;

import com.vova.reputation.ReputationMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only reputation journal. All values originate from server sync. */
public final class ReputationBookScreen extends Screen {
    private final int score;
    private final String history;
    private int page;

    public ReputationBookScreen(int score, String history) {
        super(Component.literal("Reputation Journal"));
        this.score = Math.max(-100, Math.min(200, score));
        this.history = history;
    }

    @Override
    protected void init() {
        int center = width / 2;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1))
            .bounds(center - 110, height - 42, 44, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(2, page + 1))
            .bounds(center + 66, height - 42, 44, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(center - 48, height - 42, 96, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = Math.max(8, width / 2 - 126);
        int top = Math.max(8, height / 2 - 98);
        graphics.fill(left, top, left + 252, top + 176, 0xF0181413);
        graphics.fill(left + 3, top + 3, left + 249, top + 6, 0xFFB99148);
        graphics.drawCenteredString(font, title, width / 2, top + 12, 0xFFFFDA8A);
        if (page == 0) {
            graphics.drawString(font, Component.literal("Score: " + score), left + 16, top + 36, 0xFFFFFFFF);
            graphics.drawString(font, Component.translatable(ReputationMod.rank(score)), left + 16, top + 53, 0xFFFFD46B);
            graphics.drawString(font, Component.literal("Range: -100 to +200"), left + 16, top + 73, 0xFFAAAAAA);
        } else if (page == 1) {
            graphics.drawString(font, Component.literal("Recent actions"), left + 16, top + 34, 0xFFFFD46B);
            if (history.isEmpty()) {
                graphics.drawString(font, Component.literal("No recorded changes"), left + 16, top + 53, 0xFFAAAAAA);
            } else {
                String[] lines = history.split("\n");
                for (int i = 0; i < Math.min(10, lines.length); i++) {
                    String line = lines[i];
                    if (font.width(line) > 216) line = font.plainSubstrByWidth(line, 210) + "...";
                    graphics.drawString(font, line, left + 16, top + 49 + i * 11, 0xFFE4D6BB);
                }
            }
        } else {
            graphics.drawString(font, Component.literal("More journal chapters"), left + 16, top + 36, 0xFFFFD46B);
            graphics.drawString(font, Component.literal("Factions, quests and settlements"), left + 16, top + 56, 0xFFAAAAAA);
            graphics.drawString(font, Component.literal("are not yet implemented."), left + 16, top + 70, 0xFFAAAAAA);
        }
        graphics.drawCenteredString(font, (page + 1) + " / 3", width / 2, top + 151, 0xFFB99148);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
