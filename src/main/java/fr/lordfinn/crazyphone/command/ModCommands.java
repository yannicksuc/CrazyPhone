package fr.lordfinn.crazyphone.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

//? if neoforge {
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
*///?}

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.network.CrazyPhoneClearPictureCachePacket;
import fr.lordfinn.crazyphone.network.FeatureFlagSyncPacket;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
//? if neoforge {
import fr.lordfinn.crazyphone.procedures.CrazyPhoneAddMayorProgramFromMainHandProcedure;
//?}
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
 * /crazyphone cache clear [player]              (level 4 - defaults to the command's own sender)
 * </pre>
 */
//? if neoforge {
@EventBusSubscriber
//?}
public class ModCommands {
    private static final SuggestionProvider<CommandSourceStack> REGISTERED_NUMBERS = (context, builder) ->
            SharedSuggestionProvider.suggest(registeredNumbers(context), builder);
    private static final SuggestionProvider<CommandSourceStack> CANDIDATE_NUMBERS = (context, builder) ->
            SharedSuggestionProvider.suggest(candidateNumbers(context), builder);
    private static final SuggestionProvider<CommandSourceStack> FEATURE_NAMES = (context, builder) ->
            SharedSuggestionProvider.suggest(java.util.Arrays.stream(FeatureFlag.values()).map(f -> f.id).toList(), builder);

    // 26.x replaced CommandSourceStack#hasPermission(int) - a simple 0-4 OP level - with a PermissionSet/
    // PermissionCheck-based system (confirmed via decompiling the real 26.1.2 vanilla jar); the new
    // equivalent for a bare `.requires(...)` gate is Commands.hasPermission(Commands.LEVEL_X), where
    // LEVEL_ALL/LEVEL_MODERATORS/LEVEL_GAMEMASTERS/LEVEL_ADMINS/LEVEL_OWNERS map to the old levels 0-4 in
    // order (confirmed against vanilla's own OpCommand, which uses this exact call shape). Every command
    // here only ever used level 2 or 4, so this only needs to cover those two.
    private static java.util.function.Predicate<CommandSourceStack> permLevel(int level) {
        //? if >=26 {
        /*return Commands.hasPermission(level >= 4 ? Commands.LEVEL_OWNERS : Commands.LEVEL_GAMEMASTERS);
        *///?} else {
        return s -> s.hasPermission(level);
        //?}
    }

    //? if neoforge {
    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(buildCommandTree());
    }
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(buildCommandTree()));
    }
    *///?}

    /** Shared by both loaders' registration entrypoint - see those for how each reaches the dispatcher. The
     * "candidate program" leaf (candidate's program poster, sourced from the phone's own native photo item -
     * no longer a Camera-mod dependency) stays NeoForge-only simply because it hasn't been ported to Fabric
     * yet. */
    private static LiteralArgumentBuilder<CommandSourceStack> buildCommandTree() {
        LiteralArgumentBuilder<CommandSourceStack> candidate = Commands.literal("candidate").requires(permLevel(4))
                .then(Commands.literal("add")
                        .then(Commands.argument("phoneNumber", IntegerArgumentType.integer(100, 999)).suggests(REGISTERED_NUMBERS)
                                .executes(ModCommands::mayorCandidateAdd)))
                .then(Commands.literal("remove")
                        .then(Commands.argument("phoneNumber", IntegerArgumentType.integer(100, 999)).suggests(CANDIDATE_NUMBERS)
                                .executes(ModCommands::mayorCandidateRemove)));
        //? if neoforge {
        candidate.then(Commands.literal("program")
                .then(Commands.argument("phoneNumber", IntegerArgumentType.integer(100, 999)).suggests(CANDIDATE_NUMBERS)
                        .executes(ModCommands::mayorCandidateProgram)));
        //?}
        return Commands.literal("crazyphone")
                .then(Commands.literal("give").requires(permLevel(4))
                        .then(Commands.argument("number", StringArgumentType.word()).suggests(REGISTERED_NUMBERS)
                                .executes(ModCommands::give)))
                .then(Commands.literal("delete").requires(permLevel(4))
                        .then(Commands.argument("number", StringArgumentType.word()).suggests(REGISTERED_NUMBERS)
                                .executes(ModCommands::delete)))
                .then(Commands.literal("list").requires(permLevel(4))
                        .executes(ModCommands::list)
                        .then(Commands.argument("search", StringArgumentType.word())
                                .executes(ModCommands::list)))
                .then(Commands.literal("feature").requires(permLevel(2))
                        .then(Commands.literal("list").executes(ModCommands::featureList))
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(FEATURE_NAMES)
                                .requires(permLevel(4))
                                .then(Commands.argument("enabled", BoolArgumentType.bool())
                                        .executes(ModCommands::featureSet))))
                .then(Commands.literal("mayor")
                        .then(Commands.literal("vote")
                                .then(Commands.argument("phoneNumber", IntegerArgumentType.integer(100, 999)).suggests(REGISTERED_NUMBERS)
                                        .executes(ModCommands::mayorVote)))
                        .then(Commands.literal("election").requires(permLevel(4))
                                .then(Commands.argument("isOn", BoolArgumentType.bool())
                                        .executes(ModCommands::mayorElection)))
                        .then(Commands.literal("voting").requires(permLevel(4))
                                .then(Commands.argument("isOn", BoolArgumentType.bool())
                                        .executes(ModCommands::mayorVoting)))
                        .then(Commands.literal("votes")
                                .then(Commands.literal("show").requires(permLevel(2))
                                        .executes(ModCommands::mayorVotesShow))
                                .then(Commands.literal("clear").requires(permLevel(4))
                                        .then(Commands.argument("phoneNumber", IntegerArgumentType.integer(100, 999)).suggests(REGISTERED_NUMBERS)
                                                .executes(ModCommands::mayorVotesClear))))
                        .then(candidate))
                .then(Commands.literal("cache").requires(permLevel(4))
                        .then(Commands.literal("clear")
                                .executes(ModCommands::cacheClearSelf)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ModCommands::cacheClearPlayer))));
    }

    // --- phones ---

    private static int give(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        Entity entity = resolveEntity(arguments, world);
        CrazyPhoneGivePhoneToPlayerFromNumberProcedure.execute(world, arguments, entity);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        String number = StringArgumentType.getString(arguments, "number");
        boolean deleted = CrazyPhoneDeletePhoneByNumberProcedure.execute(world, number);
        if (deleted) {
            arguments.getSource().sendSuccess(() -> Component.translatable("command.crazyphone.phone_deleted", number).withStyle(ChatFormatting.GREEN), true);
        } else {
            arguments.getSource().sendFailure(Component.translatable("command.crazyphone.phone_not_registered", number).withStyle(ChatFormatting.RED));
        }
        return deleted ? 1 : 0;
    }

    private static int list(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        Entity entity = resolveEntity(arguments, world);
        CrazyPhoneListAndPrintPhonesProcedure.execute(world, arguments, entity);
        return 1;
    }

    // --- feature toggles ---

    private static int featureList(CommandContext<CommandSourceStack> arguments) {
        for (FeatureFlag flag : FeatureFlag.values()) {
            boolean enabled = flag.isGloballyEnabled();
            arguments.getSource().sendSuccess(() -> Component.translatable("command.crazyphone.feature_status_line", flag.id)
                    .append(stateComponent(enabled)), false);
        }
        return 1;
    }

    private static int featureSet(CommandContext<CommandSourceStack> arguments) {
        String id = StringArgumentType.getString(arguments, "name");
        boolean enabled = BoolArgumentType.getBool(arguments, "enabled");
        FeatureFlag flag = FeatureFlag.byId(id);
        if (flag == null) {
            arguments.getSource().sendFailure(Component.translatable("command.crazyphone.unknown_feature", id).withStyle(ChatFormatting.RED));
            return 0;
        }
        flag.setGloballyEnabled(enabled);
        FeatureFlagSyncPacket.syncToAll(arguments.getSource().getServer());
        arguments.getSource().sendSuccess(() -> Component.translatable("command.crazyphone.feature_now", flag.id)
                .append(stateComponent(enabled)), true);
        return 1;
    }

    /** The "enabled"/"disabled" word, translated and colored - shared by featureList and featureSet so the
     * two commands' feedback stays visually consistent. */
    private static Component stateComponent(boolean enabled) {
        return Component.translatable(enabled ? "command.crazyphone.state_enabled" : "command.crazyphone.state_disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    // --- mayor ---

    private static int mayorVote(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        Entity entity = resolveEntity(arguments, world);
        VoteForMayorProcedure.execute(world, arguments, entity);
        return 1;
    }

    private static int mayorElection(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        CrazyPhoneToggleElectionProcedure.execute(world, arguments);
        return 1;
    }

    private static int mayorVoting(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        CrazyPhoneToggleMayorVotingProcedure.execute(world, arguments);
        return 1;
    }

    private static int mayorVotesShow(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        Entity entity = resolveEntity(arguments, world);
        ShowMayorVotesProcedure.execute(world, entity);
        return 1;
    }

    private static int mayorVotesClear(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        Entity entity = resolveEntity(arguments, world);
        RemoveVoteByNumberProcedure.execute(world, arguments, entity);
        return 1;
    }

    private static int mayorCandidateAdd(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        Entity entity = resolveEntity(arguments, world);
        CrazyPhoneAddNewMayorCandidateProcedure.execute(world, arguments, entity);
        return 1;
    }

    private static int mayorCandidateRemove(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        Entity entity = resolveEntity(arguments, world);
        CrazyPhoneRemoveMayorCandidateProcedure.execute(world, arguments, entity);
        return 1;
    }

    //? if neoforge {
    private static int mayorCandidateProgram(CommandContext<CommandSourceStack> arguments) {
        Level world = arguments.getSource().getLevel();
        Entity entity = resolveEntity(arguments, world);
        CrazyPhoneAddMayorProgramFromMainHandProcedure.execute(world, arguments, entity);
        return 1;
    }
    //?}
    // No Fabric mayorCandidateProgram - the "candidate program" leaf isn't registered on Fabric at all (see
    // buildCommandTree's own note), so this method would be unreachable there anyway.

    // --- cache ---

    private static int cacheClearSelf(CommandContext<CommandSourceStack> arguments) {
        // No player argument given - only meaningful for a real player running it on themselves; a
        // console/command-block sender has no client-side cache to clear and must name someone explicitly.
        if (!(arguments.getSource().getEntity() instanceof ServerPlayer player)) {
            arguments.getSource().sendFailure(Component.translatable("command.crazyphone.cache_clear_needs_player"));
            return 0;
        }
        return clearCacheFor(arguments.getSource(), player);
    }

    private static int cacheClearPlayer(CommandContext<CommandSourceStack> arguments) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(arguments, "player");
        return clearCacheFor(arguments.getSource(), player);
    }

    /** Backing logic for both {@code /crazyphone cache clear} forms - tells the target player's own client
     * to wipe {@link fr.lordfinn.crazyphone.client.picture.FabricPictureCache} (RAM and disk both, see its
     * own {@code clearAll()} doc comment), for troubleshooting a stuck/corrupted local photo cache. */
    private static int clearCacheFor(CommandSourceStack source, ServerPlayer player) {
        NetworkAccess.sendToPlayer(player, new CrazyPhoneClearPictureCachePacket());
        source.sendSuccess(() -> Component.translatable("command.crazyphone.cache_cleared", player.getName())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    // --- shared helpers ---

    /** The command sender as an Entity, falling back to a fake player when run from console/command block
     * (a world is still needed for procedures that touch player-scoped state) - every subcommand needed
     * this same fallback, previously copy-pasted into all dozen command classes. Fabric API has no equivalent
     * fake-player factory; running one of these commands from console/command-block simply no-ops there
     * (every procedure already null-checks its entity parameter) rather than getting a half-working fake
     * player. */
    private static Entity resolveEntity(CommandContext<CommandSourceStack> arguments, Level world) {
        Entity entity = arguments.getSource().getEntity();
        //? if neoforge {
        if (entity == null && world instanceof ServerLevel serverLevel)
            entity = FakePlayerFactory.getMinecraft(serverLevel);
        //?}
        return entity;
    }

    private static Iterable<String> registeredNumbers(CommandContext<CommandSourceStack> context) {
        LevelAccessor world = context.getSource().getLevel();
        return world == null ? java.util.List.of() : fr.lordfinn.crazyphone.utils.NbtCompat.keySet(PhoneRegistrySavedData.get(world).phones);
    }

    private static Iterable<String> candidateNumbers(CommandContext<CommandSourceStack> context) {
        LevelAccessor world = context.getSource().getLevel();
        return world == null ? java.util.List.of() : fr.lordfinn.crazyphone.utils.NbtCompat.keySet(PhoneRegistrySavedData.get(world).mayorsCandidates);
    }
}
