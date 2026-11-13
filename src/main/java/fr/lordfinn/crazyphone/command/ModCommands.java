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
// Forge 1.20.1: same FORGE-bus event and fake player, at their original net.minecraftforge.* paths.
//? if legacyforge {
/*import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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
//? if neoforge || legacyforge {
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
//? if neoforge || legacyforge {
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

    //? if neoforge || legacyforge {
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
        //? if neoforge || legacyforge {
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
                // Dev only (CRAZYPHONE_DEV_COMMANDS=1 on the server): opens one phone page directly, to look
                // at every screen in a test game without clicking through the phone.
                .then(Commands.literal("open").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("screen", StringArgumentType.word()).suggests(DEV_SCREENS)
                                .executes(ModCommands::devOpen)
                                .then(Commands.argument("data", StringArgumentType.greedyString())
                                        .executes(ModCommands::devOpen))))
                // Dev only too: unlocks the phone in hand as if its password had been typed in.
                .then(Commands.literal("unlock").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("password", StringArgumentType.word())
                                .executes(ModCommands::devUnlock)))
                // Dev only too: registers the phone in hand as if its registration form had been filled in.
                .then(Commands.literal("register").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("number", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("password", StringArgumentType.word())
                                                .executes(ModCommands::devRegister)))))
                // Dev only too: fills the test world for the phone in hand (two more phones as contacts, a
                // group with them, a test photo) and prints the group and photo ids.
                // Dev only too: the call buttons, for a test game (start, answer, video, hangup, open).
                .then(Commands.literal("call").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("action", StringArgumentType.word())
                                .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        new String[]{"start", "answer", "video", "hangup", "open"}, builder))
                                .then(Commands.argument("conversation", StringArgumentType.greedyString())
                                        .executes(ModCommands::devCall))))
                // Dev only too: a group of the phone in hand with these numbers (prints its id).
                .then(Commands.literal("group").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("numbers", StringArgumentType.greedyString())
                                .executes(ModCommands::devGroup)))
                // Dev only too: a text message from any number to a conversation.
                .then(Commands.literal("devmsg").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("conversation", StringArgumentType.word())
                                .then(Commands.argument("sender", StringArgumentType.word())
                                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                                .executes(arguments -> {
                                                    fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.addMessage(arguments.getSource().getLevel(),
                                                            StringArgumentType.getString(arguments, "conversation"),
                                                            StringArgumentType.getString(arguments, "sender"),
                                                            StringArgumentType.getString(arguments, "text"),
                                                            (int) (java.time.Instant.now().getEpochSecond() / 60));
                                                    return 1;
                                                })))))
                // Dev only too: a voice message of that many seconds (silent audio, made-up waveform).
                .then(Commands.literal("devvoice").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("conversation", StringArgumentType.word())
                                .then(Commands.argument("sender", StringArgumentType.word())
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                                                .executes(arguments -> {
                                                    int seconds = IntegerArgumentType.getInteger(arguments, "seconds");
                                                    java.util.Random random = new java.util.Random();
                                                    byte[] envelope = new byte[24];
                                                    for (int i = 0; i < envelope.length; i++)
                                                        envelope[i] = (byte) (40 + random.nextInt(200));
                                                    fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.addVoiceMessage(arguments.getSource().getLevel(),
                                                            StringArgumentType.getString(arguments, "conversation"),
                                                            StringArgumentType.getString(arguments, "sender"),
                                                            java.util.UUID.randomUUID(), new byte[48000 * 2 * seconds], seconds * 20, envelope,
                                                            (int) (java.time.Instant.now().getEpochSecond() / 60));
                                                    return 1;
                                                })))))
                // Dev only too: a photo of the sender's gallery (by its index, 0 = newest) sent to a conversation.
                .then(Commands.literal("devimg").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("conversation", StringArgumentType.word())
                                .then(Commands.argument("sender", StringArgumentType.word())
                                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                                .executes(arguments -> {
                                                    String sender = StringArgumentType.getString(arguments, "sender");
                                                    java.util.List<java.util.UUID> gallery = fr.lordfinn.crazyphone.data.PhotoSavedData
                                                            .get(arguments.getSource().getLevel()).getPhotoIdsForOwner(sender);
                                                    int index = IntegerArgumentType.getInteger(arguments, "index");
                                                    if (index >= gallery.size())
                                                        return 0;
                                                    fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.addImageMessage(arguments.getSource().getLevel(),
                                                            StringArgumentType.getString(arguments, "conversation"), sender, gallery.get(index),
                                                            (int) (java.time.Instant.now().getEpochSecond() / 60));
                                                    return 1;
                                                })))))
                // Dev only too: lists a number's photos (index = id), or deletes some by index.
                .then(Commands.literal("devphotos").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .executes(arguments -> {
                                    java.util.List<java.util.UUID> gallery = fr.lordfinn.crazyphone.data.PhotoSavedData
                                            .get(arguments.getSource().getLevel()).getPhotoIdsForOwner(StringArgumentType.getString(arguments, "owner"));
                                    for (int i = 0; i < gallery.size(); i++) {
                                        String line = i + " = " + gallery.get(i);
                                        arguments.getSource().sendSuccess(() -> Component.literal(line), false);
                                    }
                                    return gallery.size();
                                })
                                .then(Commands.argument("deleteIndexes", StringArgumentType.greedyString())
                                        .executes(arguments -> {
                                            String owner = StringArgumentType.getString(arguments, "owner");
                                            fr.lordfinn.crazyphone.data.PhotoSavedData data = fr.lordfinn.crazyphone.data.PhotoSavedData.get(arguments.getSource().getLevel());
                                            java.util.List<java.util.UUID> gallery = data.getPhotoIdsForOwner(owner);
                                            java.util.Set<java.util.UUID> doomed = new java.util.HashSet<>();
                                            for (String token : StringArgumentType.getString(arguments, "deleteIndexes").trim().split("\\s+")) {
                                                int i = Integer.parseInt(token);
                                                if (i >= 0 && i < gallery.size())
                                                    doomed.add(gallery.get(i));
                                            }
                                            data.deletePhotos(owner, doomed);
                                            return doomed.size();
                                        }))))
                // Dev only too: adds a contact to a phone (devcontact <owner> <number>), or toggles it as a favorite.
                .then(Commands.literal("devcontact").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .then(Commands.argument("number", StringArgumentType.word())
                                        .executes(arguments -> {
                                            fr.lordfinn.crazyphone.procedures.CrazyPhoneAddContactToPhoneProcedure.execute(arguments.getSource().getLevel(),
                                                    StringArgumentType.getString(arguments, "number"), StringArgumentType.getString(arguments, "owner"));
                                            return 1;
                                        }))))
                // Dev only too: the player UUID a phone shows as its contact head (test contacts get a face).
                .then(Commands.literal("devuuid").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("number", StringArgumentType.word())
                                .then(Commands.argument("uuid", StringArgumentType.word())
                                        .executes(arguments -> {
                                            fr.lordfinn.crazyphone.data.PhoneRegistrySavedData registry =
                                                    fr.lordfinn.crazyphone.data.PhoneRegistrySavedData.get(arguments.getSource().getLevel());
                                            if (!(registry.phones.get(StringArgumentType.getString(arguments, "number")) instanceof net.minecraft.nbt.CompoundTag phone))
                                                return 0;
                                            phone.putString("uuid", StringArgumentType.getString(arguments, "uuid"));
                                            registry.setDirty();
                                            return 1;
                                        }))))
                // Dev only too: opens the resize screen of the nearest photo frame, as a right-click on it would.
                .then(Commands.literal("devframe").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .executes(arguments -> {
                            net.minecraft.server.level.ServerPlayer player = arguments.getSource().getPlayerOrException();
                            var frames = player.level().getEntitiesOfClass(fr.lordfinn.crazyphone.entity.CrazyPhonePhotoFrameEntity.class,
                                    player.getBoundingBox().inflate(8));
                            frames.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
                            if (frames.isEmpty())
                                return 0;
                            fr.lordfinn.crazyphone.utils.ScreenMenuUtils.openPhotoFrameResizeMenu(player, frames.get(0));
                            return 1;
                        }))
                .then(Commands.literal("devfav").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("owner", StringArgumentType.word())
                                .then(Commands.argument("number", StringArgumentType.word())
                                        .executes(arguments -> {
                                            fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.toggleFavorite(arguments.getSource().getLevel(),
                                                    StringArgumentType.getString(arguments, "owner"), StringArgumentType.getString(arguments, "number"));
                                            return 1;
                                        }))))
                // Dev only too: names a group, and gives it the off-hand item as its icon when there is one.
                .then(Commands.literal("groupname").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .then(Commands.argument("conversation", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(arguments -> {
                                            net.minecraft.server.level.ServerPlayer player = arguments.getSource().getPlayerOrException();
                                            String conversation = StringArgumentType.getString(arguments, "conversation");
                                            fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.renameGroup(player.level(), conversation,
                                                    StringArgumentType.getString(arguments, "name"));
                                            if (!player.getOffhandItem().isEmpty())
                                                fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.setGroupIcon(player.level(), conversation,
                                                        player.getOffhandItem().copyWithCount(1));
                                            return 1;
                                        }))))
                .then(Commands.literal("seed").requires(source -> DEV_COMMANDS && permLevel(4).test(source))
                        .executes(ModCommands::devSeed)
                        // Names of the two test contacts (201 and 202), Mastu and Zafeel by default.
                        .then(Commands.argument("first", StringArgumentType.word())
                                .then(Commands.argument("second", StringArgumentType.word())
                                        .executes(ModCommands::devSeed))))
                .then(Commands.literal("cache").requires(permLevel(4))
                        .then(Commands.literal("clear")
                                .executes(ModCommands::cacheClearSelf)
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ModCommands::cacheClearPlayer))));
    }

    // --- dev ---

    private static final boolean DEV_COMMANDS = "1".equals(System.getenv("CRAZYPHONE_DEV_COMMANDS"))
            || Boolean.getBoolean("crazyphone.devCommands");
    private static final String[] DEV_SCREEN_IDS = {"crazyphone_home_screen", "crazy_phone_sign_in_screen",
            "crazy_phone_password_screen", "crazy_phone_contacts_screen", "crazy_phone_conversation",
            "crazy_phone_group_settings_screen", "crazy_phone_mayors_candidates_list", "my_photos"};
    private static final com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack> DEV_SCREENS =
            (context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(DEV_SCREEN_IDS, builder);

    private static int devOpen(CommandContext<CommandSourceStack> arguments) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = arguments.getSource().getPlayerOrException();
        String screen = StringArgumentType.getString(arguments, "screen");
        String data;
        try {
            data = StringArgumentType.getString(arguments, "data");
        } catch (IllegalArgumentException noData) {
            data = null;
        }
        net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        switch (screen) {
            case "my_photos" -> fr.lordfinn.crazyphone.utils.ScreenMenuUtils.openPhoneMyPhotosMenu(player, hand, data == null ? "" : data);
            default -> fr.lordfinn.crazyphone.utils.ScreenMenuUtils.openCrazyPhoneMenuByTag(player, hand, "crazyphone:" + screen, data);
        }
        return 1;
    }

    private static int devGroup(CommandContext<CommandSourceStack> arguments) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = arguments.getSource().getPlayerOrException();
        String owner = fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        java.util.List<String> members = new java.util.ArrayList<>(java.util.Arrays.asList(StringArgumentType.getString(arguments, "numbers").trim().split("\\s+")));
        members.add(owner);
        String groupId = fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.generateGroupConversationId();
        fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.createGroup(player.level(), groupId, members, owner);
        String result = "group=" + groupId;
        arguments.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int devCall(CommandContext<CommandSourceStack> arguments) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = arguments.getSource().getPlayerOrException();
        int action = switch (StringArgumentType.getString(arguments, "action")) {
            case "start" -> fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage.START_CALL;
            case "answer" -> fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage.ANSWER;
            case "video" -> fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage.TOGGLE_VIDEO;
            case "hangup" -> fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage.HANGUP;
            default -> fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage.OPEN_CALL_SCREEN;
        };
        String conversation = StringArgumentType.getString(arguments, "conversation");
        fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage.handleAction(player, action, conversation);
        // Reports where the call stands, so a test can tell why nothing happened.
        String number = fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        String status = "number=" + number
                + " members=" + fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.getGroupMembers(player.level(), conversation)
                + " voice=" + fr.lordfinn.crazyphone.voicechat.VoicechatIntegration.isAvailable()
                + " session=" + fr.lordfinn.crazyphone.voicechat.CallRegistry.getSessionFor(player.getUUID())
                        .map(s -> "in:" + s.participants.size() + " ringing:" + s.ringing.size()).orElse("none");
        arguments.getSource().sendSuccess(() -> Component.literal(status), false);
        return 1;
    }

    private static int devUnlock(CommandContext<CommandSourceStack> arguments) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = arguments.getSource().getPlayerOrException();
        java.util.HashMap<String, String> form = new java.util.HashMap<>();
        form.put("textin:password", StringArgumentType.getString(arguments, "password"));
        fr.lordfinn.crazyphone.procedures.CrazyPhoneTrySignInProcedure.execute(
                player.level(), player.getX(), player.getY(), player.getZ(), player, form);
        return 1;
    }

    private static int devRegister(CommandContext<CommandSourceStack> arguments) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = arguments.getSource().getPlayerOrException();
        java.util.HashMap<String, String> form = new java.util.HashMap<>();
        form.put("textin:number", StringArgumentType.getString(arguments, "number"));
        form.put("textin:name", StringArgumentType.getString(arguments, "name"));
        form.put("textin:password", StringArgumentType.getString(arguments, "password"));
        fr.lordfinn.crazyphone.procedures.CrazyPhoneInitialFormValidationButtonClickProcedure.execute(
                player.level(), player.getX(), player.getY(), player.getZ(), player, form);
        return 1;
    }

    private static int devSeed(CommandContext<CommandSourceStack> arguments) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = arguments.getSource().getPlayerOrException();
        Level world = player.level();
        String owner = fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        if (owner == null || owner.isEmpty()) {
            arguments.getSource().sendFailure(Component.literal("Hold a registered phone first (/crazyphone register ...)"));
            return 0;
        }
        String first = "Mastu", second = "Zafeel";
        try {
            first = StringArgumentType.getString(arguments, "first");
            second = StringArgumentType.getString(arguments, "second");
        } catch (IllegalArgumentException defaultNames) {
        }
        String[][] others = {{"201", first}, {"202", second}};
        for (String[] other : others) {
            java.util.HashMap<String, String> form = new java.util.HashMap<>();
            form.put("textin:number", other[0]);
            form.put("textin:name", other[1]);
            form.put("textin:password", "");
            if (!fr.lordfinn.crazyphone.procedures.IsPhoneInUseProcedure.execute(world, other[0]))
                fr.lordfinn.crazyphone.procedures.RegisterNewPhoneFromFormProcedure.execute(world, player,
                        new net.minecraft.world.item.ItemStack(fr.lordfinn.crazyphone.init.ModItems.CRAZY_PHONE.get()), form);
            fr.lordfinn.crazyphone.procedures.CrazyPhoneAddContactToPhoneProcedure.execute(world, other[0], owner);
        }
        String groupId = fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.generateGroupConversationId();
        fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.createGroup(world, groupId, java.util.List.of("201", "202", owner), owner);

        java.util.UUID photoId;
        try {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(160, 120, java.awt.image.BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 120; y++)
                for (int x = 0; x < 160; x++) {
                    int hill = 80 + (int) (12 * Math.sin(x / 18.0));
                    int rgb = y < hill ? (0x5A9BD8 + (y / 4) * 0x010100) : (y < hill + 4 ? 0x5DAA3C : 0x7A5634);
                    if ((x - 120) * (x - 120) + (y - 28) * (y - 28) < 120)
                        rgb = 0xFFD84A;
                    image.setRGB(x, y, rgb);
                }
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", png);
            byte[] bytes = png.toByteArray();
            photoId = fr.lordfinn.crazyphone.data.PhotoSavedData.get(world).storePhoto(owner, "", null, bytes, bytes, 0);
        } catch (java.io.IOException e) {
            arguments.getSource().sendFailure(Component.literal("Could not make the test photo: " + e));
            return 0;
        }
        String result = "seed ok, group=" + groupId + " photo=" + photoId;
        arguments.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
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

    //? if neoforge || legacyforge {
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
        //? if neoforge || legacyforge {
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
