package de.z0rdak.yawp.core.flag;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.z0rdak.yawp.constants.Constants;

import java.util.Objects;
import java.util.Optional;

public abstract class FlagValue implements IFlag {

    public static Codec<IFlag> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.STRING.fieldOf("name")
                            .forGetter(IFlag::getName),
                    Codec.STRING.fieldOf("type")
                            .forGetter(f -> f.getType().flagType),
                    Codec.STRING.fieldOf("state")
                            .forGetter(f -> f.getState().name),
                    Codec.BOOL.fieldOf("override")
                            .forGetter(IFlag::doesOverride),
                    FlagMessage.CODEC.fieldOf("msg")
                            .forGetter(IFlag::getFlagMsg),
                    BlockExceptions.CODEC.optionalFieldOf("exceptions")
                            .forGetter(f -> f.getBlockExceptions().isEmpty() ? Optional.empty() : Optional.of(f.getBlockExceptions()))
                    ).apply(instance, (name, type, state, override, flagMessage, exceptions) -> {
                        var flagType = FlagType.of(type);
                        switch (flagType) {
                            case BOOLEAN_FLAG -> {
                                RegionFlag regionFlag = RegionFlag.fromId(name);
                                BooleanFlag flag = new BooleanFlag(regionFlag, FlagState.from(state), override, flagMessage);
                                exceptions.ifPresent(blockExceptions -> {
                                    if (regionFlag.supportsBlockExceptions()) {
                                        flag.setBlockExceptions(blockExceptions);
                                    } else {
                                        Constants.LOGGER.warn("Ignoring block exceptions stored for flag '{}', only {} and {} support block exceptions",
                                                name, RegionFlag.BREAK_BLOCKS.name, RegionFlag.PLACE_BLOCKS.name);
                                    }
                                });
                                return flag;
                            }
                            default -> throw new IllegalStateException("Unexpected value: " + flagType);
                        }
                    }
            ));

    protected String name;
    protected FlagType type;
    protected FlagState state;
    protected boolean doesOverride;
    protected FlagMessage msg;
    protected BlockExceptions blockExceptions = BlockExceptions.NONE;

    public FlagValue(String name, FlagType type, boolean override) {
        this(name, type, override, FlagState.DENIED);
    }

    public FlagValue(String name, FlagType type, boolean override, FlagState state) {
        this.name = name;
        this.type = type;
        this.state = state;
        this.doesOverride = override;
        this.msg = FlagMessage.DEFAULT_FLAG_MSG;
    }

    public FlagValue(String name, FlagType type) {
        this(name, type, false, FlagState.DENIED);
    }

    public FlagValue(String name, FlagType type, boolean override, FlagState state, String msg) {
        this(name, type, override, state);
        this.msg = new FlagMessage(msg);
    }

    public FlagValue(String name, FlagType type, boolean override, FlagState state, FlagMessage msg) {
        this(name, type, override, state);
        this.msg = msg;
    }

    @Override
    public FlagType getType() {
        return this.type;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public boolean isActive() {
        return this.state == FlagState.ALLOWED || this.state == FlagState.DENIED;
    }

    @Override
    public FlagState getState() {
        return this.state;
    }

    @Override
    public void setState(FlagState state) {
        this.state = state;
    }

    @Override
    public boolean doesOverride() {
        return this.doesOverride;
    }

    @Override
    public void setOverride(boolean override) {
        this.doesOverride = override;
    }

    @Override
    public FlagMessage getFlagMsg() {
        return this.msg;
    }

    @Override
    public void setFlagMsg(FlagMessage msg) {
        this.msg = msg;
    }

    @Override
    public BlockExceptions getBlockExceptions() {
        return this.blockExceptions;
    }

    @Override
    public void setBlockExceptions(BlockExceptions exceptions) {
        Objects.requireNonNull(exceptions, "exceptions");
        if (!exceptions.isEmpty() && !RegionFlag.fromId(this.name).supportsBlockExceptions()) {
            throw new UnsupportedOperationException("Flag '" + this.name + "' does not support block exceptions");
        }
        this.blockExceptions = exceptions;
    }

    @Override
    public int compareTo(IFlag o) {
        int nameComparisonRes = this.name.compareTo(o.getName());
        int stateResult = this.state.compareTo(o.getState());
        return nameComparisonRes + stateResult;
    }
}
