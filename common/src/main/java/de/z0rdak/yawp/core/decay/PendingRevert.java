package de.z0rdak.yawp.core.decay;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

public record PendingRevert(String dimension, long packedPos, CompoundTag expected,
                            CompoundTag revertTo, long dueTick) {
    public PendingRevert {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(revertTo, "revertTo");
        if (dimension.isBlank()) {
            throw new IllegalArgumentException("A pending revert needs a dimension");
        }
    }

    public Slot slot() {
        return new Slot(dimension, packedPos);
    }

    public boolean isDue(long nowTick) {
        return nowTick >= dueTick;
    }

    public long remaining(long nowTick) {
        return Math.max(0L, dueTick - nowTick);
    }

    public record Slot(String dimension, long packedPos) {
    }
}
