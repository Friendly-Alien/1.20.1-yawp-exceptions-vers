package de.z0rdak.yawp.core.decay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

public final class RevertQueue {
    public static final int DEFAULT_CAPACITY = 20_000;

    private final Map<PendingRevert.Slot, PendingRevert> promises = new LinkedHashMap<>();
    private int capacity = DEFAULT_CAPACITY;

    public int capacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = Math.max(0, capacity);
    }

    public int size() {
        return promises.size();
    }

    public boolean isEmpty() {
        return promises.isEmpty();
    }

    public boolean isFull() {
        return promises.size() >= capacity;
    }

    public boolean promise(PendingRevert revert) {
        PendingRevert.Slot slot = revert.slot();
        if (!promises.containsKey(slot) && isFull()) {
            return false;
        }
        promises.put(slot, revert);
        return true;
    }

    @Nullable
    public PendingRevert promiseFor(String dimension, long packedPos) {
        return promises.get(new PendingRevert.Slot(dimension, packedPos));
    }

    @Nullable
    public PendingRevert forget(String dimension, long packedPos) {
        return promises.remove(new PendingRevert.Slot(dimension, packedPos));
    }

    public int forgetDimension(String dimension) {
        int before = promises.size();
        promises.keySet().removeIf(slot -> slot.dimension().equals(dimension));
        return before - promises.size();
    }

    public void clear() {
        promises.clear();
    }

    public List<PendingRevert> due(long nowTick) {
        List<PendingRevert> ready = new ArrayList<>();
        for (PendingRevert revert : promises.values()) {
            if (revert.isDue(nowTick)) {
                ready.add(revert);
            }
        }
        ready.sort((a, b) -> Long.compare(a.dueTick(), b.dueTick()));
        return ready;
    }

    public Collection<PendingRevert> all() {
        return Collections.unmodifiableCollection(promises.values());
    }

    public void loadAll(Collection<PendingRevert> reverts) {
        promises.clear();
        for (PendingRevert revert : reverts) {
            promises.put(revert.slot(), revert);
        }
    }
}
