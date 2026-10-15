package de.z0rdak.yawp.core.flag;

import java.util.Objects;

public record BlockException(BlockSelector selector, int decaySeconds) {
    public static final int NEVER = 0;

    public static final int MAX_SECONDS = 30 * 24 * 60 * 60;

    public BlockException {
        Objects.requireNonNull(selector, "selector");
        if (decaySeconds < 0) {
            throw new IllegalArgumentException("Decay must not be negative, but got: " + decaySeconds);
        }
        if (decaySeconds > MAX_SECONDS) {
            throw new IllegalArgumentException("Decay must be at most " + MAX_SECONDS
                    + " seconds (30 days), but got: " + decaySeconds);
        }
    }

    public static BlockException permanent(BlockSelector selector) {
        return new BlockException(selector, NEVER);
    }

    public boolean decays() {
        return decaySeconds > NEVER;
    }

    public BlockException withDecay(int seconds) {
        return new BlockException(selector, seconds);
    }

    public long decayTicks() {
        return (long) decaySeconds * 20L;
    }

    @Override
    public String toString() {
        return decays() ? selector + " (" + decaySeconds + "s)" : selector.toString();
    }
}
