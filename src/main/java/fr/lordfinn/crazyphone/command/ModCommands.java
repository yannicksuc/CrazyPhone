package fr.lordfinn.crazyphone.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.network.FeatureFlagSyncPacket;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneAddMayorProgramFromMainHandProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneAddNewMayorCandidateProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneDeletePhoneByNumberProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneGivePhoneToPlayerFromNumberProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneListAndPrintPhonesProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneRemoveMayorCandidateProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneToggleElectionProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneToggleMayorVotingProcedure;
import fr.lordfinn.crazyphone.procedures.RemoveVoteByNumberProcedure;
import fr.lordfinn.crazyphone.procedures.ShowMayorVotesProcedure;
import fr.lordfinn.crazyphone.procedures.VoteForMayorProcedure;

/**
 * Every CrazyPhone command, under one {@code /crazyphone} root instead of the dozen inconsistently-named,
 * inconsistently-permissioned top-level commands this replaces ({@code /phoneGive}, {@code /phoneshowvotes},
 * {@code /phoneremovevotebynumber}, ...). Each subcommand keeps calling the same procedure the old command
 * for it called - this only changes how they're reached, not what they do - except the mayor
 * candidate/vote/program commands, which took a {@code phoneNumber} as a raw {@code double} (rounded back
 * to a string on the other end) purely because that's what MCreator generated; phone numbers here are
 * always plain 3-digit codes (see ResetCrazyPhoneNumberProcedure), so those now take a real
 * {@link IntegerArgumentType} instead - simpler, and it's what makes autocompletion possible.
 *
 * <pre>
 * /crazyphone give &lt;number&gt;                    (level 4)
 * /crazyphone delete &lt;number&gt;                  (level 4)
 * /crazyphone list [search]                     (level 4)
 * /crazyphone feature list                      (level 2)
 * /crazyphone feature &lt;name&gt; &lt;true|false&gt;      (level 4)
 * /crazyphone mayor vote &lt;number&gt;               (level 0 - any player, same procedure the in-phone vote button calls)
 * /crazyphone mayor election &lt;true|false&gt;       (level 4)
 * /crazyphone mayor voting &lt;true|false&gt;         (level 4)
 * /crazyphone mayor votes show                  (level 2)
 * /crazyphone mayor votes clear &lt;number&gt;        (level 4)
 * /crazyphone mayor candidate add &lt;number&gt;      (level 4)
 * /crazyphone mayor candidate remove &lt;number&gt;   (level 4)
 * /crazyphone mayor candidate program &lt;number&gt;  (level 4 - candidate's program poster from the item in hand)
 * </pre>
 */
@EventBusSubscriber
public class ModCommands {
    private static final SuggestionProvider<CommandSourceStack> REGISTERED_NUMBERS = (context, builder) ->
            SharedSuggestionProvider.suggest(registeredNumbers(context), builder);
    private static final SuggestionProvider<CommandSourceStack> CANDIDATE_NUMBERS = (context, builder) ->
            SharedSuggestionProvider.suggest(candidateNumbers(context), builder);
    private static final SuggestionProvider<CommandSourceStack> FEATURE_NAMES = (context, builder) ->
            SharedSuggestionProvider.suggest(java.util.Arrays.stream(FeatureFlag.values()).map(f -> f.id).toList(), builder);

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("crazyphone")
                .then(Commands.literal("give").requires(s -> s.hasPermission(4))
                        .then(Commands.argument("number", StringArgumentType.word()).suggests(REGISTERED_NUMBERS)
                                .executes(ModCommands::give)))
                .then(Commands.literal("delete").requires(s -> s.hasPermission(4))
                        .then(Commands.argument("number", StringArgumentType.word()).suggests(REGISTERED_NUMBERS)
                                .executes(ModCommands::delete)))
                .then(Commands.literal("list").requires(s -> s.hasPermission(4))
                        .executes(ModCommands::list)
                        .then(Commands.argument("search", StringArgumentType.word())
                                .executes(ModCommands::list)))
                .then(Commands.literal("feature").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(ModCommands::featureList))
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(FEATURE_NAMES)
                                .requires(s -> s.hasPermission(4))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ModCommands::featureSet))))
                .then(Commands.literal("mayor")
                        .then(Commands.literal("vote")
                                .then(Commands.argument("phoneNumber", IntegerArgumentType.integer(100, 999)).suggests(REGISTERED_NUMBERS)
                                        .executes(ModCommands::mayorVote)))
                        .then(Commands.literal("election").requires(s -> s.hasPermission(4))
                                .then(Commands.argument("isOn", BoolArgumentType.bool())
                                        .executes(ModCommands::mayorElection)))
                        .then(Commands.literal("voting").requires(s -> s.hasPermission(4))
                                .then(Commands.argument("isOn", BoolArgumentType.bool())
                                        .executes(ModCommands::mayorVoting)))
                        .then(Commands.literal("votes")
                                .then(Commands.literal("show").requires(s -> s.hasPermission(2))
                                        .executes(ModCommands::mayorVotesShow))
                                .then(Commands.literal("clear").requires(s -> s.hasPermission(4))
                                        .then(Commands.argument("phoneNumber", IntegerArgumentType.integer(100, 999)).suggests(REGISTERED_NUMBERS)
                                                .executes(ModCommands::mayorVotesClear))))
                        .then(Commands.literal("candidate").requires(s -> s.hasPermission(4))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("phoneNumber", IntegerArgumentType.integer(100, 999)).suggests(REGISTERED_NUMBERS)
                                                .executes(ModCommands::mayorCandidateAdd)))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("phoneNumber", IntegerArgumentType.integer(100, 999)).suggests(CANDIDATE_NUMBERS)
                                                .executes(ModCommands::mayorCandidateRemove)))
                                .then(Commands.literal("program")
                                        .then(Commands.argument("phoneNumber", IntegerArgumentType.integer(100, 999)).suggests(CANDIDATE_NUMBERS)
                                                .executes(ModCommands::mayorCandidateProgram))))));
    }

    // --- phones ---

    private static int give(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        Entity entity = resolveEntity(arguments, world);
        CrazyPhoneGivePhoneToPlayerFromNumberProcedure.execute(world, arguments, entity);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        String number = StringArgumentType.getString(arguments, "number");
        boolean deleted = CrazyPhoneDeletePhoneByNumberProcedure.execute(world, number);
        if (deleted) {
            arguments.getSource().sendSuccess(() -> Component.literal("Phone " + number + " deleted.").withStyle(ChatFormatting.GREEN), true);
        } else {
            arguments.getSource().sendFailure(Component.literal("No phone registered with number " + number + ".").withStyle(ChatFormatting.RED));
        }
        return deleted ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        Entity entity = resolveEntity(arguments, world);
        CrazyPhoneListAndPrintPhonesProcedure.execute(world, arguments, entity);
        return 1;
    }

    // --- feature toggles ---

    private static int featureList(CommandContext<CommandSourceStack> arguments) {
        for (FeatureFlag flag : FeatureFlag.values()) {
            boolean enabled = flag.isGloballyEnabled();
            arguments.getSource().sendSuccess(() -> Component.literal(flag.id + ": ")
                    .append(Component.literal(enabled ? "enabled" : "disabled")
                            .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
        }
        return 1;
    }

    private static int featureSet(CommandContext<CommandSourceStack> arguments) {
        String id = StringArgumentType.getString(arguments, "name");
        boolean enabled = BoolArgumentType.getBool(arguments, "enabled");
        FeatureFlag flag = FeatureFlag.byId(id);
        if (flag == null) {
            arguments.getSource().sendFailure(Component.literal("Unknown feature: " + id
                    + " (try /crazyphone feature list)").withStyle(ChatFormatting.RED));
            return 0;
        }
        flag.setGloballyEnabled(enabled);
        FeatureFlagSyncPacket.syncToAll(arguments.getSource().getServer());
        arguments.getSource().sendSuccess(() -> Component.literal(flag.id + " is now ")
                .append(Component.literal(enabled ? "enabled" : "disabled").withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)), true);
        return 1;
    }

    // --- mayor ---

    private static int mayorVote(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        Entity entity = resolveEntity(arguments, world);
        VoteForMayorProcedure.execute(world, arguments, entity);
        return 1;
    }

    private static int mayorElection(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        CrazyPhoneToggleElectionProcedure.execute(world, arguments);
        return 1;
    }

    private static int mayorVoting(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        CrazyPhoneToggleMayorVotingProcedure.execute(world, arguments);
        return 1;
    }

    private static int mayorVotesShow(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        Entity entity = resolveEntity(arguments, world);
        ShowMayorVotesProcedure.execute(world, entity);
        return 1;
    }

    private static int mayorVotesClear(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        Entity entity = resolveEntity(arguments, world);
        RemoveVoteByNumberProcedure.execute(world, arguments, entity);
        return 1;
    }

    private static int mayorCandidateAdd(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        Entity entity = resolveEntity(arguments, world);
        CrazyPhoneAddNewMayorCandidateProcedure.execute(world, arguments, entity);
        return 1;
    }

    private static int mayorCandidateRemove(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        Entity entity = resolveEntity(arguments, world);
        CrazyPhoneRemoveMayorCandidateProcedure.execute(world, arguments, entity);
        return 1;
    }

    private static int mayorCandidateProgram(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getUnsidedLevel();
        Entity entity = resolveEntity(arguments, world);
        CrazyPhoneAddMayorProgramFromMainHandProcedure.execute(world, arguments, entity);
        return 1;
    }

    // --- shared helpers ---

    /** The command sender as an Entity, falling back to a fake player when run from console/command block
     * (a world is still needed for procedures that touch player-scoped state) - every subcommand needed
     * this same fallback, previously copy-pasted into all dozen command classes. */
    private static Entity resolveEntity(CommandContext<CommandSourceStack> arguments, Level world) {
        Entity entity = arguments.getSource().getEntity();
        if (entity == null && world instanceof ServerLevel serverLevel)
            entity = FakePlayerFactory.getMinecraft(serverLevel);
        return entity;
    }

    private static Iterable<String> registeredNumbers(CommandContext<CommandSourceStack> context) {
        LevelAccessor world = context.getSource().getUnsidedLevel();
        return world == null ? java.util.List.of() : PhoneRegistrySavedData.get(world).phones.getAllKeys();
    }

    private static Iterable<String> candidateNumbers(CommandContext<CommandSourceStack> context) {
        LevelAccessor world = context.getSource().getUnsidedLevel();
        return world == null ? java.util.List.of() : PhoneRegistrySavedData.get(world).mayorsCandidates.getAllKeys();
    }
}
