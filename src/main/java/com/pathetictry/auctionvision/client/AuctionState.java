package com.pathetictry.auctionvision.client;

import net.minecraft.world.item.ItemStack;

/**
 * Auction data. TIMER RULES (strict):
 *  - The duration is chosen once, when the auction is launched, in start().
 *  - timerEnd is written in exactly ONE place: start().
 *  - offer() may change bid/bidder only. It never extends or resets timerEnd.
 *  - The clock is System.nanoTime() (monotonic).
 *  - A bid arriving at or after timerEnd is rejected and the auction is closed.
 * Min/max: bids below minBid are ignored. A bid >= maxBid (buy-now) wins and ends the auction at once.
 */
public final class AuctionState {
    public static final int DEFAULT_SECONDS = 30;
    public static final int MIN_SECONDS = 5;
    public static final int MAX_SECONDS = 600;

    /** true while the bidding window is open. */
    public static volatile boolean active = false;
    /** true while the card is shown; stays true after the auction ends until /cancel. */
    public static volatile boolean visible = false;
    public static ItemStack stack = ItemStack.EMPTY;
    public static volatile long bid = 0L;
    public static volatile String bidder = "No bids yet";

    /** 0 = no limit. */
    public static volatile long minBid = 0L;
    public static volatile long maxBid = 0L;

    public static int lastSeconds = DEFAULT_SECONDS;

    private static long durationNs = DEFAULT_SECONDS * 1_000_000_000L;
    /** Absolute nanoTime deadline. Only start() assigns it. */
    private static long timerEnd = 0L;
    private static boolean announced = true;

    private AuctionState() {}

    public static int clampSeconds(int s) {
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, s));
    }

    public static synchronized void start(ItemStack item, int seconds, long min, long max) {
        int s = clampSeconds(seconds);
        lastSeconds = s;
        durationNs = s * 1_000_000_000L;
        stack = item.copy();
        bid = 0L;
        bidder = "No bids yet";
        minBid = Math.max(0L, min);
        maxBid = Math.max(0L, max);
        if (maxBid > 0 && maxBid < minBid) maxBid = minBid;
        timerEnd = System.nanoTime() + durationNs; // the ONLY write to timerEnd
        announced = false;
        active = true;
        visible = true;
    }

    /** /cancel: hides the card, ends the auction if running, and suppresses the winner message. */
    public static synchronized void dismiss() {
        active = false;
        visible = false;
        announced = true;
    }

    /** True exactly once after an auction ends on its own (time out or buy-now). */
    public static synchronized boolean consumeEnd() {
        if (visible && !active && !announced) {
            announced = true;
            return true;
        }
        return false;
    }

    public static synchronized boolean isExpired() {
        return System.nanoTime() - timerEnd >= 0L;
    }

    /** Closes the bidding window if time is up. Returns true if the auction is still live. */
    public static synchronized boolean tickAlive() {
        if (!active) return false;
        if (isExpired()) { active = false; return false; }
        return true;
    }

    public static synchronized long remainingNanos() {
        return Math.max(0L, timerEnd - System.nanoTime());
    }

    public static long remainingMs() { return remainingNanos() / 1_000_000L; }

    public static synchronized double fraction() { return remainingNanos() / (double) durationNs; }

    /** Updates bid + bidder if allowed. Never touches timerEnd. */
    public static synchronized boolean offer(String sender, long amount) {
        if (!tickAlive()) return false;
        if (minBid > 0 && amount < minBid) return false; // below minimum
        if (amount <= bid) return false;
        bid = amount;
        bidder = sender;
        if (maxBid > 0 && amount >= maxBid) active = false; // buy-now reached: sold
        return true;
    }

    public static String abbreviate(long v) {
        if (v >= 1_000_000_000L) return trim(v / 1_000_000_000.0) + "B";
        if (v >= 1_000_000L) return trim(v / 1_000_000.0) + "M";
        if (v >= 1_000L) return trim(v / 1_000.0) + "k";
        return Long.toString(v);
    }

    private static String trim(double d) {
        return String.format(java.util.Locale.ROOT, "%.2f", d).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
