package de.z0rdak.yawp.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.DataResult;
import de.z0rdak.yawp.api.core.RegionManager;
import de.z0rdak.yawp.core.flag.*;
import de.z0rdak.yawp.core.region.IProtectedRegion;
import de.z0rdak.yawp.util.text.Messages;
import de.z0rdak.yawp.util.text.messages.multiline.FlagDetailMessage;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static de.z0rdak.yawp.api.MessageSender.sendCmdFeedback;
import static de.z0rdak.yawp.api.MessageSender.sendError;
import static de.z0rdak.yawp.api.commands.CommandConstants.*;
import static de.z0rdak.yawp.api.commands.Commands.*;
import static de.z0rdak.yawp.commands.arguments.ArgumentUtil.getIFlagArgument;
import static de.z0rdak.yawp.commands.arguments.ArgumentUtil.literal;
import static de.z0rdak.yawp.util.ChatComponentBuilder.buildFlagInfoHeader;
import static de.z0rdak.yawp.util.ChatLinkBuilder.buildFlagInfoLink;
import static de.z0rdak.yawp.util.ChatLinkBuilder.buildUndoLink;

final class BlockExceptionCommands {
    private BlockExceptionCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build(Function<CommandContext<CommandSourceStack>, IProtectedRegion> regionSupplier) {
        return literal(EXCEPTION)
                .executes(ctx -> promptExceptions(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx)))
                .then(literal(ALLOW)
                        .then(selectorArgument()
                                .suggests((ctx, builder) -> suggestBlocksAndTags(builder))
                                .executes(ctx -> addException(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx), FlagState.ALLOWED, getSelectorArgument(ctx)))))
                .then(literal(DENY)
                        .then(selectorArgument()
                                .suggests((ctx, builder) -> suggestBlocksAndTags(builder))
                                .executes(ctx -> addException(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx), FlagState.DENIED, getSelectorArgument(ctx)))))
                .then(literal(REMOVE)
                        .then(selectorArgument()
                                .suggests((ctx, builder) -> suggestConfiguredExceptions(ctx, regionSupplier, builder))
                                .executes(ctx -> removeException(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx), getSelectorArgument(ctx)))))
                .then(literal(DECAY)
                        .then(decayArgument()
                                .executes(ctx -> setDecay(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx),
                                        StringArgumentType.getString(ctx, DECAY.toString())))))
                .then(literal(CLEAR)
                        .executes(ctx -> clearExceptions(ctx, regionSupplier.apply(ctx), getIFlagArgument(ctx))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> selectorArgument() {
        return Commands.argument(BLOCK.toString(), StringArgumentType.greedyString());
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> decayArgument() {
        return Commands.argument(DECAY.toString(), StringArgumentType.greedyString());
    }

    private static String getSelectorArgument(CommandContext<CommandSourceStack> ctx) {
        return StringArgumentType.getString(ctx, BLOCK.toString());
    }

    private static CompletableFuture<Suggestions> suggestBlocksAndTags(SuggestionsBuilder builder) {
        if (builder.getRemaining().startsWith(String.valueOf(BlockSelector.TAG_PREFIX))) {
            return SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.getTagNames().map(TagKey::location), builder, "#");
        }
        SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), builder);
        return SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.getTagNames().map(TagKey::location), builder, "#");
    }

    private static CompletableFuture<Suggestions> suggestConfiguredExceptions(CommandContext<CommandSourceStack> ctx, Function<CommandContext<CommandSourceStack>, IProtectedRegion> regionSupplier, SuggestionsBuilder builder) {
        try {
            IProtectedRegion region = regionSupplier.apply(ctx);
            IFlag flag = region.getFlag(StringArgumentType.getString(ctx, FLAG.toString()));
            if (flag == null) {
                return Suggestions.empty();
            }
            return SharedSuggestionProvider.suggest(flag.getBlockExceptions().selectors().stream().map(BlockSelector::serialize), builder);
        } catch (RuntimeException e) {
            return Suggestions.empty();
        }
    }

    private static boolean supportsExceptions(CommandContext<CommandSourceStack> ctx, IFlag flag) {
        if (RegionFlag.fromId(flag.getName()).supportsBlockExceptions()) {
            return true;
        }
        sendError(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.unsupported", "Flag '%s' does not support block exceptions. Supported flags: %s",
                flag.getName(), String.join(", ", RegionFlag.getBlockExceptionFlagNames())));
        return false;
    }

    private static Optional<BlockSelector> parseSelector(CommandContext<CommandSourceStack> ctx, String input) {
        DataResult<BlockSelector> parsed = BlockSelector.parse(input);
        if (parsed.result().isEmpty()) {
            String reason = parsed.error().map(DataResult.PartialResult::message).orElse(input);
            sendError(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.invalid", "Invalid block exception: %s", reason));
        }
        return parsed.result();
    }

    private static int promptExceptions(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag flag) {
        if (flag == null || !supportsExceptions(ctx, flag)) return 1;
        sendCmdFeedback(ctx.getSource(), buildFlagInfoHeader(region, flag));
        FlagDetailMessage.buildBlockExceptionLines(region, flag).forEach(line -> ctx.getSource().sendSystemMessage(line));
        return 0;
    }

    private static int addException(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag flag, FlagState state, String input) {
        if (flag == null || !supportsExceptions(ctx, flag)) return 1;
        Optional<BlockSelector> maybeSelector = parseSelector(ctx, input);
        if (maybeSelector.isEmpty()) return 1;
        BlockSelector selector = maybeSelector.get();
        if (!selector.isTag() && !BuiltInRegistries.BLOCK.containsKey(selector.id())) {
            sendError(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.unknown-block",
                    "Unknown block '%s'. Use an existing block id (e.g. minecraft:cobweb) or a block tag (e.g. #minecraft:logs)", selector.serialize()));
            return 1;
        }
        BlockExceptions current = flag.getBlockExceptions();
        MutableComponent flagLink = buildFlagInfoLink(region, flag);
        if (!current.isEmpty() && current.state() != state) {
            sendError(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.state-conflict",
                    "The exceptions of %s are '%s'. All exceptions of a flag have the same result, remove or clear them first: %s",
                    flagLink, current.state().name, buildFlagExceptionClearCmd(region, flag.getName())));
            return 1;
        }
        if (current.contains(selector)) {
            sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.present",
                    "'%s' is already an exception of %s", selector.serialize(), flagLink));
            return 1;
        }
        flag.setBlockExceptions(current.isEmpty() ? BlockExceptions.of(state, List.of(selector)) : current.with(selector));
        RegionManager.get().save(region);

        MutableComponent undoLink = buildUndoLink(buildFlagExceptionRemoveCmd(region, flag.getName(), selector.serialize()));
        MutableComponent msg = Component.translatableWithFallback("cli.flag.exception.added", "Added '%s' exception '%s' to %s",
                state.name, selector.serialize(), flagLink);
        sendCmdFeedback(ctx.getSource(), Messages.substitutable("%s %s", msg, undoLink));
        if (selector.isTag() && isUnknownOrEmptyTag(selector)) {
            sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.unknown-tag",
                    "Warning: the block tag '%s' is currently unknown or empty. It will not match any block until a datapack defines it.",
                    selector.serialize()).withStyle(ChatFormatting.GOLD));
        }
        if (flag.getState() == state) {
            sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.no-effect",
                    "Note: %s itself is '%s' as well, so its exceptions currently have no effect.",
                    flagLink, flag.getState().name).withStyle(ChatFormatting.GOLD));
        }
        return 0;
    }

    private static boolean isUnknownOrEmptyTag(BlockSelector selector) {
        return selector.tagKey()
                .flatMap(BuiltInRegistries.BLOCK::getTag)
                .map(HolderSet::size)
                .orElse(0) == 0;
    }

    private static int removeException(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag flag, String input) {
        if (flag == null || !supportsExceptions(ctx, flag)) return 1;
        Optional<BlockSelector> maybeSelector = parseSelector(ctx, input);
        if (maybeSelector.isEmpty()) return 1;
        BlockSelector selector = maybeSelector.get();
        BlockExceptions current = flag.getBlockExceptions();
        MutableComponent flagLink = buildFlagInfoLink(region, flag);
        if (!current.contains(selector)) {
            sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.not-present",
                    "'%s' is not an exception of %s", selector.serialize(), flagLink));
            return 1;
        }
        flag.setBlockExceptions(current.without(selector));
        RegionManager.get().save(region);
        MutableComponent undoLink = buildUndoLink(buildFlagExceptionAddCmd(region, flag.getName(), current.state(), selector.serialize()));
        MutableComponent msg = Component.translatableWithFallback("cli.flag.exception.removed", "Removed exception '%s' from %s",
                selector.serialize(), flagLink);
        sendCmdFeedback(ctx.getSource(), Messages.substitutable("%s %s", msg, undoLink));
        return 0;
    }

    private static int setDecay(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag flag, String input) {
        if (flag == null || !supportsExceptions(ctx, flag)) return 1;
        String[] parts = input == null ? new String[0] : input.trim().split("\s+");
        if (parts.length != 2) {
            sendError(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.decay.usage",
                    "Usage: decay <all|block|#tag> <seconds>, e.g. 'decay minecraft:torch 3600' or 'decay all 0'"));
            return 1;
        }
        int seconds;
        try {
            seconds = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            sendError(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.decay.not-a-number",
                    "'%s' is not a number of seconds", parts[1]));
            return 1;
        }
        if (seconds < 0 || seconds > BlockException.MAX_SECONDS) {
            sendError(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.decay.out-of-range",
                    "Seconds must be 0 (permanent) to %s (30 days), but got %s", BlockException.MAX_SECONDS, seconds));
            return 1;
        }

        BlockExceptions current = flag.getBlockExceptions();
        MutableComponent flagLink = buildFlagInfoLink(region, flag);
        if (current.isEmpty()) {
            sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.empty",
                    "%s has no block exceptions", flagLink));
            return 1;
        }

        if (parts[0].equalsIgnoreCase(ALL.toString())) {
            flag.setBlockExceptions(current.withDecayForAll(seconds));
            RegionManager.get().save(region);
            sendCmdFeedback(ctx.getSource(), seconds == BlockException.NEVER
                    ? Component.translatableWithFallback("cli.flag.exception.decay.cleared-all",
                            "All %s exceptions of %s are permanent again", current.size(), flagLink)
                    : Component.translatableWithFallback("cli.flag.exception.decay.set-all",
                            "All %s exceptions of %s now revert after %ss", current.size(), flagLink, seconds));
            return 0;
        }

        Optional<BlockSelector> maybeSelector = parseSelector(ctx, parts[0]);
        if (maybeSelector.isEmpty()) return 1;
        BlockSelector selector = maybeSelector.get();
        if (!current.contains(selector)) {
            sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.not-present",
                    "'%s' is not an exception of %s", selector.serialize(), flagLink));
            return 1;
        }
        flag.setBlockExceptions(current.withDecay(selector, seconds));
        RegionManager.get().save(region);
        sendCmdFeedback(ctx.getSource(), seconds == BlockException.NEVER
                ? Component.translatableWithFallback("cli.flag.exception.decay.cleared",
                        "'%s' of %s is permanent again", selector.serialize(), flagLink)
                : Component.translatableWithFallback("cli.flag.exception.decay.set",
                        "'%s' of %s now reverts after %ss", selector.serialize(), flagLink, seconds));
        return 0;
    }

    private static int clearExceptions(CommandContext<CommandSourceStack> ctx, IProtectedRegion region, IFlag flag) {
        if (flag == null || !supportsExceptions(ctx, flag)) return 1;
        BlockExceptions current = flag.getBlockExceptions();
        MutableComponent flagLink = buildFlagInfoLink(region, flag);
        if (current.isEmpty()) {
            sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.empty",
                    "%s has no block exceptions", flagLink));
            return 1;
        }
        flag.setBlockExceptions(BlockExceptions.NONE);
        RegionManager.get().save(region);
        sendCmdFeedback(ctx.getSource(), Component.translatableWithFallback("cli.flag.exception.cleared",
                "Removed %s block exception(s) from %s", current.size(), flagLink));
        return 0;
    }
}
