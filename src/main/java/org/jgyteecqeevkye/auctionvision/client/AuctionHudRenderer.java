package org.jgyteecqeevkye.auctionvision.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws the card. Read-only with respect to the timer: it never modifies timing state. */
public final class AuctionHudRenderer {
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
        // Closes the card at the exact moment the original window reaches zero.
        if (!AuctionState.tickAlive()) return;

        Minecraft mc = Minecraft.getInstance();
        long remainingMs = AuctionState.remainingMs();
        double fraction = AuctionState.fraction();

        int cardW = 150, cardH = 56;
        int x = (g.guiWidth() - cardW) / 2, y = 4;

        g.fill(x, y, x + cardW, y + cardH, 0xC0263238);

        // color-cycling 1px border: each side is offset in hue, whole thing rotates over 3s
        float t = (System.currentTimeMillis() % 3000L) / 3000f;
        g.fill(x, y, x + cardW, y + 1, rainbow(t));                       // top
        g.fill(x + cardW - 1, y, x + cardW, y + cardH, rainbow(t + 0.25f)); // right
        g.fill(x, y + cardH - 1, x + cardW, y + cardH, rainbow(t + 0.5f));  // bottom
        g.fill(x, y, x + 1, y + cardH, rainbow(t + 0.75f));                 // left

        // floating item (2x), sine-wave bob
        float bob = (float) (Math.sin(System.currentTimeMillis() / 350.0) * 2.0);
        g.pose().pushMatrix();
        g.pose().translate(x + 8, y + 8 + bob);
        g.pose().scale(2.0f, 2.0f);
        g.item(AuctionState.stack, 0, 0);
        g.pose().popMatrix();

        int tx = x + 46;
        g.text(mc.font, cap(AuctionState.stack.getHoverName().getString(), 16), tx, y + 5, 0xFFFFFFFF);
        g.text(mc.font, "Bid: $" + AuctionState.abbreviate(AuctionState.bid), tx, y + 16, 0xFFFFD700);
        g.text(mc.font, "By: " + cap(AuctionState.bidder, 12), tx, y + 27, 0xFFCCCCCC);
        g.text(mc.font, String.format("%.1fs", remainingMs / 1000.0), tx, y + 38, 0xFFAAAAAA);

        // shrinking countdown bar, red under 7s
        int barX = x + 4, barY = y + cardH - 7, barMax = cardW - 8;
        int barW = (int) (barMax * fraction);
        int color = remainingMs < 7_000L ? 0xFFE53935 : 0xFF43A047;
        g.fill(barX, barY, barX + barMax, barY + 3, 0x80000000);
        g.fill(barX, barY, barX + barW, barY + 3, color);
    }
}
