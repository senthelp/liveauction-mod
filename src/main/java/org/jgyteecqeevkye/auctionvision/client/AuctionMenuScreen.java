package org.jgyteecqeevkye.auctionvision.client;

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
    private static final int TOP = 40;

    private static List<ItemStack> ALL;

    private final List<ItemStack> filtered = new ArrayList<>();
    private EditBox search;
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
        search = new EditBox(this.font, this.width / 2 - 100, 10, 200, 18, Component.literal("Search"));
        search.setHint(Component.literal("Search items..."));
        search.setResponder(s -> refilter());
        addRenderableWidget(search);
        setInitialFocus(search);
        refilter();
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
                AuctionState.start(filtered.get(idx));
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
