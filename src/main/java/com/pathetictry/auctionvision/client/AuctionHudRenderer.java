package com.pathetictry.auctionvision.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Draws the card. Read-only with respect to the timer: it never modifies timing state. */
public final class AuctionHudRenderer {
    /** Overall card scale. */
    private static final float S = 1.2f;
    private static final String ENDED_TEXT = "ENDED (/cancel)";

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

    public static void render(GuiGraphicsExtractor g) {
        if (!AuctionState.visible) return;

        // Closes the BIDDING window at the exact moment the original time hits zero.
        // The card stays visible (showing the result) until /cancel is used.
        boolean live = AuctionState.tickAlive();

        Minecraft mc = Minecraft.getInstance();

        // one-time winner message in chat when the auction ends by itself
        if (!live && AuctionState.consumeEnd()) {
            String item = AuctionState.stack.getHoverName().getString();
            String msg = AuctionState.bid > 0
                ? "[Auction] Sold " + item + " to " + AuctionState.bidder + " for $" + AuctionState.abbreviate(AuctionState.bid)
                : "[Auction] " + item + " ended with no bids.";
            mc.gui.getChat().addClientSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GOLD));
        }

        long remainingMs = live ? AuctionState.remainingMs() : 0L;
        double fraction = live ? AuctionState.fraction() : 0.0;

        // text lines (full names, never shortened)
        String name = AuctionState.stack.getHoverName().getString();
        String bidLine = "Bid: $" + AuctionState.abbreviate(AuctionState.bid);
        String who = (!live && AuctionState.bid > 0) ? "Won: " : "By: ";
        String byLine = who + AuctionState.bidder;
        String timeLine = live ? String.format("%.1fs", remainingMs / 1000.0) : ENDED_TEXT;

        // card width = widest line (ended text always counted so the card doesn't jump at the end)
        int textW = Math.max(Math.max(mc.font.width(name), mc.font.width(bidLine)),
                             Math.max(mc.font.width(byLine), mc.font.width(ENDED_TEXT)));
        int tx = 44;
        int cardW = Math.max(110, tx + textW + 8);
        int cardH = 54;

        float screenX = (g.guiWidth() - cardW * S) / 2f;

        g.pose().pushMatrix();
        g.pose().translate(screenX, 4f);
        g.pose().scale(S, S);

        g.fill(0, 0, cardW, cardH, 0xC0263238);

        // one color-cycling border, all four sides in sync (3s per full cycle)
        int border = rainbow((System.currentTimeMillis() % 3000L) / 3000f);
        g.fill(0, 0, cardW, 1, border);
        g.fill(0, cardH - 1, cardW, cardH, border);
        g.fill(0, 0, 1, cardH, border);
        g.fill(cardW - 1, 0, cardW, cardH, border);

        // floating item (2x local), sine-wave bob
        float bob = (float) (Math.sin(System.currentTimeMillis() / 350.0) * 2.0);
        g.pose().pushMatrix();
        g.pose().translate(7f, 7f + bob);
        g.pose().scale(2.0f, 2.0f);
        g.item(AuctionState.stack, 0, 0);
        g.pose().popMatrix();

        g.text(mc.font, name, tx, 5, 0xFFFFFFFF);
        g.text(mc.font, bidLine, tx, 16, 0xFFFFD700);
        g.text(mc.font, byLine, tx, 27, 0xFFCCCCCC);
        g.text(mc.font, timeLine, tx, 38, live ? 0xFFAAAAAA : 0xFFE53935);

        // shrinking countdown bar, red under 7s
        int barX = 4, barY = cardH - 6, barMax = cardW - 8;
        int barW = (int) (barMax * fraction);
        int color = remainingMs < 7_000L ? 0xFFE53935 : 0xFF43A047;
        g.fill(barX, barY, barX + barMax, barY + 3, 0x80000000);
        g.fill(barX, barY, barX + barW, barY + 3, color);

        g.pose().popMatrix();
    }
}
