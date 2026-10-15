package de.z0rdak.yawp.util.text.messages.multiline;

import de.z0rdak.yawp.core.flag.BlockExceptions;
import de.z0rdak.yawp.core.flag.BlockSelector;
import de.z0rdak.yawp.core.flag.FlagState;
import de.z0rdak.yawp.core.flag.IFlag;
import de.z0rdak.yawp.core.flag.RegionFlag;
import de.z0rdak.yawp.core.region.IProtectedRegion;
import de.z0rdak.yawp.util.text.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

import static de.z0rdak.yawp.api.commands.Commands.*;
import static de.z0rdak.yawp.util.ChatComponentBuilder.*;
import static de.z0rdak.yawp.util.ChatLinkBuilder.*;
import static de.z0rdak.yawp.util.text.Messages.ADD_CMD_COLOR;
import static de.z0rdak.yawp.util.text.Messages.LINK_COLOR;
import static de.z0rdak.yawp.util.text.Messages.REMOVE_CMD_COLOR;
import static net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND;
import static net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND;

public class FlagDetailMessage implements MultiLineMessage<IFlag> {

    private final IProtectedRegion region;
    private final IFlag flag;
    private final List<Component> regionInfoLines;

    public FlagDetailMessage(IProtectedRegion region, IFlag flag) {
        this.region = region;
        this.flag = flag;
        this.regionInfoLines = new ArrayList<>();
    }

    @Override
    public IFlag getContent() {
        return this.flag;
    }

    /**
     * Builds the flag info component for the given flag and region. <br>
     * == Flag info for [flagname] of [region] == <br>
     * State: state [set state] <br>
     * Alert: [active] <br>
     * Override: [false] <br>
     * Message: [set] [x]: 'msg' <br>
     *
     */
    @Override
    public List<Component> getLines() {
        regionInfoLines.clear();
        MutableComponent header = buildFlagInfoHeader(region, flag);
        regionInfoLines.add(header);
        MutableComponent state = buildInfoComponent("cli.flag.state", "State", buildFlagStateComponent(region, flag));
        MutableComponent override = buildInfoComponent("cli.flag.override", "Override", buildFlagOverrideToggleLink(region, flag, false));
        regionInfoLines.add(state);
        regionInfoLines.add(override);
        if (RegionFlag.fromId(flag.getName()).supportsBlockExceptions()) {
            regionInfoLines.addAll(buildBlockExceptionLines(region, flag));
        }
        if (RegionFlag.hasPlayerCategory(flag)) {
            MutableComponent alert = buildInfoComponent("cli.flag.msg.mute", "Alert", buildFlagMuteToggleLink(region, flag, false));
            MutableComponent message = buildInfoComponent("cli.flag.msg.text", "Message", buildFlagMessageComponent(region, flag));
            regionInfoLines.add(alert);
            regionInfoLines.add(message);
        }
        return regionInfoLines;
    }

    /**
     * Message: [set] [x]: 'msg' <br>
     */
    public static MutableComponent buildFlagMessageComponent(IProtectedRegion region, IFlag flag) {
        MutableComponent editLink = buildFlagMessageEditLink(region, flag);
        MutableComponent clearLink = buildFlagMessageClearLink(region, flag);
        MutableComponent flagMsgTextWithHover = buildFlagMessageHoverText(region, flag);
        return Messages.substitutable("%s %s '%s'", editLink, clearLink, flagMsgTextWithHover);
    }

    public static MutableComponent buildFlagMessageClearLink(IProtectedRegion region, IFlag flag) {
        MutableComponent hover = Component.translatableWithFallback("cli.flag.msg.text.set.default", "Reset flag message for flag '%s' of '%s' to config default", flag.getName(), region.getName());
        MutableComponent text = Component.translatableWithFallback("cli.link.remove", "x");
        String cmd = buildFlagMsgClearCmd(region, flag.getName());
        return buildExecuteCmdComponent(text, hover, cmd, RUN_COMMAND, REMOVE_CMD_COLOR);
    }

    /**
     */
    public static List<Component> buildBlockExceptionLines(IProtectedRegion region, IFlag flag) {
        List<Component> lines = new ArrayList<>();
        BlockExceptions exceptions = flag.getBlockExceptions();
        MutableComponent summary;
        if (exceptions.isEmpty()) {
            MutableComponent none = Component.translatableWithFallback("cli.flag.exception.none", "none");
            summary = Messages.substitutable("%s %s %s", none,
                    buildAddExceptionLink(region, flag, FlagState.ALLOWED, true),
                    buildAddExceptionLink(region, flag, FlagState.DENIED, true));
        } else {
            summary = Messages.substitutable("%s %s %s", buildExceptionStateComponent(exceptions),
                    buildAddExceptionLink(region, flag, exceptions.state(), false),
                    buildClearExceptionsLink(region, flag));
        }
        lines.add(buildInfoComponent("cli.flag.exception", "Exceptions", summary));
        for (BlockSelector selector : exceptions.selectors()) {
            lines.add(Messages.substitutable(" - %s %s", buildSelectorComponent(selector), buildRemoveExceptionLink(region, flag, selector)));
        }
        return lines;
    }

    private static MutableComponent buildExceptionStateComponent(BlockExceptions exceptions) {
        boolean allowed = exceptions.state() == FlagState.ALLOWED;
        MutableComponent text = Component.literal(exceptions.state().name + " (" + exceptions.size() + ")");
        MutableComponent hover = allowed
                ? Component.translatableWithFallback("cli.flag.exception.state.allowed.hover", "Blocks matching one of the exceptions are allowed, all other blocks get the flag state")
                : Component.translatableWithFallback("cli.flag.exception.state.denied.hover", "Blocks matching one of the exceptions are denied, all other blocks get the flag state");
        return buildTextWithHoverMsg(text, hover, allowed ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static MutableComponent buildSelectorComponent(BlockSelector selector) {
        MutableComponent hover = selector.isTag()
                ? Component.translatableWithFallback("cli.flag.exception.entry.tag.hover", "All blocks in the block tag '%s'", selector.id().toString())
                : Component.translatableWithFallback("cli.flag.exception.entry.block.hover", "The block '%s'", selector.id().toString());
        return buildTextWithHoverMsg(Component.literal(selector.serialize()), hover, ChatFormatting.WHITE);
    }

    private static MutableComponent buildAddExceptionLink(IProtectedRegion region, IFlag flag, FlagState state, boolean longText) {
        boolean allow = state == FlagState.ALLOWED;
        MutableComponent text = longText
                ? (allow ? Component.translatableWithFallback("cli.flag.exception.add.allow.link.text", "+ allow")
                         : Component.translatableWithFallback("cli.flag.exception.add.deny.link.text", "+ deny"))
                : Component.translatableWithFallback("cli.link.add", "+");
        MutableComponent hover = allow
                ? Component.translatableWithFallback("cli.flag.exception.add.allow.link.hover", "Add a block or #tag which is allowed by flag '%s' of '%s'", flag.getName(), region.getName())
                : Component.translatableWithFallback("cli.flag.exception.add.deny.link.hover", "Add a block or #tag which is denied by flag '%s' of '%s'", flag.getName(), region.getName());
        String cmd = buildFlagExceptionAddCmd(region, flag.getName(), state, "");
        return buildExecuteCmdComponent(text, hover, cmd, SUGGEST_COMMAND, ADD_CMD_COLOR);
    }

    private static MutableComponent buildClearExceptionsLink(IProtectedRegion region, IFlag flag) {
        MutableComponent text = Component.translatableWithFallback("cli.flag.exception.clear.link.text", "clear");
        MutableComponent hover = Component.translatableWithFallback("cli.flag.exception.clear.link.hover", "Remove all block exceptions of flag '%s' of '%s'", flag.getName(), region.getName());
        return buildExecuteCmdComponent(text, hover, buildFlagExceptionClearCmd(region, flag.getName()), RUN_COMMAND, REMOVE_CMD_COLOR);
    }

    private static MutableComponent buildRemoveExceptionLink(IProtectedRegion region, IFlag flag, BlockSelector selector) {
        MutableComponent text = Component.translatableWithFallback("cli.link.remove", "x");
        MutableComponent hover = Component.translatableWithFallback("cli.flag.exception.remove.link.hover", "Remove exception '%s' from flag '%s' of '%s'", selector.serialize(), flag.getName(), region.getName());
        return buildExecuteCmdComponent(text, hover, buildFlagExceptionRemoveCmd(region, flag.getName(), selector.serialize()), RUN_COMMAND, REMOVE_CMD_COLOR);
    }

    public static MutableComponent buildFlagMessageEditLink(IProtectedRegion region, IFlag flag) {
        MutableComponent hover = Component.translatableWithFallback("cli.flag.msg.text.set.link.hover", "Change the message shown when the flag '%s' of '%s' is triggered", flag.getName(), region.getName());
        MutableComponent text = Component.translatableWithFallback("cli.flag.msg.text.set.link.text", "Edit");
        String msg = "\"" + flag.getFlagMsg().msg() + "\"";
        String cmd = buildFlagMsgSetCmd(region, flag.getName(), msg);
        return buildExecuteCmdComponent(text, hover, cmd, SUGGEST_COMMAND, LINK_COLOR);
    }

}
