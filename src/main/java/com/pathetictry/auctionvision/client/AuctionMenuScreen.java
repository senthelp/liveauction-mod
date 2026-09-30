package com.pathetictry.auctionvision.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class AuctionMenuScreen extends Screen {
    private static final int CELL = 20;
    private static final int TOP = 54;

    private static List<ItemStack> ALL;

    private final List<ItemStack> filtered = new ArrayList<>();
    private EditBox search;
    private EditBox seconds;
    private EditBox minBox;
    private EditBox maxBox;
    private int scrollRows = 0;

    public AuctionMenuScreen() {
        super(Component.literal("Auction Item Selector"));
        if (ALL == null) {
            List<ItemStack> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack s = item.getDefaultInstance();
                if (!s.isEmpty()) list.add(s);
            }
            ALL = list;
        }
    }

    @Override
    protected void init() {
        search = new EditBox(this.font, this.width / 2 - 100, 6, 200, 18, Component.literal("Search"));
        search.setHint(Component.literal("Search items..."));
        search.setResponder(s -> refilter());
        addRenderableWidget(search);

        int sx = this.width / 2 - 140;
        seconds = new EditBox(this.font, sx + 42, 28, 40, 18, Component.literal("Seconds"));
        seconds.setValue(Integer.toString(AuctionState.lastSeconds));
        addRenderableWidget(seconds);

        minBox = new EditBox(this.font, sx + 122, 28, 56, 18, Component.literal("Min bid"));
        minBox.setHint(Component.literal("none"));
        addRenderableWidget(minBox);

        maxBox = new EditBox(this.font, sx + 216, 28, 56, 18, Component.literal("Max bid"));
        maxBox.setHint(Component.literal("none"));
        addRenderableWidget(maxBox);

        setInitialFocus(search);
        refilter();
    }

    private int chosenSeconds() {
        String digits = seconds == null ? "" : seconds.getValue().replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 6) return AuctionState.lastSeconds;
        try {
            return AuctionState.clampSeconds(Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return AuctionState.lastSeconds;
        }
    }

    /** Parses "150k", "2.5m", "1,000", "" -> number (0 if empty/invalid). */
    private static long parseAmount(EditBox box) {
        if (box == null) return 0L;
        String t = box.getValue().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "").trim();
        if (t.isEmpty()) return 0L;
        long mult = 1L;
        char last = t.charAt(t.length() - 1);
        if (last == 'k') mult = 1_000L;
        else if (last == 'm') mult = 1_000_000L;
        else if (last == 'b') mult = 1_000_000_000L;
        if (mult != 1L) t = t.substring(0, t.length() - 1);
        try {
            double v = Double.parseDouble(t);
            if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) return 0L;
            return (long) (v * mult);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private void refilter() {
        String q = search == null ? "" : search.getValue().toLowerCase(Locale.ROOT).trim();
        filtered.clear();
        for (ItemStack s : ALL) {
            if (q.isEmpty() || s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(s);
            }
        }
        scrollRows = 0;
    }

    private int cols() { return Math.max(1, (this.width - 20) / CELL); }
    private int gridLeft() { return (this.width - cols() * CELL) / 2; }
    private int visibleRows() { return Math.max(1, (this.height - TOP - 20) / CELL); }
    private int totalRows() { return (filtered.size() + cols() - 1) / cols(); }

    private int indexAt(double mx, double my) {
        int gx = (int) (mx - gridLeft());
        int gy = (int) (my - TOP);
        if (gx < 0 || gy < 0) return -1;
        int c = gx / CELL, r = gy / CELL;
        if (c >= cols() || r >= visibleRows()) return -1;
        int idx = (r + scrollRows) * cols() + c;
        return idx < filtered.size() ? idx : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);

        int sx = this.width / 2 - 140;
        g.text(this.font, "Time(s)", sx, 33, 0xFFAAAAAA);
        g.text(this.font, "Min $", sx + 90, 33, 0xFFAAAAAA);
        g.text(this.font, "Max $", sx + 184, 33, 0xFFAAAAAA);

        int cols = cols(), left = gridLeft();
        int hovered = indexAt(mouseX, mouseY);

        for (int r = 0; r < visibleRows(); r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (r + scrollRows) * cols + c;
                if (idx >= filtered.size()) break;
                int x = left + c * CELL, y = TOP + r * CELL;
                if (idx == hovered) g.fill(x, y, x + CELL, y + CELL, 0x80FFFFFF);
                g.item(filtered.get(idx), x + 2, y + 2);
            }
        }

        if (filtered.isEmpty()) {
            g.centeredText(this.font, "No matching items", this.width / 2, TOP + 10, 0xFFAAAAAA);
        }
        if (hovered >= 0) {
            g.setTooltipForNextFrame(this.font, filtered.get(hovered), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == 0) {
            int idx = indexAt(event.x(), event.y());
            if (idx >= 0) {
                AuctionState.start(filtered.get(idx), chosenSeconds(), parseAmount(minBox), parseAmount(maxBox));
                Minecraft.getInstance().setScreen(null);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int max = Math.max(0, totalRows() - visibleRows());
        scrollRows = Math.max(0, Math.min(max, scrollRows - (int) Math.signum(sy)));
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
