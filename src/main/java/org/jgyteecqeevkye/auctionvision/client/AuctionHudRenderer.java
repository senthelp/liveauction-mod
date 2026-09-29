package org.jgyteecqeevkye.auctionvision.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws the card. Read-only with respect to the timer: it never modifies timing state. */
public final class AuctionHudRenderer {
    private static boolean wasDown = false;

    private AuctionHudRenderer() {}

    /** Hue 0..1 -> opaque ARGB (full saturation, full brightness). */
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

    private static String cap(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!AuctionState.visible) return;

        // Timer check: closes the BIDDING window at the exact moment the original time hits zero.
        // The card itself stays visible (showing the result) until the X is clicked.
        boolean live = AuctionState.tickAlive();

        Minecraft mc = Minecraft.getInstance();
        long remainingMs = live ? AuctionState.remainingMs() : 0L;
        double fraction = live ? AuctionState.fraction() : 0.0;

        int cardW = 150, cardH = 56;
        int x = (g.guiWidth() - cardW) / 2, y = 4;

        g.fill(x, y, x + cardW, y + cardH, 0xC0263238);

        // one color-cycling border, all four sides in sync (3s per full cycle)
        int border = rainbow((System.currentTimeMillis() % 3000L) / 3000f);
        g.fill(x, y, x + cardW, y + 1, border);
        g.fill(x, y + cardH - 1, x + cardW, y + cardH, border);
        g.fill(x, y, x + 1, y + cardH, border);
        g.fill(x + cardW - 1, y, x + cardW, y + cardH, border);

        // X button (top-right). Clickable while a screen is open (e.g. press T for chat).
        int bx = x + cardW - 12, by = y + 3, bs = 9;
        boolean hover = false;
        if (mc.screen != null && mc.getWindow().getScreenWidth() > 0) {
            double scale = (double) g.guiWidth() / mc.getWindow().getScreenWidth();
            double mx = mc.mouseHandler.xpos() * scale;
            double my = mc.mouseHandler.ypos() * scale;
            hover = mx >= bx && mx < bx + bs && my >= by && my < by + bs;
            boolean down = mc.mouseHandler.isLeftPressed();
            if (down && !wasDown && hover) {
                AuctionState.dismiss();
                wasDown = down;
                return;
            }
            wasDown = down;
        } else {
            wasDown = mc.mouseHandler.isLeftPressed();
        }
        if (hover) g.fill(bx, by, bx + bs, by + bs, 0x80E53935);
        g.text(mc.font, "x", bx + 2, by + 1, hover ? 0xFFFFFFFF : 0xFFCCCCCC);

        // floating item (2x), sine-wave bob
        float bob = (float) (Math.sin(System.currentTimeMillis() / 350.0) * 2.0);
        g.pose().pushMatrix();
        g.pose().translate(x + 8, y + 8 + bob);
        g.pose().scale(2.0f, 2.0f);
        g.item(AuctionState.stack, 0, 0);
        g.pose().popMatrix();

        int tx = x + 46;
        g.text(mc.font, cap(AuctionState.stack.getHoverName().getString(), 12), tx, y + 5, 0xFFFFFFFF);
        g.text(mc.font, "Bid: $" + AuctionState.abbreviate(AuctionState.bid), tx, y + 16, 0xFFFFD700);
        boolean hasBid = AuctionState.bid > 0;
        String who = live ? "By: " : (hasBid ? "Won: " : "By: ");
        g.text(mc.font, who + cap(AuctionState.bidder, 11), tx, y + 27, 0xFFCCCCCC);
        if (live) {
            g.text(mc.font, String.format("%.1fs", remainingMs / 1000.0), tx, y + 38, 0xFFAAAAAA);
        } else {
            g.text(mc.font, "ENDED", tx, y + 38, 0xFFE53935);
        }

        // shrinking countdown bar, red under 7s
        int barX = x + 4, barY = y + cardH - 7, barMax = cardW - 8;
        int barW = (int) (barMax * fraction);
        int color = remainingMs < 7_000L ? 0xFFE53935 : 0xFF43A047;
        g.fill(barX, barY, barX + barMax, barY + 3, 0x80000000);
        g.fill(barX, barY, barX + barW, barY + 3, color);
    }
}
