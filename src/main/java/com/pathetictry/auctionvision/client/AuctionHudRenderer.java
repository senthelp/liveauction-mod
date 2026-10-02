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

    // ---------- colour helpers ----------
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

    private static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (((a >> 16) & 0xFF) + ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t));
        int g = (int) (((a >> 8) & 0xFF) + ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t));
        int bl = (int) ((a & 0xFF) + (((b & 0xFF) - (a & 0xFF)) * t));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int alpha(int rgb, int a) {
        return ((Math.max(0, Math.min(255, a))) << 24) | (rgb & 0xFFFFFF);
    }

    private static void ring(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static void scaledText(GuiGraphicsExtractor g, Minecraft mc, String s, float x, float y, float sc, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        g.text(mc.font, s, 0, 0, color);
        g.pose().popMatrix();
    }

    // ---------- render ----------
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
        boolean urgent = live && remainingMs < 7_000L;

        // ---- text ----
        int qty = AuctionState.quantity;
        String name = (qty > 1 ? qty + "x " : "") + AuctionState.stack.getHoverName().getString();
        String bidLine = "Bid: $" + AuctionState.abbreviate(AuctionState.bid);
        String byLine = (sold ? "Won: " : "By: ") + AuctionState.bidder;
        String timeLine = live ? String.format("%.1fs", remainingMs / 1000.0) : ENDED_TEXT;
        String minTxt = AuctionState.minBid > 0 ? "Min $" + AuctionState.abbreviate(AuctionState.minBid) : null;
        String maxTxt = AuctionState.maxBid > 0 ? "Max $" + AuctionState.abbreviate(AuctionState.maxBid) : null;
        boolean hasLimits = minTxt != null || maxTxt != null;
        String limitsAll = (minTxt == null ? "" : minTxt) + (minTxt != null && maxTxt != null ? "  " : "")
                         + (maxTxt == null ? "" : maxTxt);

        float bidScale = 1.35f;
        int textW = Math.max(Math.max(mc.font.width(name), (int) (mc.font.width(bidLine) * bidScale)),
                             Math.max(mc.font.width(byLine), mc.font.width(ENDED_TEXT)));
        if (hasLimits) textW = Math.max(textW, mc.font.width(limitsAll));
        int tx = 48;
        int cardW = Math.max(134, tx + textW + 10);
        int cardH = hasLimits ? 88 : 77;

        // ---- border colour (rainbow / urgent red / sold gold / ended gray) ----
        float tt = (now % 3000L) / 3000f;
        int border;
        if (sold) border = lerp(0xFFD700, 0xFFF59D, (float) (Math.sin(now / 150.0) * 0.5 + 0.5));
        else if (!live) border = 0xFF78909C;
        else if (urgent) border = lerp(0x7A1010, 0xFF3030, (float) (Math.sin(now / 100.0) * 0.5 + 0.5));
        else border = rainbow(tt);

        // ---- position (+ little shake in the last 3 seconds) ----
        float shake = (live && remainingMs < 3_000L) ? (float) (Math.sin(now / 35.0) * 0.8) : 0f;
        float screenX = (g.guiWidth() - cardW * S) / 2f + shake;

        g.pose().pushMatrix();
        g.pose().translate(screenX, 4f);
        g.pose().scale(S, S);

        // glow halo around the card
        ring(g, -1, -1, cardW + 2, cardH + 2, alpha(border, 0x70));
        ring(g, -2, -2, cardW + 4, cardH + 4, alpha(border, 0x38));
        ring(g, -3, -3, cardW + 6, cardH + 6, alpha(border, 0x18));

        // body: vertical gradient glass
        for (int yy = 0; yy < cardH; yy += 2) {
            int c = lerp(0x2E3D4F, 0x131A24, yy / (float) cardH);
            g.fill(0, yy, cardW, Math.min(yy + 2, cardH), alpha(c, 0xE4));
        }

        // header strip
        g.fill(0, 0, cardW, 12, 0x70000000);
        if (live) {
            int dot = urgent ? 0xFFFF3B3B : 0xFFFF5252;
            if (((now / 450) % 2) == 0 || urgent) g.fill(5, 4, 9, 8, dot);
            scaledText(g, mc, urgent ? "ENDING!" : "LIVE AUCTION", 12f, 2.5f, 0.75f, urgent ? 0xFFFF8A80 : 0xFFFFFFFF);
        } else if (sold) {
            scaledText(g, mc, "SOLD!", 6f, 2.5f, 0.85f, 0xFFFFD700);
        } else {
            scaledText(g, mc, "NO BIDS", 6f, 2.5f, 0.75f, 0xFFB0BEC5);
        }

        // "+$" popup in the header when a higher bid just arrived
        long age = now - AuctionState.lastBidMs;
        if (live && AuctionState.lastBidMs > 0 && age < 1600L && AuctionState.lastDelta > 0) {
            String up = "+$" + AuctionState.abbreviate(AuctionState.lastDelta);
            int a = (int) (255 * (1f - age / 1600f));
            float uw = mc.font.width(up) * 0.85f;
            scaledText(g, mc, up, cardW - uw - 5f, 2.5f, 0.85f, alpha(0x69F0AE, a));
        }

        // item pedestal: pulsing rainbow glow + floating item + orbiting sparkles
        int glow = rainbow(tt + 0.3f);
        int pulse = (int) (0x34 + 0x22 * Math.sin(now / 280.0));
        g.fill(6, 17, 44, 55, alpha(glow, pulse));
        ring(g, 6, 17, 38, 38, alpha(glow, 0x90));
        float bob = (float) (Math.sin(now / 350.0) * 2.0);
        g.pose().pushMatrix();
        g.pose().translate(10f, 21f + bob);
        g.pose().scale(2.0f, 2.0f);
        g.item(AuctionState.stack, 0, 0);
        g.pose().popMatrix();
        if (qty > 1) {
            String qt = "x" + qty;
            int qw = mc.font.width(qt);
            g.text(mc.font, qt, 43 - qw + 1, 47, 0xFF000000);
            g.text(mc.font, qt, 43 - qw, 46, 0xFFFFFFFF);
        }
        if (live) {
            for (int i = 0; i < 3; i++) {
                double ang = now / 600.0 + i * (Math.PI * 2 / 3);
                int ox = (int) (25 + Math.cos(ang) * 17), oy = (int) (36 + Math.sin(ang) * 17);
                g.fill(ox, oy, ox + 2, oy + 2, 0xCCFFFFFF);
            }
        }

        // lines
        g.text(mc.font, name, tx + 1, 17, 0xFF000000);
        g.text(mc.font, name, tx, 16, 0xFFFFFFFF);
        boolean flash = AuctionState.lastBidMs > 0 && now - AuctionState.lastBidMs < 350L;
        scaledText(g, mc, bidLine, tx, 27f, bidScale, flash ? 0xFFFFFFFF : 0xFFFFD700);
        g.text(mc.font, byLine, tx, 43, sold ? 0xFFFFF59D : 0xFFCCCCCC);
        g.text(mc.font, timeLine, tx, 53, live ? (urgent ? 0xFFFF6E6E : 0xFFB0BEC5) : 0xFFE53935);
        if (hasLimits) {
            int lx = tx;
            if (minTxt != null) {
                g.text(mc.font, minTxt, lx, 64, 0xFF80D8FF);
                lx += mc.font.width(minTxt + "  ");
            }
            if (maxTxt != null) g.text(mc.font, maxTxt, lx, 64, 0xFFFFB74D);
        }

        // countdown bar: green -> yellow -> red, with a highlight line
        int barX = 4, barY = cardH - 15, barMax = cardW - 8;
        int barW = (int) (barMax * fraction);
        float f = (float) fraction;
        int barColor = f > 0.5f ? lerp(0xFFC107, 0x43A047, (f - 0.5f) * 2f) : lerp(0xE53935, 0xFFC107, f * 2f);
        g.fill(barX, barY, barX + barMax, barY + 4, 0x90000000);
        g.fill(barX, barY, barX + barW, barY + 4, barColor);
        g.fill(barX, barY, barX + barW, barY + 1, 0x55FFFFFF);

        // moving shine sweep across the glass
        if (live) {
            float p = (now % 2600L) / 2600f;
            for (int yy = 0; yy < cardH; yy += 2) {
                int x0 = (int) (p * (cardW + 40) - 20 + yy * 0.35f);
                int xa = Math.max(1, x0), xb = Math.min(cardW - 1, x0 + 9);
                if (xb > xa) g.fill(xa, yy, xb, Math.min(yy + 2, cardH - 1), 0x16FFFFFF);
            }
        }

        // new-bid flash
        if (AuctionState.lastBidMs > 0 && now - AuctionState.lastBidMs < 450L) {
            int a = (int) (90 * (1f - (now - AuctionState.lastBidMs) / 450f));
            g.fill(0, 0, cardW, cardH, alpha(0xFFFFFF, a));
        }

        // confetti when sold
        if (sold) {
            for (int i = 0; i < 26; i++) {
                int cx = (i * 53) % Math.max(1, cardW - 4) + 1;
                int cy = (int) (((now / 14) + i * 37) % (cardH + 16)) - 8;
                if (cy > 0 && cy < cardH - 3) g.fill(cx, cy, cx + 2, cy + 2, rainbow(i / 26f + tt));
            }
        }

        // border
        ring(g, 0, 0, cardW, cardH, border);

        // small credit inside the card: thin strip along the bottom edge
        float cs = 0.62f;
        float cwid = mc.font.width(CREDIT) * cs;
        g.fill(1, cardH - 9, cardW - 1, cardH - 1, 0x40000000);
        g.fill(1, cardH - 9, cardW - 1, cardH - 8, alpha(border, 0x50));
        scaledText(g, mc, CREDIT, cardW - cwid - 6f, cardH - 7.2f, cs, 0xFFB0BEC5);

        g.pose().popMatrix();
    }
}
