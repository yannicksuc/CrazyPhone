package fr.lordfinn.crazyphone.client.livescreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.ClientConfig;
import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.ClientFeatureFlagState;
import fr.lordfinn.crazyphone.client.CrazyPhoneSelfiePose;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;
import fr.lordfinn.crazyphone.utils.NbtCompat;
import fr.lordfinn.crazyphone.utils.PhoneLiveScreenData;
import fr.lordfinn.crazyphone.utils.PhoneScreenApp;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

import javax.annotation.Nullable;

/**
 * Decides whether a phone being rendered gets a live screen at all, and if so builds its
 * {@link LiveScreenView}. Every "no" here means the phone simply keeps its normal static texture.
 *
 * Who gets a live screen: only a phone held in a hand (first or third person) by a PLAYER who isn't a
 * spectator. Not in GUI slots or the creative tab, not on the ground, not in item frames, not held by armor
 * stands or mobs: those phones are not "in use", and their item data may be stale (a phone dropped in the
 * middle of a call keeps its old call state on the item), so they never show anything live.
 *
 * Privacy: for another player's phone only the server-written, category-only item data is read (call state,
 * call start time, app category) and only when their server explicitly shares it. Caller names and unread
 * counts exist only for the local player's own phone, read from their own client state.
 */
public final class LiveScreenViews {
    /** Beyond this, another player's phone is a few pixels on screen: not worth any work. */
    private static final double MAX_OTHER_DISTANCE_SQ = 32 * 32;

    private LiveScreenViews() {
    }

    static boolean isHand(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
    }

    public static @Nullable LiveScreenView resolve(ItemStack stack, @Nullable LivingEntity holder, ItemDisplayContext context) {
        if (stack == null || stack.isEmpty() || stack.getItem() != ModItems.CRAZY_PHONE.get() || !isHand(context))
            return null;
        if (!(holder instanceof Player player) || player.isSpectator())
            return null;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (mc.player == null || level == null)
            return null;
        boolean own = player == mc.player;
        if (own ? !ClientConfig.liveScreenOwnPhone : !ClientConfig.liveScreenOtherPhones)
            return null;
        if (!own) {
            if (!ClientFeatureFlagState.isExplicitlyEnabled(FeatureFlag.LIVE_SCREEN))
                return null;
            if (player.distanceToSqr(mc.player) > MAX_OTHER_DISTANCE_SQ)
                return null;
        }
        // The selfie-stick models have a different shape: no screen face to draw on.
        if (CrazyPhoneSelfiePose.isSelfieFraming(player))
            return null;

        CompoundTag tag = PhoneTagAccess.getTag(stack);
        String number = NbtCompat.getString(tag, "number");
        String callState = NbtCompat.getString(tag, "callState");
        long gameTime = level.getGameTime();
        // 4 updates a second; 12 is a multiple of every animation's own cycle (2, 3 and 4 frames).
        int frame = (int) ((gameTime / 5) % 12);
        PhoneScreenApp app = own
                ? (isActivePhone(player, stack) ? LiveScreenClient.localApp() : PhoneScreenApp.NONE)
                : PhoneLiveScreenData.readApp(tag);

        switch (callState) {
            case "RINGING" -> {
                String caller = own && ClientCallState.numberHasState(number, State.RINGING)
                        ? LiveScreenClient.incomingCaller(ClientCallState.getCallId()) : "";
                return view(LiveScreenView.Mode.RINGING, PhoneScreenApp.NONE, 0, 0, -1, -1, caller.isEmpty() ? frame : (int) ((gameTime / 5) % 120), caller, 0);
            }
            case "CALLING" -> {
                return view(LiveScreenView.Mode.DIALING, PhoneScreenApp.NONE, 0, 0, -1, -1, frame, "", 0);
            }
            case "ACTIVE" -> {
                if (app == PhoneScreenApp.NONE || app == PhoneScreenApp.CALL)
                    return view(LiveScreenView.Mode.IN_CALL, PhoneScreenApp.NONE, 0, 0, -1,
                            callSeconds(own, number, tag, gameTime), frame, "", participants(own, number));
            }
            default -> {
            }
        }
        if (app == PhoneScreenApp.CAMERA)
            return view(LiveScreenView.Mode.CAMERA, PhoneScreenApp.CAMERA, 0, 0, -1, -1, frame % 4, "", 0);
        if (app != PhoneScreenApp.NONE)
            return view(LiveScreenView.Mode.APP, app, 0, 0, -1, -1, frame, "", 0);

        long dayTime = Math.floorMod(dayTime(level), 24000L);
        int hour = (int) ((dayTime / 1000 + 6) % 24);
        int minute = (int) ((dayTime % 1000) * 60 / 1000);
        int phase = hour >= 20 || hour < 5 ? LiveScreenView.NIGHT
                : (hour < 7 || hour >= 18) ? LiveScreenView.TWILIGHT : LiveScreenView.DAY;
        int unread = own ? LiveScreenClient.unreadFor(number) : -1;
        return view(LiveScreenView.Mode.LOCK, PhoneScreenApp.NONE, hour * 60 + minute, phase, unread, -1, 0, "", 0);
    }

    private static LiveScreenView view(LiveScreenView.Mode mode, PhoneScreenApp app, int clock, int phase, int unread,
                                       int seconds, int frame, String caller, int participants) {
        return new LiveScreenView(mode, app, clock, phase, unread, seconds, frame, caller, participants);
    }

    /** The phone the local player's open phone UI belongs to: the main hand one, else the off hand one. */
    private static boolean isActivePhone(Player player, ItemStack stack) {
        ItemStack main = player.getMainHandItem();
        boolean mainIsPhone = !main.isEmpty() && main.getItem() == ModItems.CRAZY_PHONE.get();
        if (mainIsPhone)
            return stack == main || ItemStack.matches(stack, main);
        ItemStack off = player.getOffhandItem();
        return stack == off || ItemStack.matches(stack, off);
    }

    private static int callSeconds(boolean own, String number, CompoundTag tag, long gameTime) {
        if (own && ClientCallState.numberHasState(number, State.ACTIVE)) {
            long since = ClientCallState.getActiveSinceMillis();
            if (since > 0)
                return (int) Math.max(0, Math.min(Integer.MAX_VALUE, (System.currentTimeMillis() - since) / 1000));
        }
        long since = PhoneLiveScreenData.readCallSince(tag);
        if (since < 0 || since > gameTime)
            return -1;
        return (int) Math.min(Integer.MAX_VALUE, (gameTime - since) / 20);
    }

    private static int participants(boolean own, String number) {
        if (own && ClientCallState.numberHasState(number, State.ACTIVE))
            return Math.min(4, 1 + ClientCallState.getOtherParticipantCount());
        return 2;
    }

    private static long dayTime(ClientLevel level) {
        //? if >=26 {
        /*return level.getOverworldClockTime();
        *///?} else {
        return level.getDayTime();
        //?}
    }
}
