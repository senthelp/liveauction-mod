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
    private static final String CREDIT = "made by PatheticTry";

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

    private static void scaledText(GuiGraphicsExtractor g, Minecraft mc, String s, float x, float y, float sc, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        g.text(mc.font, s, 0, 0, color);
        g.pose().popMatrix();
    }

    public static void render(GuiGraphicsExtractor g) {
        if (!AuctionState.visible) return;

        // Closes the BIDDING window at the exact moment the original time hits zero.
        // The card stays visible (showing the result) until /cancel is used.
        boolean live = AuctionState.tickAlive();
        Minecraft mc = Minecraft.getInstance();

        // one-time winner message in chat when the auction ends by itself
        if (!live && AuctionState.consumeEnd()) {
            int qn = AuctionState.quantity;
            String item = (qn > 1 ? qn + "x " : "") + AuctionState.stack.getHoverName().getString();
            String msg = AuctionState.bid > 0
                ? "[Auction] Sold " + item + " to " + AuctionState.bidder + " for $" + AuctionState.abbreviate(AuctionState.bid)
                : "[Auction] " + item + " ended with no bids.";
            mc.gui.getChat().addClientSystemMessage(Component.literal(msg).withStyle(ChatFormatting.GOLD));
        }

        long now = System.currentTimeMillis();
        long remainingMs = live ? AuctionState.remainingMs() : 0L;
        double fraction = live ? AuctionState.fraction() : 0.0;
        boolean sold = !live && AuctionState.bid > 0;

        // ---- text ----
        int qty = AuctionState.quantity;
        String name = (qty > 1 ? qty + "x " : "") + AuctionState.stack.getHoverName().getString();
        String bidLine = "Bid: $" + AuctionState.abbreviate(AuctionState.bid);
        String byLine = (sold ? "Won: " : "By: ") + AuctionState.bidder;
        String timeLine = live ? String.format("%.1fs", remainingMs / 1000.0) : ENDED_TEXT;

        String minTxt = AuctionState.minBid > 0 ? "Min $" + AuctionState.abbreviate(AuctionState.minBid) : null;
        String maxTxt = AuctionState.maxBid > 0 ? "Max $" + AuctionState.abbreviate(AuctionState.maxBid) : null;
        boolean hasLimits = minTxt != null || maxTxt != null;
        String limits = (minTxt == null ? "" : minTxt) + (minTxt != null && maxTxt != null ? "   " : "")
                      + (maxTxt == null ? "" : maxTxt);

        float bidScale = 1.25f;
        int textW = Math.max(Math.max(mc.font.width(name), (int) (mc.font.width(bidLine) * bidScale)),
                             Math.max(mc.font.width(byLine), mc.font.width(ENDED_TEXT)));
        if (hasLimits) textW = Math.max(textW, mc.font.width(limits));
        int tx = 44;
        int cardW = Math.max(120, tx + textW + 8);
        int barY = hasLimits ? 61 : 51;
        int cardH = barY + 13;

        float screenX = (g.guiWidth() - cardW * S) / 2f;
        g.pose().pushMatrix();
        g.pose().translate(screenX, 4f);
        g.pose().scale(S, S);

        // slate card + thin colour-cycling border (all four sides in sync; gray once ended)
        g.fill(0, 0, cardW, cardH, 0xC0263238);
        int border = live ? rainbow((now % 3000L) / 3000f) : 0xFF78909C;
        g.fill(0, 0, cardW, 1, border);
        g.fill(0, cardH - 1, cardW, cardH, border);
        g.fill(0, 0, 1, cardH, border);
        g.fill(cardW - 1, 0, cardW, cardH, border);

        // floating item (2x) + quantity badge
        float bob = (float) (Math.sin(now / 350.0) * 2.0);
        g.pose().pushMatrix();
        g.pose().translate(7f, 9f + bob);
        g.pose().scale(2.0f, 2.0f);
        g.item(AuctionState.stack, 0, 0);
        g.pose().popMatrix();
        if (qty > 1) {
            String qt = "x" + qty;
            int qw = mc.font.width(qt);
            g.text(mc.font, qt, 40 - qw + 1, 34, 0xFF000000);
            g.text(mc.font, qt, 40 - qw, 33, 0xFFFFFFFF);
        }

        // lines
        g.text(mc.font, name, tx, 5, 0xFFFFFFFF);
        scaledText(g, mc, bidLine, tx, 16f, bidScale, 0xFFFFD700);
        g.text(mc.font, byLine, tx, 29, 0xFFCCCCCC);
        g.text(mc.font, timeLine, tx, 40, live ? 0xFFAAAAAA : 0xFFE53935);
        if (hasLimits) g.text(mc.font, limits, tx, 50, 0xFF8FA3AD);

        // countdown bar: green, red under 7s
        int barX = 4, barMax = cardW - 8;
        int barW = (int) (barMax * fraction);
        int color = remainingMs < 7_000L ? 0xFFE53935 : 0xFF43A047;
        g.fill(barX, barY, barX + barMax, barY + 3, 0x80000000);
        g.fill(barX, barY, barX + barW, barY + 3, color);

        // small credit, bottom-right inside the card
        float cs = 0.55f;
        float cwid = mc.font.width(CREDIT) * cs;
        scaledText(g, mc, CREDIT, cardW - cwid - 5f, barY + 6f, cs, 0xFF8FA3AD);

        g.pose().popMatrix();
    }
}
