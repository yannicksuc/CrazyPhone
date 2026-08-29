package fr.lordfinn.crazyphone.gametest;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneOnUseProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.voicechat.CallRegistry;

import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

import net.minecraft.commands.CommandSourceStack;
//? if <26 {
import net.minecraft.gametest.framework.GameTest;
//?}
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
//? if <26 {
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
//?}

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Real-world integration coverage the unit tests deliberately can't reach: an actual ServerLevel + real
 * ServerPlayer(s) placed via {@link GameTestHelper#makeMockServerPlayerInLevel()}, real command dispatch
 * through the registered Brigadier tree, and real menu-opening side effects. Every test's own structure
 * (crazyphone:platform, a bare 3x3 stone floor) is irrelevant to what's being verified here - this mod has
 * no block-placement behavior of its own to test, only entity/item/data interactions, so the platform
 * exists purely to give the test player somewhere to legally stand.
 */
//? if <26 {
@GameTestHolder(Crazyphone.MODID)
@PrefixGameTestTemplate(false) // every method below shares the one "platform" structure, no per-class prefix needed
//?}
public class CrazyPhoneGameTests {

    private static ItemStack freshCrazyPhone() {
        return new ItemStack(ModItems.CRAZY_PHONE.get());
    }

    /** GameTestHelper#makeMockServerPlayerInLevel() places the player through the REAL PlayerList.placeNewPlayer
     * path - connection, level placement, and player-list registration all happen there before it fires
     * PlayerLoggedInEvent as its very last step. This mod's own login-sync handler (PhoneAttachmentTypes)
     * then tries to send a packet over the mock's fake, handshake-less connection and throws, which
     * propagates out of makeMockServerPlayerInLevel() itself - losing the otherwise fully-usable player
     * along with it, since the method never reaches its own return statement. Recovering it from the
     * player list (just-appended, so it's the last entry) avoids needing to touch that production code
     * path just to accommodate this test-only limitation. */
    private static ServerPlayer makeTestPlayer(GameTestHelper helper) {
        try {
            return helper.makeMockServerPlayerInLevel();
        } catch (RuntimeException e) {
            List<ServerPlayer> players = helper.getLevel().getServer().getPlayerList().getPlayers();
            return players.get(players.size() - 1);
        }
    }

    private static String votedFor(PhoneRegistrySavedData registry, String voterNumber) {
        return registry.mayorVotes.get(voterNumber) instanceof StringTag tag ? fr.lordfinn.crazyphone.utils.NbtCompat.asString(tag) : null;
    }

    /** {@code GameTestHelper#assertValueEqual} doesn't exist pre-1.20.5 - falls back to a plain
     *  equals-check through {@code assertTrue}, which both versions have. */
    private static void assertValueEqual(GameTestHelper helper, String actual, String expected, String message) {
        //? if >=1.20.5 {
        /*helper.assertValueEqual(actual, expected, message);
        *///? } else {
        helper.assertTrue(java.util.Objects.equals(actual, expected), message);
        //?}
    }

    /** The mock player's connection (see makeTestPlayer) never completed a real handshake, so NeoForge's
     * NetworkRegistry correctly refuses to send ANY custom packet through it - a test-harness limitation,
     * not a signal about the code actually under test. Any OTHER exception still propagates normally. */
    private static void ignoringMockConnectionPacketLimits(Runnable action) {
        try {
            action.run();
        } catch (UnsupportedOperationException e) {
            if (e.getMessage() == null || !e.getMessage().contains("may not be sent to the client"))
                throw e;
        }
    }

    private static String currentScreenOf(ServerPlayer player) {
        return player.getData(PhoneAttachmentTypes.PLAYER_PHONE_STATE).currentCrazyPhoneScreenOpened;
    }

    private static void resetRegistry(GameTestHelper helper) {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(helper.getLevel());
        registry.phones = new CompoundTag();
        registry.mayorsCandidates = new CompoundTag();
        registry.mayorVotes = new CompoundTag();
        registry.lastMayorVoteTimestamps = new CompoundTag();
        registry.isMayorVotingOn = false;
        for (FeatureFlag flag : FeatureFlag.values())
            flag.setGloballyEnabled(true);
    }

    /** An unregistered (no name/number set up yet) phone must route to the password/sign-up screen, never
     * straight to the home screen - CrazyPhoneOnUseProcedure's very first branch.
     *
     * Verifies via PlayerPhoneState rather than player.containerMenu: the menu constructor (which records
     * the opened screen there via ScreenMenuUtils.pushScreen) runs before ServerPlayer#openMenu's own
     * packet send, but containerMenu itself is only assigned AFTER that send succeeds - which it can't,
     * against the mock player's handshake-less connection (see ignoringMockConnectionPacketLimits). */
    //? if <26 {
    @GameTest(template = "platform", batch = "unregisteredPhone")
    //?}
    public static void unregisteredPhone_useOpensPasswordScreen(GameTestHelper helper) {
        resetRegistry(helper);
        ServerPlayer player = makeTestPlayer(helper);
        player.getInventory().setItem(0, freshCrazyPhone());
        player.getInventory()/*$ set_selected_slot_0 {*/.selected = 0/*$}*/;

        ignoringMockConnectionPacketLimits(() ->
                CrazyPhoneOnUseProcedure.execute(helper.getLevel(), player.getX(), player.getY(), player.getZ(), player));

        String opened = currentScreenOf(player);
        helper.assertTrue(opened != null && opened.contains("crazy_phone_password_screen"),
                "an unregistered phone must open the password/sign-up screen, got " + opened);
        helper.succeed();
    }

    /** A phone that's both set up (name/number registered) AND already unlocked this session (isOpen=true
     * - distinct from being merely registered: a registered-but-not-yet-unlocked phone correctly routes to
     * the sign-in screen first instead, exercised by the initial version of this test, which used to
     * assert this exact scenario without setting isOpen and consequently failed against real
     * CrazyPhoneOnUseProcedure behavior, not a bug in it) must open straight to the home screen. See
     * unregisteredPhone_useOpensPasswordScreen's javadoc for why PlayerPhoneState is checked instead of
     * containerMenu. */
    //? if <26 {
    @GameTest(template = "platform", batch = "registeredPhone")
    //?}
    public static void registeredPhone_useOpensHomeScreen(GameTestHelper helper) {
        resetRegistry(helper);
        ServerPlayer player = makeTestPlayer(helper);
        ItemStack phone = freshCrazyPhone();
        PhoneTagAccess.updateTag(phone, tag -> {
            tag.putString("name", "Alice");
            tag.putString("number", "555");
            tag.putBoolean("isOpen", true);
        });
        player.getInventory().setItem(0, phone);
        player.getInventory()/*$ set_selected_slot_0 {*/.selected = 0/*$}*/;
        PhoneRegistrySavedData.get(helper.getLevel()).phones.put("555", new CompoundTag());

        ignoringMockConnectionPacketLimits(() ->
                CrazyPhoneOnUseProcedure.execute(helper.getLevel(), player.getX(), player.getY(), player.getZ(), player));

        String opened = currentScreenOf(player);
        helper.assertTrue(opened != null && opened.contains("crazyphone_home_screen"),
                "a set-up, unlocked phone must open straight to the home screen, got " + opened);
        helper.succeed();
    }

    /** With no SVC server installed (the normal case for this headless GameTestServer run - it's an
     * optional, compileOnly dependency), starting a call must degrade gracefully: no crash, no half-broken
     * state - see SvcCallBridge.isCallable/createCallGroup. Wrapped in ignoringMockConnectionPacketLimits
     * because even the "no SVC, bail out early" path may get far enough to attempt a state-sync packet
     * before failing - that's the mock connection's limitation, not evidence startCall itself crashed. */
    //? if <26 {
    @GameTest(template = "platform", batch = "startCallNoSvc")
    //?}
    public static void startCall_withoutSvcInstalled_degradesGracefullyInsteadOfCrashing(GameTestHelper helper) {
        ServerPlayer initiator = makeTestPlayer(helper);
        ServerPlayer callee = makeTestPlayer(helper);

        ignoringMockConnectionPacketLimits(() -> CallRegistry.startCall("112.223", initiator, List.of(callee)));

        // Whether or not a session ended up existing (depends on whether SVC happens to be present in this
        // environment), the one invariant that must always hold: a player is never left "in a call"
        // without CallRegistry itself agreeing a session actually exists for them.
        Optional<CallRegistry.CallSession> session = CallRegistry.getSessionFor(initiator.getUUID());
        if (session.isEmpty()) {
            helper.assertTrue(CallRegistry.getSessionFor(callee.getUUID()).isEmpty(),
                    "initiator has no session but callee does - inconsistent half-started call state");
        } else {
            helper.assertTrue(session.get().participants.contains(initiator.getUUID()) || session.get().ringing.contains(initiator.getUUID()),
                    "a session exists for the initiator but doesn't actually list them as a participant or ringer");
        }

        // If startCall did leave a session behind (SVC absent from this headless run, but the "no SVC" bail
        // path still ran far enough to register one), tear it down explicitly rather than leaving these mock
        // players dangling in CallRegistry's static maps for the rest of the JVM's lifetime - every other
        // batch in this class runs in the same process, and CallTerminationListener's periodic sweep would
        // otherwise keep tripping over these two on every future tick.
        ignoringMockConnectionPacketLimits(() -> CallRegistry.leave(initiator));
        ignoringMockConnectionPacketLimits(() -> CallRegistry.leave(callee));
        helper.succeed();
    }

    /** Full round trip through the REAL registered command tree: register two phones, add one as a
     * candidate, open voting, cast a vote via the actual "/crazyphone mayor vote" command, then confirm an
     * immediate re-vote attempt is blocked by the 600-tick cooldown - the tricky stateful logic that's hard
     * to reach without a real CommandSourceStack. */
    //? if <26 {
    @GameTest(template = "platform", batch = "mayorVoteCooldown")
    //?}
    public static void mayorVote_viaRealCommand_recordsVoteThenBlocksImmediateRevote(GameTestHelper helper) {
        resetRegistry(helper);
        ServerPlayer voter = makeTestPlayer(helper);

        ItemStack voterPhone = freshCrazyPhone();
        PhoneTagAccess.updateTag(voterPhone, tag -> {
            tag.putString("name", "Voter");
            tag.putString("number", "555");
        });
        voter.getInventory().setItem(0, voterPhone);
        voter.getInventory()/*$ set_selected_slot_0 {*/.selected = 0/*$}*/;

        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(helper.getLevel());
        registry.phones.put("555", new CompoundTag());
        registry.phones.put("666", new CompoundTag());
        registry.mayorsCandidates.put("666", new CompoundTag());
        registry.isMayorVotingOn = true;

        CommandSourceStack source = voter.createCommandSourceStack();
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "crazyphone mayor vote 666");

        assertValueEqual(helper, votedFor(registry, "555"), "666", "vote must be recorded for the voter's own number, pointing at the candidate");

        // Immediately re-vote for a DIFFERENT candidate - must be rejected by the 600-tick cooldown, not
        // silently accepted as a changed vote.
        registry.phones.put("777", new CompoundTag());
        registry.mayorsCandidates.put("777", new CompoundTag());
        helper.getLevel().getServer().getCommands().performPrefixedCommand(voter.createCommandSourceStack(), "crazyphone mayor vote 777");

        assertValueEqual(helper, votedFor(registry, "555"), "666", "an immediate re-vote must be blocked by the cooldown - the original vote must still stand");
        helper.succeed();
    }

    /** The permission-node half of FeatureFlag#isEnabledFor needs a real PermissionAPI, which a plain
     * unit test can't provide - this confirms a globally-disabled feature actually blocks its command path
     * end-to-end (not just that the config bit flips). */
    //? if <26 {
    @GameTest(template = "platform", batch = "mayorVoteFlagDisabled")
    //?}
    public static void mayorVote_whileFeatureGloballyDisabled_isBlocked(GameTestHelper helper) {
        resetRegistry(helper);
        ServerPlayer voter = makeTestPlayer(helper);
        ItemStack voterPhone = freshCrazyPhone();
        PhoneTagAccess.updateTag(voterPhone, tag -> {
            tag.putString("name", "Voter");
            tag.putString("number", "555");
        });
        voter.getInventory().setItem(0, voterPhone);
        voter.getInventory()/*$ set_selected_slot_0 {*/.selected = 0/*$}*/;

        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(helper.getLevel());
        registry.phones.put("555", new CompoundTag());
        registry.phones.put("666", new CompoundTag());
        registry.mayorsCandidates.put("666", new CompoundTag());
        registry.isMayorVotingOn = true;

        FeatureFlag.MAYOR_VOTING.setGloballyEnabled(false);

        helper.getLevel().getServer().getCommands().performPrefixedCommand(voter.createCommandSourceStack(), "crazyphone mayor vote 666");

        helper.assertTrue(votedFor(registry, "555") == null,
                "voting must be a no-op entirely while the feature is globally disabled, not just gray out client-side");
        helper.succeed();
    }

    /** Full lifecycle of the level-4 (op-only) mayor candidate/vote-clear commands, dispatched through the
     * real command tree with the SERVER's own console CommandSourceStack (permission level 4) rather than the
     * mock player's own (which defaults to level 0, same as any un-opped player, and would be rejected by
     * these commands' {@code requires(s -> s.hasPermission(4))} gate) - covers
     * CrazyPhoneAddNewMayorCandidateProcedure and CrazyPhoneRemoveMayorCandidateProcedure, neither of which
     * had any coverage before, plus RemoveVoteByNumberProcedure (the "votes clear" admin command, distinct
     * from a voter's own vote being blocked/overwritten). Candidate add/remove both call
     * PhoneRegistrySavedData#syncToAll, which broadcasts to every connected player - including the mock
     * voter's handshake-less connection - hence ignoringMockConnectionPacketLimits around those two. */
    //? if <26 {
    @GameTest(template = "platform", batch = "mayorCandidateAndVoteClearLifecycle")
    //?}
    public static void mayorCandidateAndVoteClear_viaRealCommands_manageFullLifecycle(GameTestHelper helper) {
        resetRegistry(helper);
        ServerPlayer voter = makeTestPlayer(helper);
        ItemStack voterPhone = freshCrazyPhone();
        PhoneTagAccess.updateTag(voterPhone, tag -> {
            tag.putString("name", "Voter");
            tag.putString("number", "555");
        });
        voter.getInventory().setItem(0, voterPhone);
        voter.getInventory()/*$ set_selected_slot_0 {*/.selected = 0/*$}*/;

        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(helper.getLevel());
        registry.phones.put("555", new CompoundTag());
        registry.phones.put("666", new CompoundTag());
        registry.isMayorVotingOn = true;

        CommandSourceStack console = helper.getLevel().getServer().createCommandSourceStack();

        ignoringMockConnectionPacketLimits(() ->
                helper.getLevel().getServer().getCommands().performPrefixedCommand(console, "crazyphone mayor candidate add 666"));
        helper.assertTrue(registry.mayorsCandidates.contains("666"), "candidate add must register the candidate");

        helper.getLevel().getServer().getCommands().performPrefixedCommand(voter.createCommandSourceStack(), "crazyphone mayor vote 666");
        assertValueEqual(helper, votedFor(registry, "555"), "666", "vote must be recorded before it's cleared");

        ignoringMockConnectionPacketLimits(() ->
                helper.getLevel().getServer().getCommands().performPrefixedCommand(console, "crazyphone mayor votes clear 555"));
        helper.assertTrue(!registry.mayorVotes.contains("555"), "votes clear must remove the recorded vote");

        ignoringMockConnectionPacketLimits(() ->
                helper.getLevel().getServer().getCommands().performPrefixedCommand(console, "crazyphone mayor candidate remove 666"));
        helper.assertTrue(!registry.mayorsCandidates.contains("666"), "candidate remove must unregister the candidate");

        helper.succeed();
    }

    /** Sanity check on the test helper itself: the phone actually held resolves to the number that was
     * stamped on it - if this ever fails, every other GameTest here that seeds a phone via CustomData is
     * suspect too. */
    //? if <26 {
    @GameTest(template = "platform", batch = "heldPhoneNumberSanity")
    //?}
    public static void heldPhoneNumber_resolvesCorrectly(GameTestHelper helper) {
        ServerPlayer player = makeTestPlayer(helper);
        ItemStack phone = freshCrazyPhone();
        PhoneTagAccess.updateTag(phone, tag -> tag.putString("number", "999"));
        player.getInventory().setItem(0, phone);
        player.getInventory()/*$ set_selected_slot_0 {*/.selected = 0/*$}*/;

        String number = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        assertValueEqual(helper, number, "999", "held phone number");
        helper.succeed();
    }

    /** Regression coverage for the "navigation must keep working while in a call" fix: screen push/pop
     * (back/home button handling) must not silently break or get blocked just because CallRegistry
     * considers this player to be in an active call - the two systems (screen history, call state) are
     * independent and neither should gate the other. */
    //? if <26 {
    @GameTest(template = "platform", batch = "navigateWhileInCall")
    //?}
    public static void screenNavigation_stillWorksNormally_whileInCall(GameTestHelper helper) {
        ServerPlayer caller = makeTestPlayer(helper);
        ServerPlayer callee = makeTestPlayer(helper);

        ignoringMockConnectionPacketLimits(() -> CallRegistry.startCall("777.888", caller, List.of(callee)));

        ScreenMenuUtils.pushScreen(caller, "crazyphone:crazyphone_home_screen", "");
        ScreenMenuUtils.pushScreen(caller, "crazyphone:crazy_phone_contacts_screen", "");
        String beforePop = currentScreenOf(caller);
        helper.assertTrue(beforePop != null && beforePop.contains("crazy_phone_contacts_screen"),
                "sanity: contacts screen must be current before testing the back action, got " + beforePop);

        ScreenMenuUtils.popScreen(caller);

        String afterPop = currentScreenOf(caller);
        helper.assertTrue(afterPop != null && afterPop.contains("crazyphone_home_screen"),
                "the back action must still return to the previous screen for a player with an active call session, got " + afterPop);

        ignoringMockConnectionPacketLimits(() -> CallRegistry.leave(caller));
        ignoringMockConnectionPacketLimits(() -> CallRegistry.leave(callee));
        helper.succeed();
    }

    /** Full round trip through the real menu-opening path (base class pushes the screen id onto history,
     * the group-settings constructor tags it with the conversationId) - none of the group-settings screen
     * plumbing (#52-72) had a real-player GameTest before, only the underlying procedures via
     * GroupProceduresTest's mocked LevelAccessor. */
    //? if <26 {
    @GameTest(template = "platform", batch = "openGroupSettingsMenu")
    //?}
    public static void groupSettingsMenu_opensViaRealMenuPath_taggedWithConversationId(GameTestHelper helper) {
        resetRegistry(helper);
        ServerPlayer admin = makeTestPlayer(helper);
        ItemStack adminPhone = freshCrazyPhone();
        PhoneTagAccess.updateTag(adminPhone, tag -> {
            tag.putString("name", "Admin");
            tag.putString("number", "555");
        });
        admin.getInventory().setItem(0, adminPhone);
        admin.getInventory()/*$ set_selected_slot_0 {*/.selected = 0/*$}*/;

        String conversationId = "group-gametest-" + UUID.randomUUID();
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(helper.getLevel());
        registry.phones.put("555", new CompoundTag());
        CompoundTag meta = new CompoundTag();
        meta.putString("name", "Test Squad");
        meta.putString("admin", "555");
        ListTag members = new ListTag();
        members.add(StringTag.valueOf("555"));
        meta.put("members", members);
        registry.groupMeta.put(conversationId, meta);

        ignoringMockConnectionPacketLimits(() ->
                ScreenMenuUtils.openGroupSettingsMenu(admin, InteractionHand.MAIN_HAND, conversationId));

        String opened = currentScreenOf(admin);
        helper.assertTrue(opened != null && opened.contains("crazy_phone_group_settings_screen"),
                "opening group settings must push the group settings screen id onto the navigation history, got " + opened);
        helper.assertTrue(opened.contains(conversationId),
                "the opened screen tag must carry the conversationId as its data, got " + opened);
        helper.succeed();
    }

    //? if >=26 {
    /*
    // 26.x removed @GameTest/@GameTestHolder entirely - vanilla's own annotation scanner is gone, replaced
    // by two separate registries a mod must populate explicitly: Registries.TEST_FUNCTION (the actual
    // Consumer<GameTestHelper> test bodies, a "simple" built-in registry - moddable via RegisterEvent the
    // same way this mod already adds custom SoundEvents, another "simple" registry, via ModSounds) and
    // Registries.TEST_INSTANCE (the per-test metadata: which function, which structure, which environment/
    // batch - populated via NeoForge's own RegisterGameTestsEvent, fired only when
    // GameTestHooks.isGametestEnabled() is true, same conditions the old annotation scanner ran under).
    // TestEnvironmentDefinition is the new home for what used to be @GameTest's plain "batch" string (tests
    // sharing an environment run as one sequential batch) - registered once per old batch name as an empty
    // AllOf(List.of()), since none of these tests need actual environment effects, only isolation from each
    // other's static state (CallRegistry, PhoneRegistrySavedData).
    private static final java.util.Map<String, net.minecraft.core.Holder<net.minecraft.gametest.framework.TestEnvironmentDefinition<?>>> ENVIRONMENTS = new java.util.HashMap<>();

    private static net.minecraft.core.Holder<net.minecraft.gametest.framework.TestEnvironmentDefinition<?>> environment(net.neoforged.neoforge.event.RegisterGameTestsEvent event, String batchName) {
        return ENVIRONMENTS.computeIfAbsent(batchName, name ->
                event.registerEnvironment(Crazyphone.resource(name), new net.minecraft.gametest.framework.TestEnvironmentDefinition.AllOf(java.util.List.of())));
    }

    public static void registerTestFunctions(net.neoforged.neoforge.registries.RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.TEST_FUNCTION, helper -> {
            helper.register(Crazyphone.resource("unregistered_phone_use_opens_password_screen"), (java.util.function.Consumer<GameTestHelper>) CrazyPhoneGameTests::unregisteredPhone_useOpensPasswordScreen);
            helper.register(Crazyphone.resource("registered_phone_use_opens_home_screen"), (java.util.function.Consumer<GameTestHelper>) CrazyPhoneGameTests::registeredPhone_useOpensHomeScreen);
            helper.register(Crazyphone.resource("start_call_without_svc_installed_degrades_gracefully"), (java.util.function.Consumer<GameTestHelper>) CrazyPhoneGameTests::startCall_withoutSvcInstalled_degradesGracefullyInsteadOfCrashing);
            helper.register(Crazyphone.resource("mayor_vote_via_real_command_records_then_blocks_revote"), (java.util.function.Consumer<GameTestHelper>) CrazyPhoneGameTests::mayorVote_viaRealCommand_recordsVoteThenBlocksImmediateRevote);
            helper.register(Crazyphone.resource("mayor_vote_while_feature_globally_disabled_is_blocked"), (java.util.function.Consumer<GameTestHelper>) CrazyPhoneGameTests::mayorVote_whileFeatureGloballyDisabled_isBlocked);
            helper.register(Crazyphone.resource("mayor_candidate_and_vote_clear_lifecycle"), (java.util.function.Consumer<GameTestHelper>) CrazyPhoneGameTests::mayorCandidateAndVoteClear_viaRealCommands_manageFullLifecycle);
            helper.register(Crazyphone.resource("held_phone_number_resolves_correctly"), (java.util.function.Consumer<GameTestHelper>) CrazyPhoneGameTests::heldPhoneNumber_resolvesCorrectly);
            helper.register(Crazyphone.resource("screen_navigation_still_works_while_in_call"), (java.util.function.Consumer<GameTestHelper>) CrazyPhoneGameTests::screenNavigation_stillWorksNormally_whileInCall);
            helper.register(Crazyphone.resource("group_settings_menu_opens_tagged_with_conversation_id"), (java.util.function.Consumer<GameTestHelper>) CrazyPhoneGameTests::groupSettingsMenu_opensViaRealMenuPath_taggedWithConversationId);
        });
    }

    public static void registerGameTests(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        ENVIRONMENTS.clear();
        registerTest(event, "unregistered_phone_use_opens_password_screen", "unregistered_phone");
        registerTest(event, "registered_phone_use_opens_home_screen", "registered_phone");
        registerTest(event, "start_call_without_svc_installed_degrades_gracefully", "start_call_no_svc");
        registerTest(event, "mayor_vote_via_real_command_records_then_blocks_revote", "mayor_vote_cooldown");
        registerTest(event, "mayor_vote_while_feature_globally_disabled_is_blocked", "mayor_vote_flag_disabled");
        registerTest(event, "mayor_candidate_and_vote_clear_lifecycle", "mayor_candidate_and_vote_clear_lifecycle");
        registerTest(event, "held_phone_number_resolves_correctly", "held_phone_number_sanity");
        registerTest(event, "screen_navigation_still_works_while_in_call", "navigate_while_in_call");
        registerTest(event, "group_settings_menu_opens_tagged_with_conversation_id", "open_group_settings_menu");
    }

    private static void registerTest(net.neoforged.neoforge.event.RegisterGameTestsEvent event, String testName, String batchName) {
        net.minecraft.resources.Identifier id = Crazyphone.resource(testName);
        net.minecraft.core.Holder<net.minecraft.gametest.framework.TestEnvironmentDefinition<?>> env = environment(event, batchName);
        net.minecraft.gametest.framework.TestData<net.minecraft.core.Holder<net.minecraft.gametest.framework.TestEnvironmentDefinition<?>>> testData =
                new net.minecraft.gametest.framework.TestData<>(env, Crazyphone.resource("platform"), 100, 0, true);
        event.registerTest(id, new net.minecraft.gametest.framework.FunctionGameTestInstance(
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.TEST_FUNCTION, id), testData));
    }
    *///?}
}
