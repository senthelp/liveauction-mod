package com.pathetictry.auctionvision.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** "Auction House" picker: search, tabs, favorites, big preview, time chips, min/max, START button. */
public class AuctionMenuScreen extends Screen {
    private static final int CELL = 22;
    private static final int GX = 12;
    private static final int GRID_TOP = 74;
    private static final int PANEL_TOP = 28;
    private static final String[] TABS = {"All", "Blocks", "Items", "Favs"};
    private static final int[] CHIPS = {15, 30, 60, 120};

    private static List<ItemStack> ALL;
    private static final Set<Item> FAVS = new HashSet<>(); // right-click an item to favorite (kept until you close the game)

    private final List<ItemStack> filtered = new ArrayList<>();
    private EditBox search, qtyBox, seconds, minBox, maxBox;
    private ItemStack selected = null;
    private int tab = 0;
    private int scrollRows = 0;

    public AuctionMenuScreen() {
        super(Component.literal("Auction House"));
        if (ALL == null) {
            List<ItemStack> list = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack s = item.getDefaultInstance();
                if (!s.isEmpty()) list.add(s);
            }
            ALL = list;
        }
    }

    // ---------- layout ----------
    private int rightW() { return Math.max(130, Math.min(160, this.width / 3)); }
    private int px() { return this.width - rightW() - 12; }
    private int panelBottom() { return this.height - 12; }
    private int gridW() { return Math.max(CELL, px() - GX - 20); }
    private int gridBottom() { return this.height - 22; }
    private int cols() { return Math.max(1, gridW() / CELL); }
    private int visibleRows() { return Math.max(1, (gridBottom() - GRID_TOP) / CELL); }
    private int totalRows() { return (filtered.size() + cols() - 1) / cols(); }
    private int settingsY() { return PANEL_TOP + 80; }
    private int startY() { return panelBottom() - 30; }

    @Override
    protected void init() {
        search = new EditBox(this.font, GX, 30, gridW(), 18, Component.literal("Search"));
        search.setHint(Component.literal("Search items..."));
        search.setResponder(s -> refilter());
        addRenderableWidget(search);

        int px = px(), rw = rightW(), y0 = settingsY();
        qtyBox = new EditBox(this.font, px + 56, y0, 44, 18, Component.literal("Quantity"));
        qtyBox.setValue("1");
        addRenderableWidget(qtyBox);

        seconds = new EditBox(this.font, px + 56, y0 + 20, 44, 18, Component.literal("Seconds"));
        seconds.setValue(Integer.toString(AuctionState.lastSeconds));
        addRenderableWidget(seconds);

        minBox = new EditBox(this.font, px + 56, y0 + 58, rw - 62, 18, Component.literal("Min bid"));
        minBox.setHint(Component.literal("none"));
        addRenderableWidget(minBox);

        maxBox = new EditBox(this.font, px + 56, y0 + 78, rw - 62, 18, Component.literal("Max bid"));
        maxBox.setHint(Component.literal("none"));
        addRenderableWidget(maxBox);

        setInitialFocus(search);
        refilter();
    }

    // ---------- logic ----------
    private int chosenSeconds() {
        String digits = seconds == null ? "" : seconds.getValue().replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 6) return AuctionState.lastSeconds;
        try {
            return AuctionState.clampSeconds(Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return AuctionState.lastSeconds;
        }
    }

    private int chosenQty() {
        String digits = qtyBox == null ? "" : qtyBox.getValue().replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 5) return 1;
        try {
            return AuctionState.clampQty(Integer.parseInt(digits));
        } catch (NumberFormatException e) {
            return 1;
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
            Item it = s.getItem();
            if (tab == 1 && !(it instanceof BlockItem)) continue;
            if (tab == 2 && (it instanceof BlockItem)) continue;
            if (tab == 3 && !FAVS.contains(it)) continue;
            if (!q.isEmpty() && !s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
            filtered.add(s);
        }
        if (tab == 0) filtered.sort(Comparator.comparingInt(s -> FAVS.contains(s.getItem()) ? 0 : 1));
        scrollRows = 0;
    }

    private void tryStart() {
        if (selected == null) return;
        AuctionState.start(selected, chosenSeconds(), parseAmount(minBox), parseAmount(maxBox), chosenQty());
        Minecraft.getInstance().setScreen(null);
    }

    private int indexAt(double mx, double my) {
        int gx = (int) (mx - GX);
        int gy = (int) (my - GRID_TOP);
        if (gx < 0 || gy < 0) return -1;
        int c = gx / CELL, r = gy / CELL;
        if (c >= cols() || r >= visibleRows()) return -1;
        int idx = (r + scrollRows) * cols() + c;
        return idx < filtered.size() ? idx : -1;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------- drawing helpers ----------
    private static int rainbow(float hue) {
        hue = hue - (float) Math.floor(hue);
        float h6 = hue * 6f;
        int i = (int) h6;
        float f = h6 - i;
        float q = 1f - f;
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = 1; g = f; b = 0; }
            case 1 -> { r = q; g = 1; b = 0; }
            case 2 -> { r = 0; g = 1; b = f; }
            case 3 -> { r = 0; g = q; b = 1; }
            case 4 -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        outline(g, x, y, w, h, border);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label,
                        int fill, int border, int textColor) {
        box(g, x, y, w, h, fill, border);
        g.centeredText(this.font, label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    // ---------- render ----------
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        long now = System.currentTimeMillis();
        float t = (now % 4000L) / 4000f;
        int W = this.width, H = this.height;
        int px = px(), rw = rightW(), gw = gridW();

        // ---- background (drawn BEFORE the widgets so text boxes stay on top) ----
        for (int y = 0; y < H; y += 4) {
            float k = y / (float) H;
            int r = (int) (12 + 22 * k), gg = (int) (14 + 6 * k), b = (int) (32 + 30 * (1 - k));
            g.fill(0, y, W, y + 4, 0xD0000000 | (r << 16) | (gg << 8) | b);
        }
        for (int i = 0; i < 28; i++) { // twinkling drifting sparkles
            int sx = (int) (((i * 73) % 100) / 100f * W);
            int sy = (int) (((((i * 37) % 100) / 100f * H) + (now / 40.0) * (0.3 + (i % 5) * 0.1)) % H);
            int a = (int) (60 + 60 * Math.sin(now / 300.0 + i));
            g.fill(sx, sy, sx + 2, sy + 2, (a << 24) | 0xFFFFFF);
        }
        box(g, GX - 4, GRID_TOP - 4, gw + 22, gridBottom() - GRID_TOP + 8, 0x60000000, 0x40FFFFFF);
        box(g, px, PANEL_TOP, rw, panelBottom() - PANEL_TOP, 0xA01A2430, rainbow(t));

        super.extractRenderState(g, mouseX, mouseY, partial); // draws the text boxes

        // ---- title ----
        g.pose().pushMatrix();
        g.pose().translate(GX, 6f);
        g.pose().scale(2f, 2f);
        g.text(this.font, "AUCTION HOUSE", 0, 0, 0xFFFFD700);
        g.pose().popMatrix();
        g.text(this.font, "pick an item, set the rules, hit START", GX + 168, 14, 0xFF8899AA);
        for (int x = GX; x < GX + gw; x += 4) {
            g.fill(x, 26, Math.min(x + 4, GX + gw), 27, rainbow(t + (x - GX) / 300f));
        }

        // ---- tabs ----
        for (int i = 0; i < TABS.length; i++) {
            int x = GX + i * 58, y = 52, w = 54, h = 16;
            boolean active = i == tab, hov = inside(mouseX, mouseY, x, y, w, h);
            int fill = active ? 0xFF3A4A5A : (hov ? 0xA0405060 : 0x80202A36);
            button(g, x, y, w, h, TABS[i], fill, active ? rainbow(t) : 0x40FFFFFF,
                   active ? 0xFFFFFFFF : 0xFFAABBCC);
        }

        // ---- grid ----
        int hovered = indexAt(mouseX, mouseY);
        int cols = cols();
        for (int r = 0; r < visibleRows(); r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (r + scrollRows) * cols + c;
                if (idx >= filtered.size()) break;
                ItemStack s = filtered.get(idx);
                int x = GX + c * CELL, y = GRID_TOP + r * CELL;
                boolean sel = selected != null && selected.getItem() == s.getItem();
                int bg = sel ? 0x60FFD700 : (idx == hovered ? 0x70FFFFFF : 0x50000000);
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, bg);
                g.item(s, x + 3, y + 3);
                if (FAVS.contains(s.getItem())) g.text(this.font, "*", x + CELL - 8, y + 2, 0xFFFFD700);
                if (idx == hovered) outline(g, x, y, CELL, CELL, rainbow(t + idx * 0.01f));
                if (sel) outline(g, x, y, CELL, CELL, 0xFFFFD700);
            }
        }
        if (filtered.isEmpty()) {
            g.text(this.font, tab == 3 ? "No favorites yet - right-click an item" : "No matching items",
                   GX + 6, GRID_TOP + 8, 0xFFAAAAAA);
        }
        // scrollbar
        int trackX = GX + gw + 4, trackH = gridBottom() - GRID_TOP;
        g.fill(trackX, GRID_TOP, trackX + 4, GRID_TOP + trackH, 0x40FFFFFF);
        int maxScroll = Math.max(0, totalRows() - visibleRows());
        if (maxScroll > 0) {
            int thumbH = Math.max(12, (int) (trackH * (visibleRows() / (double) totalRows())));
            int thumbY = GRID_TOP + (int) ((trackH - thumbH) * (scrollRows / (double) maxScroll));
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, rainbow(t));
        }

        // ---- right panel: big preview ----
        int boxX = px + (rw - 56) / 2, boxY = PANEL_TOP + 6;
        g.fill(boxX, boxY, boxX + 56, boxY + 56, 0x50000000);
        outline(g, boxX, boxY, 56, 56, (selected != null ? rainbow(t + 0.5f) : 0x40FFFFFF));
        if (selected != null) {
            float bob = (float) (Math.sin(now / 350.0) * 3.0);
            g.pose().pushMatrix();
            g.pose().translate(boxX + 4f, boxY + 4f + bob);
            g.pose().scale(3f, 3f);
            g.item(selected, 0, 0);
            g.pose().popMatrix();

            int q = chosenQty();
            if (q > 1) {
                String qt = "x" + q;
                g.text(this.font, qt, boxX + 54 - this.font.width(qt), boxY + 46, 0xFFFFFFFF);
            }
            String n = selected.getHoverName().getString();
            int nw = Math.max(1, this.font.width(n));
            float ns = Math.min(1f, (rw - 8f) / nw);
            g.pose().pushMatrix();
            g.pose().translate(px + rw / 2f - nw * ns / 2f, PANEL_TOP + 66f);
            g.pose().scale(ns, ns);
            g.text(this.font, n, 0, 0, 0xFFFFFFFF);
            g.pose().popMatrix();
        } else {
            g.centeredText(this.font, "Pick an item", px + rw / 2, boxY + 24, 0xFF8899AA);
        }

        // ---- settings ----
        int y0 = settingsY();
        g.text(this.font, "Qty", px + 6, y0 + 5, 0xFFAAAAAA);
        g.text(this.font, "Time (s)", px + 6, y0 + 25, 0xFFAAAAAA);
        int chipW = (rw - 12 - 9) / 4;
        int cur = chosenSeconds();
        for (int i = 0; i < CHIPS.length; i++) {
            int x = px + 6 + i * (chipW + 3), y = y0 + 40;
            boolean active = cur == CHIPS[i], hov = inside(mouseX, mouseY, x, y, chipW, 14);
            button(g, x, y, chipW, 14, CHIPS[i] + "s",
                   active ? 0xFF3A4A5A : (hov ? 0xA0405060 : 0x80202A36),
                   active ? rainbow(t) : 0x40FFFFFF, active ? 0xFFFFFFFF : 0xFFAABBCC);
        }
        g.text(this.font, "Min $", px + 6, y0 + 63, 0xFF80D8FF);
        g.text(this.font, "Max $", px + 6, y0 + 83, 0xFFFFB74D);

        // ---- START button ----
        int sx = px + 6, sy = startY(), sw = rw - 12;
        boolean can = selected != null, shov = can && inside(mouseX, mouseY, sx, sy, sw, 24);
        button(g, sx, sy, sw, 24, "START AUCTION",
               can ? (shov ? 0xFF2E9E3E : 0xFF237A31) : 0xFF3A3A3A,
               can ? rainbow(t + 0.25f) : 0x40FFFFFF, can ? 0xFFFFFFFF : 0xFF888888);

        // ---- footer ----
        g.text(this.font, filtered.size() + " items   |   click: select   double-click: start   right-click: favorite",
               GX, this.height - 14, 0xFF8899AA);

        // tiny credit, bottom-right
        String credit = "made by PatheticTry";
        float cw = this.font.width(credit) * 0.75f;
        g.pose().pushMatrix();
        g.pose().translate(W - 12 - cw, H - 10f);
        g.pose().scale(0.75f, 0.75f);
        g.text(this.font, credit, 0, 0, 0xFFB0BEC5);
        g.pose().popMatrix();

        if (hovered >= 0) {
            g.setTooltipForNextFrame(this.font, filtered.get(hovered), mouseX, mouseY);
        }
    }

    // ---------- input ----------
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        double mx = event.x(), my = event.y();
        int b = event.button();

        if (b == 0) {
            for (int i = 0; i < TABS.length; i++) {
                if (inside(mx, my, GX + i * 58, 52, 54, 16)) { tab = i; refilter(); return true; }
            }
            int px = px(), rw = rightW(), y0 = settingsY();
            int chipW = (rw - 12 - 9) / 4;
            for (int i = 0; i < CHIPS.length; i++) {
                if (inside(mx, my, px + 6 + i * (chipW + 3), y0 + 40, chipW, 14)) {
                    seconds.setValue(Integer.toString(CHIPS[i]));
                    return true;
                }
            }
            if (inside(mx, my, px + 6, startY(), rw - 12, 24)) { tryStart(); return true; }
        }

        int idx = indexAt(mx, my);
        if (idx >= 0) {
            ItemStack s = filtered.get(idx);
            if (b == 1) {
                if (!FAVS.remove(s.getItem())) FAVS.add(s.getItem());
                if (tab == 3) refilter();
                return true;
            }
            if (b == 0) {
                selected = s;
                if (doubleClick) tryStart();
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
