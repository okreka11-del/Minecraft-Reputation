package com.vova.reputation.client;

import com.vova.reputation.ReputationMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only reputation journal. All values originate from server sync. */
public final class ReputationBookScreen extends Screen {
    private final int score;
    private final String history;
    private final int suspicion;
    private static final int PAGE_COUNT = 7;
    private static final int[] RANKS = {-100,-80,-60,-20,0,100,120,140,160,180,200};
    private int page;
    private Button previousButton;
    private Button nextButton;

    public ReputationBookScreen(int score, int suspicion, String history) {
        super(Component.translatable("reputation.book.title"));
        this.score = Math.max(-100, Math.min(200, score));
        this.history = history == null ? "" : history;
        this.suspicion = suspicion;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int controlsY = height / 2 + 83;
        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
            .bounds(center - 110, controlsY, 44, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
            .bounds(center + 66, controlsY, 44, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(center - 48, controlsY, 96, 20).build());
        updateNavigation();
    }

    private void changePage(int offset) {
        page = Math.max(0, Math.min(PAGE_COUNT - 1, page + offset));
        updateNavigation();
    }

    private void updateNavigation() {
        if (previousButton != null) previousButton.active = page > 0;
        if (nextButton != null) nextButton.active = page < PAGE_COUNT - 1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263 || keyCode == 262) {
            changePage(keyCode == 263 ? -1 : 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = width / 2 - 126;
        int top = height / 2 - 98;
        graphics.fill(left - 2, top - 2, left + 254, top + 178, 0xFF6B4D2B);
        graphics.fill(left, top, left + 252, top + 176, 0xF0181413);
        graphics.fill(left + 3, top + 3, left + 249, top + 6, 0xFFB99148);
        graphics.drawCenteredString(font, title, width / 2, top + 12, 0xFFFFDA8A);
        if (page == 0) {
            line(graphics, "score", left, top + 36, score);
            graphics.drawString(font, Component.translatable(ReputationMod.rank(score)), left + 16, top + 53, 0xFFFFD46B);
            line(graphics, "suspicion", left, top + 72, suspicion);
            int filled = Math.round((score + 100) * 208f / 300f);
            graphics.fill(left + 16, top + 94, left + 224, top + 105, 0xFF393039);
            graphics.fill(left + 16, top + 94, left + 16 + filled, top + 105,
                score < 0 ? 0xFFB4485A : 0xFFE0BB55);
            graphics.drawString(font, Component.translatable("reputation.book.range"), left + 16, top + 114, 0xFFAAAAAA);
        } else if (page == 1 || page == 2) {
            heading(graphics, "actions", left, top);
            if (history.isEmpty()) {
                line(graphics, "empty", left, top + 53);
            } else {
                String[] lines = history.split("\\n");
                int start = (page - 1) * 8;
                for (int i = start; i < Math.min(start + 8, lines.length); i++) {
                    String[] parts = lines[i].split(" \\| ", 3);
                    Component entry = parts.length == 3
                        ? Component.literal(parts[0] + "  ")
                            .append(Component.translatable("reputation.reason." + parts[1].replace(" ", "_")))
                            .append(Component.literal(" (" + parts[2] + ")"))
                        : Component.literal(lines[i]);
                    String label = entry.getString();
                    if (font.width(label) > 215) label = font.plainSubstrByWidth(label, 207) + "...";
                    graphics.drawString(font, label, left + 16, top + 49 + (i - start) * 13, 0xFFE4D6BB);
                }
            }
        } else if (page == 3) {
            heading(graphics, "ranks", left, top);
            for (int i = 0; i < RANKS.length; i++) {
                int y = top + 49 + i * 9;
                String key = ReputationMod.rank(RANKS[i]);
                Component label = Component.literal(RANKS[i] + "  ")
                    .append(Component.translatable(key));
                graphics.drawString(font, label, left + 16, y,
                    key.equals(ReputationMod.rank(score)) ? 0xFFFFD46B : 0xFFD5C9B5);
            }
        } else if (page == 4) {
            heading(graphics, "factions", left, top);
            line(graphics, "factions_pending", left, top + 57);
        } else if (page == 5) {
            heading(graphics, "quests", left, top);
            line(graphics, "quests_pending", left, top + 57);
            line(graphics, "settlements_pending", left, top + 79);
        } else {
            heading(graphics, "help", left, top);
            line(graphics, "help_book", left, top + 54);
            line(graphics, "help_history", left, top + 76);
            line(graphics, "help_score", left, top + 98);
        }
        graphics.drawCenteredString(font, (page + 1) + " / " + PAGE_COUNT, width / 2, top + 151, 0xFFB99148);
        // Render interactive buttons last so the parchment panel cannot cover them.
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void heading(GuiGraphics graphics, String key, int left, int top) {
        graphics.drawString(font, Component.translatable("reputation.book." + key),
            left + 16, top + 34, 0xFFFFD46B);
    }

    private void line(GuiGraphics graphics, String key, int left, int y, Object... args) {
        graphics.drawString(font, Component.translatable("reputation.book." + key, args),
            left + 16, y, 0xFFE4D6BB);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
