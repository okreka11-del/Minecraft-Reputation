package com.vova.reputation.client;

import com.vova.reputation.ReputationMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Server-synced, two-page parchment journal with seven accessible sections. */
public final class ReputationBookScreen extends Screen {
    private static final int W = 600, H = 324;
    private static final int INK = 0xFF49301F, GOLD = 0xFF9B6B29;
    private static final String[] SECTIONS = {"score", "factions", "quests", "settlements", "unlocks", "actions", "help"};
    private static final int[] RANKS = {-100,-80,-60,-20,0,100,120,140,160,180,200};
    private final int score, suspicion, hunterProgress;
    private final boolean hunterCompleted;
    private final String history, settlement;
    private final int[] factions = new int[6];
    private int section;
    private int historyPage;
    private int ox, oy;

    public ReputationBookScreen(int score, int suspicion, String history, String factionData,
                                String settlement, int hunterProgress, boolean hunterCompleted) {
        super(Component.translatable("reputation.book.title"));
        this.score = Math.max(-100, Math.min(200, score));
        this.suspicion = suspicion;
        this.history = history == null ? "" : history;
        this.settlement = settlement == null ? "none" : settlement;
        this.hunterProgress = Math.max(0, Math.min(10, hunterProgress));
        this.hunterCompleted = hunterCompleted;
        String[] parts = (factionData == null ? "" : factionData).split(",");
        for (int i = 0; i < Math.min(6, parts.length); i++) {
            try { factions[i] = Math.max(-100, Math.min(200, Integer.parseInt(parts[i]))); }
            catch (NumberFormatException ignored) { factions[i] = 0; }
        }
    }

    // Keep the book centered when it fits; only allow panning on smaller GUI viewports.
    // All offsets remain integral so bitmap fonts and parchment pixels are not resampled.
    private int clampPanX(int value) {
        return width >= W ? (width - W) / 2 : Math.max(width - W, Math.min(0, value));
    }
    private int clampPanY(int value) {
        return height >= H ? (height - H) / 2 : Math.max(height - H, Math.min(0, value));
    }

    private void geometry() {
        // Native GUI pixels only. Pan the unscaled book on small screens.
        ox = clampPanX((width - W) / 2);
        oy = clampPanY((height - H) / 2);
    }

    @Override protected void init() { geometry(); }

    private void turnPage(int direction) {
        if (section == 5 && direction < 0 && historyPage > 0) { historyPage--; return; }
        if (section == 5 && direction > 0 && historyPage + 1 < historyPages()) { historyPage++; return; }
        section = Math.max(0, Math.min(SECTIONS.length - 1, section + direction));
        historyPage = 0;
    }

    private int historyPages() {
        return Math.max(1, (history.isEmpty() ? 0 : history.split("\\n").length + 7) / 8);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Arrow keys use exactly the same page-turn rules as the parchment corners.
        if (keyCode == 263 || keyCode == 262) {
            turnPage(keyCode == 263 ? -1 : 1);
            return true;
        }
        // Home/End jump to the first/last section without retaining a stale history page.
        if (keyCode == 268 || keyCode == 269) {
            section = keyCode == 268 ? 0 : SECTIONS.length - 1;
            historyPage = 0;
            return true;
        }
        // Page Up/Down navigate history without changing sections.
        if (section == 5 && (keyCode == 266 || keyCode == 267)) {
            historyPage = Math.max(0, Math.min(historyPages() - 1,
                historyPage + (keyCode == 266 ? -1 : 1)));
            return true;
        }
        // Number keys navigate directly to the seven journal sections.
        if (keyCode >= 49 && keyCode < 49 + SECTIONS.length) {
            section = keyCode - 49;
            historyPage = 0;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void text(GuiGraphics g, String s, int x, int y, int color) {
        int max = x < 340 ? 200 : 210;
        // Reserve the actual glyph width of the suffix before truncating. The old
        // fixed 10px allowance was smaller than the bitmap-font ellipsis and
        // could spill into the page margin at native GUI scale.
        String suffix = "...";
        String shown = font.width(s) > max
            ? font.plainSubstrByWidth(s, Math.max(0, max - font.width(suffix))) + suffix
            : s;
        // One crisp text pass: the old translucent offset made glyphs look doubled.
        g.drawString(font, shown, x, y, color, false);
    }
    // Vanilla drawCenteredString always adds a shadow pass. Draw bitmap glyphs once
    // at integral coordinates to avoid the doubled pixel edges in journal labels.
    private void centeredText(GuiGraphics g, Component value, int centerX, int y, int color) {
        g.drawString(font, value, centerX - font.width(value) / 2, y, color, false);
    }
    private void centeredText(GuiGraphics g, String value, int centerX, int y, int color) {
        g.drawString(font, value, centerX - font.width(value) / 2, y, color, false);
    }
    private void tr(GuiGraphics g, String key, int x, int y, Object... args) {
        text(g, Component.translatable(key, args).getString(), x, y, INK);
    }
    private void rule(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, 0xFFB8955E);
    }
    private void progress(GuiGraphics g, int x, int y, int w, float fraction, int color) {
        g.fill(x, y, x + w, y + 9, 0xFF69543E);
        g.fill(x + 1, y + 1, x + w - 1, y + 8, 0xFFE2D0AB);
        g.fill(x + 1, y + 1, x + 1 + Math.round((w - 2) * Math.max(0, Math.min(1, fraction))), y + 8, color);
    }
    private void paper(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF3E241C);
        g.fill(x, y, x + w, y + h, 0xFFD4B582);
        g.fill(x + 5, y + 4, x + w - 5, y + h - 4, 0xFFF1DDB2);
        g.fill(x + 7, y + 6, x + w - 7, y + 7, 0xFFE3C995);
        g.fill(x + 7, y + h - 7, x + w - 7, y + h - 6, 0xFFE3C995);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF100B10);
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        // A real two-leaf book, not the old compact dialog.
        g.fill(108, 9, 599, 291, 0xFF37201A);
        paper(g, 115, 15, 230, 270);
        paper(g, 351, 15, 230, 270);
        g.fill(344, 13, 352, 286, 0xFF70402A);
        g.fill(347, 15, 350, 284, 0xFFB7844A);
        centeredText(g, title, 347, 25, INK);
        rule(g, 138, 42, 183);
        rule(g, 373, 42, 183);
        for (int i = 0; i < SECTIONS.length; i++) {
            int tabTop = 27 + i * 39;
            g.fill(2, tabTop, 106, tabTop + 34, i == section ? 0xFF654085 : 0xFF352634);
            if (i == section) {
                g.fill(0, tabTop, 3, tabTop + 34, 0xFFFFCF68);
                g.fill(3, tabTop, 109, tabTop + 2, 0xFF9B73B7);
            }
            // Draw labels after the selected-tab decoration so they remain legible.
            centeredText(g, Component.translatable("reputation.journal.tab." + SECTIONS[i]),
                54, tabTop + 12, i == section ? 0xFFFFD46A : 0xFFE5D3B1);
        }
        switch (section) {
            case 0 -> scorePage(g);
            case 1 -> factionPage(g);
            case 2 -> questPage(g);
            case 3 -> settlementPage(g);
            case 4 -> unlockPage(g);
            case 5 -> historyPage(g);
            default -> helpPage(g);
        }
        centeredText(g, (section + 1) + " / " + SECTIONS.length, 349, 275, GOLD);
        // Folded parchment corners are the navigation controls.
        g.fill(115, 253, 143, 283, 0xFFB18A57);
        g.fill(119, 257, 140, 280, 0xFFF1DDB2);
        centeredText(g, "◀", 129, 266, INK);
        g.fill(552, 253, 581, 283, 0xFFB18A57);
        g.fill(556, 257, 578, 280, 0xFFF1DDB2);
        centeredText(g, "▶", 567, 266, INK);
        g.fill(574, 0, 599, 24, 0xFF532A2A);
        centeredText(g, "X", 586, 8, 0xFFFFFFFF);
        g.pose().popPose();
        // Buttons are positioned in actual GUI coordinates for correct mouse hitboxes.
        // No vanilla buttons or screen background blur.
        // Long history entries remain readable without changing persisted history data.
        if (section == 5 && !history.isEmpty()) {
            double localX = mouseX - ox;
            double localY = mouseY - oy;
            int column = localX >= 137 && localX < 337 ? 0
                : localX >= 373 && localX < 573 ? 1 : -1;
            int row = localY >= 83 && localY < 219 ? (int) ((localY - 83) / 34) : -1;
            if (column >= 0 && row >= 0 && row < 4) {
                String[] entries = history.split("\\n");
                int index = historyPage * 8 + column * 4 + row;
                if (index < entries.length && font.width(entries[index]) > 200) {
                    g.renderTooltip(font, Component.literal(entries[index]), mouseX, mouseY);
                }
            }
        }
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button,
                                          double dragX, double dragY) {
        if (button == 2) {
            ox = clampPanX(ox + (int) Math.round(dragX));
            oy = clampPanY(oy + (int) Math.round(dragY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (section == 5 && scrollY != 0) {
            double x = mouseX - ox, y = mouseY - oy;
            if (x >= 115 && x <= 581 && y >= 15 && y <= 285) {
                historyPage = Math.max(0, Math.min(historyPages() - 1,
                    historyPage + (scrollY < 0 ? 1 : -1)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= 0 && mouseX < width && mouseY >= 0 && mouseY < height) {
            double x = mouseX - ox;
            double y = mouseY - oy;
            if (x >= 0 && x <= 109 && y >= 27 && y < 27 + SECTIONS.length * 39) {
                int index = (int) ((y - 27) / 39);
                if (y >= 27 + index * 39 + 34) return true;
                section = index;
                historyPage = 0;
                return true;
            }
            if (x >= 572 && x <= 600 && y >= 0 && y <= 26) { onClose(); return true; }
            if (x >= 115 && x <= 145 && y >= 250 && y <= 287) { turnPage(-1); return true; }
            if (x >= 550 && x <= 585 && y >= 250 && y <= 287) { turnPage(1); return true; }
            // The book itself is an interactive table of contents: click a page heading.
            if (section == 0 && x >= 373 && x <= 550 && y >= 58 && y <= 95) {
                section = 2;
                historyPage = 0;
                return true;
            }
            if (section == 0 && x >= 373 && x <= 550 && y >= 184 && y <= 225) {
                section = 5;
                historyPage = 0;
                return true;
            }
            if (section == 0 && x >= 137 && x <= 320 && y >= 170 && y <= 207) {
                section = 4;
                historyPage = 0;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void scorePage(GuiGraphics g) {
        tr(g, "reputation.book.score", 137, 59, score);
        text(g, Component.translatable(ReputationMod.rank(score)).getString(), 137, 80, 0xFF805A21);
        tr(g, "reputation.book.suspicion", 137, 103, suspicion);
        progress(g, 137, 128, 180, (score + 100) / 300f, score < 0 ? 0xFFAC4659 : 0xFF8252AE);
        text(g, score + "  /  +200", 137, 145, INK);
        tr(g, "reputation.book.ranks", 137, 171);
        int current = -100;
        for (int threshold : RANKS) if (score >= threshold) current = threshold;
        text(g, Component.translatable(ReputationMod.rank(current)).getString(), 137, 191, INK);
        tr(g, "reputation.book.range", 137, 214);
        tr(g, "reputation.book.quests", 373, 59);
        tr(g, "reputation.book.hunter_title", 373, 81);
        tr(g, hunterCompleted ? "reputation.book.hunter_done" : "reputation.book.hunter_progress", 373, 105, hunterProgress);
        progress(g, 373, 126, 176, hunterProgress / 10f, 0xFF6A9B42);
        tr(g, "reputation.book.hunter_reward", 373, 148);
        rule(g, 373, 174, 176);
        tr(g, "reputation.book.actions", 373, 187);
        if (history.isEmpty()) tr(g, "reputation.book.empty", 373, 207);
        else text(g, history.split("\\n")[0], 373, 207, INK);
    }
    private void factionPage(GuiGraphics g) {
        tr(g, "reputation.book.factions", 137, 60);
        for (int i = 0; i < factions.length; i++) {
            int y = 80 + i * 25;
            tr(g, "reputation.faction." + ReputationMod.FACTIONS[i], 137, y);
            text(g, (factions[i] > 0 ? "+" : "") + factions[i], 300, y, INK);
            progress(g, 373, y, 168, (factions[i] + 100) / 300f, factions[i] < 0 ? 0xFFAD4A5B : 0xFF7653A6);
        }
    }
    private void questPage(GuiGraphics g) {
        tr(g, "reputation.book.quests", 137, 60);
        tr(g, "reputation.book.hunter_title", 137, 90);
        tr(g, hunterCompleted ? "reputation.book.hunter_done" : "reputation.book.hunter_progress", 137, 114, hunterProgress);
        progress(g, 137, 139, 184, hunterProgress / 10f, 0xFF719B42);
        tr(g, "reputation.book.hunter_reward", 137, 165);
        tr(g, "reputation.book.help_book", 373, 90);
    }
    private void settlementPage(GuiGraphics g) {
        tr(g, "reputation.journal.tab.settlements", 137, 60);
        if ("none".equals(settlement)) tr(g, "reputation.book.settlement_none", 137, 92);
        else {
            String[] parts = settlement.split(":", 2);
            tr(g, "reputation.book.settlement_" + ("occupied".equals(parts[0]) ? "occupied" : "free"), 137, 92);
            if (parts.length > 1) tr(g, "reputation.book.settlement_stage", 137, 116, parts[1]);
        }
    }
    private void unlockPage(GuiGraphics g) {
        tr(g, "reputation.book.unlocks", 137, 60);
        tr(g, score >= 151 ? "reputation.book.unlock_hero" :
            score <= -80 ? "reputation.book.unlock_dark" : "reputation.book.unlock_neutral", 137, 93);
        tr(g, "reputation.book.ranks", 373, 60);
        for (int i = 0; i < RANKS.length; i++) {
            int x = i < 6 ? 373 : 137;
            int y = i < 6 ? 84 + i * 26 : 147 + (i - 6) * 24;
            text(g, RANKS[i] + " " + Component.translatable(ReputationMod.rank(RANKS[i])).getString(), x, y,
                ReputationMod.rank(RANKS[i]).equals(ReputationMod.rank(score)) ? 0xFF8C4B8D : INK);
        }
    }
    private void historyPage(GuiGraphics g) {
        tr(g, "reputation.book.actions", 137, 60);
        if (history.isEmpty()) { tr(g, "reputation.book.empty", 137, 90); return; }
        String[] lines = history.split("\\n");
        int start = historyPage * 8;
        for (int i = start; i < Math.min(start + 8, lines.length); i++) {
            int x = i - start < 4 ? 137 : 373;
            int y = 83 + ((i - start) % 4) * 34;
            text(g, lines[i], x, y, INK);
            rule(g, x, y + 14, 180);
        }
        text(g, (historyPage + 1) + " / " + historyPages(), 137, 254, GOLD);
    }
    private void helpPage(GuiGraphics g) {
        tr(g, "reputation.book.help", 137, 60);
        tr(g, "reputation.book.help_book", 137, 94);
        tr(g, "reputation.book.help_history", 137, 123);
        tr(g, "reputation.book.help_score", 137, 152);
    }
    @Override public boolean isPauseScreen() { return false; }
}
