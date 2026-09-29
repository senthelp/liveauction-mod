package org.jgyteecqeevkye.auctionvision.client;

import net.minecraft.world.item.ItemStack;

/**
 * Auction data. TIMER RULES (strict):
 *  - timerEnd is written in exactly ONE place: start().
 *  - offer() may change bid/bidder only. It never reads-then-writes, extends or resets timerEnd.
 *  - The clock is System.nanoTime() (monotonic), so wall-clock changes cannot alter it.
 *  - A bid arriving at or after timerEnd is rejected and the auction is closed.
 */
public final class AuctionState {
    public static final long DURATION_MS = 30_000L;
    private static final long DURATION_NS = DURATION_MS * 1_000_000L;

    public static volatile boolean active = false;
    public static ItemStack stack = ItemStack.EMPTY;
    public static volatile long bid = 0L;
    public static volatile String bidder = "No bids yet";

    /** Absolute nanoTime deadline. Only start() assigns it. */
    private static long timerEnd = 0L;

    private AuctionState() {}

    public static synchronized void start(ItemStack item) {
        stack = item.copy();
        bid = 0L;
        bidder = "No bids yet";
        timerEnd = System.nanoTime() + DURATION_NS; // the ONLY write to timerEnd
        active = true;
    }

    /** True once the original window has elapsed (exact: now >= timerEnd). */
    public static synchronized boolean isExpired() {
        return System.nanoTime() - timerEnd >= 0L;
    }

    /** Closes the card if the window has elapsed. Returns true if the auction is still live. */
    public static synchronized boolean tickAlive() {
        if (!active) return false;
        if (isExpired()) { active = false; return false; }
        return true;
    }

    public static synchronized long remainingNanos() {
        return Math.max(0L, timerEnd - System.nanoTime());
    }

    public static long remainingMs() { return remainingNanos() / 1_000_000L; }

    /** 1.0 -> 0.0 across the original 30s window. */
    public static double fraction() { return remainingNanos() / (double) DURATION_NS; }

    /**
     * Updates bid + bidder instantly if strictly higher and the window is still open.
     * Never touches timerEnd.
     */
    public static synchronized boolean offer(String sender, long amount) {
        if (!tickAlive()) return false;      // expired -> closed, bid rejected
        if (amount <= bid) return false;
        bid = amount;
        bidder = sender;
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
