package org.jgyteecqeevkye.auctionvision.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws the card. Read-only with respect to the timer: it never modifies timing state. */
public final class AuctionHudRenderer {
    private AuctionHudRenderer() {}

    public static void render(GuiGraphicsExtractor g) {
        // Closes the card at the exact moment the original window reaches zero.
        if (!AuctionState.tickAlive()) return;

        Minecraft mc = Minecraft.getInstance();
        long remainingMs = AuctionState.remainingMs();
        double fraction = AuctionState.fraction();

        int cardW = 170, cardH = 74;
        int x = (g.guiWidth() - cardW) / 2, y = 6;
        int gold = 0xFFFFD700;

        g.fill(x, y, x + cardW, y + cardH, 0xC0263238);
        g.fill(x, y, x + cardW, y + 1, gold);
        g.fill(x, y + cardH - 1, x + cardW, y + cardH, gold);
        g.fill(x, y, x + 1, y + cardH, gold);
        g.fill(x + cardW - 1, y, x + cardW, y + cardH, gold);

        float bob = (float) (Math.sin(System.currentTimeMillis() / 350.0) * 3.0);
        g.pose().pushMatrix();
        g.pose().translate(x + 10, y + 12 + bob);
        g.pose().scale(3.0f, 3.0f);
        g.item(AuctionState.stack, 0, 0);
        g.pose().popMatrix();

        int tx = x + 68;
        g.text(mc.font, AuctionState.stack.getHoverName().getString(), tx, y + 9, 0xFFFFFFFF);
        g.text(mc.font, "Bid: $" + AuctionState.abbreviate(AuctionState.bid), tx, y + 24, gold);
        g.text(mc.font, "By: " + AuctionState.bidder, tx, y + 37, 0xFFCCCCCC);
        g.text(mc.font, String.format("%.1fs", remainingMs / 1000.0), tx, y + 50, 0xFFAAAAAA);

        int barX = x + 4, barY = y + cardH - 8, barMax = cardW - 8;
        int barW = (int) (barMax * fraction);
        int color = remainingMs < 7_000L ? 0xFFE53935 : 0xFF43A047;
        g.fill(barX, barY, barX + barMax, barY + 4, 0x80000000);
        g.fill(barX, barY, barX + barW, barY + 4, color);
    }
}
