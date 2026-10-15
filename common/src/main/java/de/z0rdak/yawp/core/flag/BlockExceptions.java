package de.z0rdak.yawp.core.flag;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import de.z0rdak.yawp.constants.Constants;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class BlockExceptions {
    public static final BlockExceptions NONE = new BlockExceptions(FlagState.ALLOWED, List.of());

    public static final Codec<BlockExceptions> CODEC = new LenientCodec();

    private final FlagState state;
    private final List<BlockException> entries;
    private final Map<ResourceLocation, BlockException> byBlockId;
    private final List<BlockException> byTag;

    private BlockExceptions(FlagState state, List<BlockException> entries) {
        this.state = state;
        this.entries = List.copyOf(entries);
        Map<ResourceLocation, BlockException> ids = new HashMap<>();
        List<BlockException> tagged = new ArrayList<>();
        for (BlockException entry : this.entries) {
            if (entry.selector().isTag()) {
                if (entry.selector().tagKey().isPresent()) {
                    tagged.add(entry);
                }
            } else {
                ids.put(entry.selector().id(), entry);
            }
        }
        this.byBlockId = Map.copyOf(ids);
        this.byTag = List.copyOf(tagged);
    }

    public static BlockExceptions of(FlagState state, Collection<BlockSelector> selectors) {
        return ofEntries(state, selectors.stream().map(BlockException::permanent).toList());
    }

    public static BlockExceptions ofEntries(FlagState state, Collection<BlockException> entries) {
        if (state != FlagState.ALLOWED && state != FlagState.DENIED) {
            throw new IllegalArgumentException("Block exceptions must either allow or deny blocks, but got: " + state);
        }
        if (entries.isEmpty()) {
            return NONE;
        }
        Map<BlockSelector, BlockException> unique = new LinkedHashMap<>();
        for (BlockException entry : entries) {
            unique.putIfAbsent(entry.selector(), entry);
        }
        return new BlockExceptions(state, new ArrayList<>(unique.values()));
    }

    public FlagState state() {
        return state;
    }

    public List<BlockException> entries() {
        return entries;
    }

    public List<BlockSelector> selectors() {
        return entries.stream().map(BlockException::selector).toList();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public boolean contains(BlockSelector selector) {
        return entryFor(selector) != null;
    }

    @Nullable
    public BlockException entryFor(BlockSelector selector) {
        for (BlockException entry : entries) {
            if (entry.selector().equals(selector)) {
                return entry;
            }
        }
        return null;
    }

    public BlockExceptions with(BlockSelector selector) {
        return with(BlockException.permanent(selector));
    }

    public BlockExceptions with(BlockException entry) {
        List<BlockException> updated = new ArrayList<>(entries);
        updated.removeIf(existing -> existing.selector().equals(entry.selector()));
        updated.add(entry);
        return ofEntries(state, updated);
    }

    public BlockExceptions without(BlockSelector selector) {
        List<BlockException> updated = new ArrayList<>(entries);
        updated.removeIf(entry -> entry.selector().equals(selector));
        return ofEntries(state, updated);
    }

    public BlockExceptions withDecay(BlockSelector selector, int decaySeconds) {
        BlockException existing = entryFor(selector);
        return existing == null ? this : with(existing.withDecay(decaySeconds));
    }

    public BlockExceptions withDecayForAll(int decaySeconds) {
        if (isEmpty()) {
            return this;
        }
        return ofEntries(state, entries.stream().map(entry -> entry.withDecay(decaySeconds)).toList());
    }

    public boolean matches(Holder<Block> block) {
        return matching(block) != null;
    }

    @Nullable
    public BlockException matching(Holder<Block> block) {
        if (!byBlockId.isEmpty()) {
            Optional<ResourceKey<Block>> key = block.unwrapKey();
            if (key.isPresent()) {
                BlockException exact = byBlockId.get(key.get().location());
                if (exact != null) {
                    return exact;
                }
            }
        }
        BlockException best = null;
        for (BlockException entry : byTag) {
            if (!block.is(entry.selector().tagKey().orElseThrow())) {
                continue;
            }
            if (best == null || isShorter(entry, best)) {
                best = entry;
            }
        }
        return best;
    }

    private static boolean isShorter(BlockException candidate, BlockException current) {
        if (candidate.decays() != current.decays()) {
            return candidate.decays();
        }
        return candidate.decaySeconds() < current.decaySeconds();
    }

    public int decaySecondsFor(Holder<Block> block) {
        BlockException entry = matching(block);
        return entry == null ? BlockException.NEVER : entry.decaySeconds();
    }

    public boolean anyDecays() {
        return entries.stream().anyMatch(BlockException::decays);
    }

    public FlagState apply(FlagState flagState, @Nullable Holder<Block> block) {
        if (block == null || isEmpty()) {
            return flagState;
        }
        if (flagState != FlagState.ALLOWED && flagState != FlagState.DENIED) {
            return flagState;
        }
        return matches(block) ? state : flagState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockExceptions other)) return false;
        if (isEmpty() && other.isEmpty()) return true;
        return state == other.state && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return isEmpty() ? 0 : 31 * state.hashCode() + entries.hashCode();
    }

    @Override
    public String toString() {
        return isEmpty() ? "none" : state.name + " " + entries;
    }

    private static final class LenientCodec implements Codec<BlockExceptions> {
        @Override
        public <T> DataResult<Pair<BlockExceptions, T>> decode(DynamicOps<T> ops, T input) {
            Dynamic<T> dynamic = new Dynamic<>(ops, input);
            String stateStr = dynamic.get("state").asString("");
            FlagState state;
            if (stateStr.equalsIgnoreCase(FlagState.ALLOWED.name)) {
                state = FlagState.ALLOWED;
            } else if (stateStr.equalsIgnoreCase(FlagState.DENIED.name)) {
                state = FlagState.DENIED;
            } else {
                Constants.LOGGER.warn("Invalid block exception state '{}', treating the exceptions as denied", stateStr);
                state = FlagState.DENIED;
            }
            List<BlockException> entries = new ArrayList<>();
            dynamic.get("blocks").asList(entry -> entry).forEach(entry -> decodeEntry(entry).ifPresent(entries::add));
            return DataResult.success(Pair.of(BlockExceptions.ofEntries(state, entries), ops.empty()));
        }

        private static <T> Optional<BlockException> decodeEntry(Dynamic<T> entry) {
            Optional<String> plain = entry.asString().result();
            String raw = plain.orElseGet(() -> entry.get("block").asString(""));
            if (raw.isBlank()) {
                Constants.LOGGER.warn("Ignoring block exception entry without a block");
                return Optional.empty();
            }
            DataResult<BlockSelector> parsed = BlockSelector.parse(raw);
            if (parsed.result().isEmpty()) {
                Constants.LOGGER.warn("Ignoring invalid block exception entry: {}",
                        parsed.error().map(DataResult.PartialResult::message).orElse(raw));
                return Optional.empty();
            }
            int decay = plain.isPresent() ? BlockException.NEVER : entry.get("decay").asInt(BlockException.NEVER);
            if (decay < 0 || decay > BlockException.MAX_SECONDS) {
                Constants.LOGGER.warn("Block exception '{}' has an out of range decay of {}s, treating it as permanent",
                        raw, decay);
                decay = BlockException.NEVER;
            }
            return Optional.of(new BlockException(parsed.result().get(), decay));
        }

        @Override
        public <T> DataResult<T> encode(BlockExceptions exceptions, DynamicOps<T> ops, T prefix) {
            return ops.mapBuilder()
                    .add("state", ops.createString(exceptions.state().name))
                    .add("blocks", ops.createList(exceptions.entries().stream().map(entry -> encodeEntry(ops, entry))))
                    .build(prefix);
        }

        private static <T> T encodeEntry(DynamicOps<T> ops, BlockException entry) {
            if (!entry.decays()) {
                return ops.createString(entry.selector().serialize());
            }
            return ops.createMap(Map.of(
                    ops.createString("block"), ops.createString(entry.selector().serialize()),
                    ops.createString("decay"), ops.createInt(entry.decaySeconds())));
        }

        @Override
        public String toString() {
            return "BlockExceptions";
        }
    }
}
