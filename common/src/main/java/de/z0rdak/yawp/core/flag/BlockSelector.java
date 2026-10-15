package de.z0rdak.yawp.core.flag;

import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public final class BlockSelector {
    public static final char TAG_PREFIX = '#';

    public enum Type {
        BLOCK,
        TAG
    }

    private final Type type;
    private final ResourceLocation id;
    @Nullable
    private final TagKey<Block> tag;

    private BlockSelector(Type type, ResourceLocation id) {
        this.type = type;
        this.id = id;
        this.tag = type == Type.TAG ? TagKey.create(Registries.BLOCK, id) : null;
    }

    public static BlockSelector block(ResourceLocation blockId) {
        return new BlockSelector(Type.BLOCK, Objects.requireNonNull(blockId));
    }

    public static BlockSelector tag(ResourceLocation tagId) {
        return new BlockSelector(Type.TAG, Objects.requireNonNull(tagId));
    }

    public static DataResult<BlockSelector> parse(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return DataResult.error(() -> "Block exception must not be empty");
        }
        String trimmed = input.trim();
        boolean isTag = trimmed.charAt(0) == TAG_PREFIX;
        String location = isTag ? trimmed.substring(1) : trimmed;
        ResourceLocation rl = ResourceLocation.tryParse(location);
        if (rl == null || rl.getPath().isEmpty()) {
            return DataResult.error(() -> "'" + trimmed + "' is not a valid " + (isTag ? "block tag" : "block id")
                    + " (expected namespace:path or #namespace:path, lowercase)");
        }
        return DataResult.success(isTag ? tag(rl) : block(rl));
    }

    public Type type() {
        return type;
    }

    public boolean isTag() {
        return type == Type.TAG;
    }

    public ResourceLocation id() {
        return id;
    }

    public Optional<TagKey<Block>> tagKey() {
        return Optional.ofNullable(tag);
    }

    public boolean matches(Holder<Block> block) {
        if (tag != null) {
            return block.is(tag);
        }
        Optional<ResourceKey<Block>> key = block.unwrapKey();
        return key.isPresent() && key.get().location().equals(id);
    }

    public String serialize() {
        return type == Type.TAG ? TAG_PREFIX + id.toString() : id.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockSelector other)) return false;
        return type == other.type && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return 31 * type.hashCode() + id.hashCode();
    }

    @Override
    public String toString() {
        return serialize();
    }
}
