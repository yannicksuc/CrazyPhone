package fr.lordfinn.crazyphone.client.livescreen;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
import net.neoforged.neoforge.event.TickEvent;
//?}
//?}
//? if legacyforge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
*///?}
//? if fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
*///?}

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.ClientConfig;
import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.client.ClientFeatureFlagState;
import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;
import fr.lordfinn.crazyphone.client.gui.PhoneScreen;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.PhoneScreenAppSyncPacket;
import fr.lordfinn.crazyphone.utils.NbtCompat;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhoneScreenApp;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client side state of the live phone screen, ticked once per client tick:
 * <ul>
 *     <li>works out which phone app the local player has on screen (from the open screen's class, by name,
 *     so screens added later by other features simply fall back to OTHER) and reports it to the server only
 *     when it changes, at most every few ticks, and only if the server explicitly advertised the
 *     live_screen feature (an older server never gets a packet it doesn't know);</li>
 *     <li>remembers the caller's name of the current incoming call (own phone only, never sent anywhere);</li>
 *     <li>caches the unread count of the phones in the local player's hands, only for phones registered to
 *     the local player themselves;</li>
 *     <li>forgets everything when the client leaves a world.</li>
 * </ul>
 * Client options (ClientConfig#liveScreenOwnPhone / #liveScreenOtherPhones) are persisted in
 * config/crazyphone-client.properties and toggled with the client command /phonescreen.
 */
//? if neoforge || legacyforge {
@EventBusSubscriber(value = Dist.CLIENT)
//?}
public final class LiveScreenClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    private static final int MIN_TICKS_BETWEEN_REPORTS = 4;
    private static final int UNREAD_REFRESH_TICKS = 20;
    private static final String CONFIG_FILE = "crazyphone-client.properties";

    private static PhoneScreenApp localApp = PhoneScreenApp.NONE;
    private static PhoneScreenApp lastSent = null;
    private static int ticksSinceSend = 0;
    private static LocalPlayer lastPlayer = null;
    private static boolean wasInWorld = false;
    private static boolean configLoaded = false;
    private static boolean sendFailed = false;
    private static UUID incomingCallId = null;
    private static String incomingCaller = "";
    private static final Map<String, Integer> unreadByNumber = new HashMap<>();
    private static int unreadRefresh = 0;

    /** Going from one phone page to the next closes one screen and opens the other: for a tick or two no
     * phone screen is open, which made the held phone flash its lock screen. The last page is kept that long. */
    private static final int PAGE_CHANGE_GRACE_TICKS = 10;
    private static int ticksWithoutApp = 0;
    /** The lock screen only lights up for a moment, like a real phone: when the phone is taken in hand, put
     * away (its UI closed) or a new message arrives. The rest of the time the held phone's screen is off. */
    private static final int WAKE_TICKS = 80;
    private static int wakeTicks = 0;
    /** A page change must last this long before the held phone shows it (3 ticks = 0.15 s). */
    private static final int PAGE_STABLE_TICKS = 3;
    private static PhoneScreenApp pendingApp = PhoneScreenApp.NONE;
    private static int pendingTicks = 0;
    private static boolean wasHoldingPhone = false;
    private static int lastUnreadTotal = -1;

    private LiveScreenClient() {
    }

    //? if neoforge || legacyforge {
    @SubscribeEvent
    //? if >=1.20.5 {
    /*public static void onClientTick(ClientTickEvent.Post event) {
        tick();
    }
    *///? } else {
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        tick();
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*// Called from CrazyphoneFabricClient#onInitializeClient.
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }
    *///?}

    private static void tick() {
        fr.lordfinn.crazyphone.client.BenchRunner.tick();
        try {
            if (!configLoaded) {
                configLoaded = true;
                loadConfig();
            }
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player == null || mc.level == null) {
                if (wasInWorld)
                    resetSession();
                return;
            }
            wasInWorld = true;
            if (player != lastPlayer) {
                // Respawn and dimension changes create a new LocalPlayer: report again from scratch.
                lastPlayer = player;
                lastSent = null;
            }
            PhoneScreenApp computed = computeLocalApp(mc);
            if (computed != PhoneScreenApp.NONE) {
                ticksWithoutApp = 0;
                if (computed == localApp || localApp == PhoneScreenApp.NONE) {
                    // Same page, or the phone just opened: shown right away.
                    localApp = computed;
                    pendingApp = PhoneScreenApp.NONE;
                } else if (computed == pendingApp) {
                    // Another page: shown once it has stayed up a few ticks, so a page that only flashes by
                    // between two others (the home screen, typically) never shows on the held phone.
                    if (++pendingTicks >= PAGE_STABLE_TICKS) {
                        localApp = computed;
                        pendingApp = PhoneScreenApp.NONE;
                    }
                } else {
                    pendingApp = computed;
                    pendingTicks = 1;
                }
            } else if (localApp != PhoneScreenApp.NONE && ++ticksWithoutApp > PAGE_CHANGE_GRACE_TICKS) {
                // Really closed (not just between two pages): the lock screen shows for a moment, then off.
                localApp = PhoneScreenApp.NONE;
                wake();
            }
            boolean holding = isPhone(player.getMainHandItem()) || isPhone(player.getOffhandItem());
            if (holding && !wasHoldingPhone)
                wake();
            wasHoldingPhone = holding;
            if (wakeTicks > 0)
                wakeTicks--;
            if (++unreadRefresh >= UNREAD_REFRESH_TICKS) {
                unreadRefresh = 0;
                refreshUnread(player);
                int total = 0;
                for (int count : unreadByNumber.values())
                    total += count;
                if (lastUnreadTotal >= 0 && total > lastUnreadTotal)
                    wake();
                lastUnreadTotal = total;
            }
            ticksSinceSend++;
            if (localApp != lastSent && ticksSinceSend >= MIN_TICKS_BETWEEN_REPORTS && canReport())
                report(localApp);
        } catch (RuntimeException e) {
            LOGGER.error("CrazyPhone: live phone screen client tick failed", e);
        }
    }

    private static boolean canReport() {
        if (sendFailed || !ClientFeatureFlagState.isExplicitlyEnabled(FeatureFlag.LIVE_SCREEN))
            return false;
        //? if fabric && >=1.20.5 {
        /*return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(PhoneScreenAppSyncPacket.TYPE);
        *///?} else {
        return true;
        //?}
    }

    private static void report(PhoneScreenApp app) {
        try {
            NetworkAccess.sendToServer(new PhoneScreenAppSyncPacket(app.id));
            lastSent = app;
            ticksSinceSend = 0;
        } catch (RuntimeException e) {
            // A server that negotiated the flag but can't take the payload: stop trying for this session.
            sendFailed = true;
            LOGGER.warn("CrazyPhone: could not send the phone screen state, sharing disabled for this session", e);
        }
    }

    private static void resetSession() {
        wasInWorld = false;
        ticksWithoutApp = 0;
        wakeTicks = 0;
        wasHoldingPhone = false;
        lastUnreadTotal = -1;
        lastPlayer = null;
        lastSent = null;
        localApp = PhoneScreenApp.NONE;
        pendingApp = PhoneScreenApp.NONE;
        pendingTicks = 0;
        sendFailed = false;
        incomingCallId = null;
        incomingCaller = "";
        unreadByNumber.clear();
        ClientFeatureFlagState.clear();
        LiveScreenMesh.clearCache();
        LiveScreenRenderer.resetFailures();
    }

    /** The phone app on the local player's screen right now (NONE when no phone UI is up). */
    public static PhoneScreenApp localApp() {
        return localApp;
    }

    /** Lights the local player's lock screen up for a few seconds. */
    static void wake() {
        wakeTicks = WAKE_TICKS;
    }

    /** True while the local player's lock screen is lit (see WAKE_TICKS). */
    static boolean isAwake() {
        return wakeTicks > 0;
    }

    private static boolean isPhone(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == ModItems.CRAZY_PHONE.get();
    }

    static PhoneScreenApp computeLocalApp(Minecraft mc) {
        if (CrazyPhoneCaptureMode.isActive())
            return PhoneScreenApp.CAMERA;
        Screen screen = mc./*$ mc_get_screen {*/screen/*$}*/;
        if (screen == null)
            return PhoneScreenApp.NONE;
        // By simple name rather than instanceof: no compile-time link to every screen class (not all of them
        // exist in every loader's source set), and a screen added later just falls back to OTHER.
        return switch (screen.getClass().getSimpleName()) {
            case "CrazyphoneHomeScreenScreen" -> PhoneScreenApp.HOME;
            case "CrazyPhoneContactsScreenScreen" -> PhoneScreenApp.CONTACTS;
            case "CrazyPhoneConversationScreen" -> PhoneScreenApp.CONVERSATION;
            case "CrazyPhoneMyPhotosScreenScreen", "CrazyPhonePhotoViewerScreen", "CrazyPhonePhotoEditScreen" -> PhoneScreenApp.PHOTOS;
            case "CrazyPhoneCallingScreenScreen", "CrazyPhoneInCallScreenScreen", "CrazyPhoneIncomingCallScreenScreen" -> PhoneScreenApp.CALL;
            case "CrazyPhoneSignInScreenScreen", "CrazyPhonePasswordScreenScreen" -> PhoneScreenApp.LOCKED;
            case "CrazyPhoneContactInfoScreenScreen", "CrazyPhoneGroupSettingsScreenScreen" -> PhoneScreenApp.SETTINGS;
            case "CrazyPhoneMayorsCandidatesListScreen", "CrazyPhoneMayorCandidateScreenScreen" -> PhoneScreenApp.ELECTION;
            default -> screen instanceof PhoneScreen ? PhoneScreenApp.OTHER : PhoneScreenApp.NONE;
        };
    }

    // ---- incoming caller (own phone only) --------------------------------------------------------------

    /** Hooked from CrazyPhoneIncomingCallNotificationPacket's client handler. */
    public static void onIncomingCall(UUID callId, String callerName) {
        incomingCallId = callId;
        incomingCaller = sanitizeName(callerName);
    }

    static String incomingCaller(UUID currentCallId) {
        return currentCallId != null && currentCallId.equals(incomingCallId) ? incomingCaller : "";
    }

    /** Only what the 3x5 pixel font can draw, upper case, at most 16 characters. */
    static String sanitizeName(String name) {
        if (name == null)
            return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length() && out.length() < 16; i++) {
            char c = Character.toUpperCase(name.charAt(i));
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.')
                out.append(c);
            else if (c == ' ')
                out.append(' ');
        }
        return out.toString().trim();
    }

    // ---- unread badge (own phone only) -----------------------------------------------------------------

    private static void refreshUnread(Player player) {
        unreadByNumber.clear();
        addUnread(player, player.getMainHandItem());
        addUnread(player, player.getOffhandItem());
    }

    private static void addUnread(Player player, ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() != ModItems.CRAZY_PHONE.get())
            return;
        String number = NbtCompat.getString(PhoneTagAccess.getTag(stack), "number");
        if (number.isEmpty())
            return;
        Tag raw = PhoneRegistrySavedData.get(player.level()).phones.get(number);
        if (!(raw instanceof CompoundTag phone))
            return;
        // Privacy: only a phone registered to the local player shows its badge, even in their own hand.
        if (!player.getStringUUID().equals(NbtCompat.getString(phone, "uuid")))
            return;
        unreadByNumber.put(number, NbtCompat.getList(phone, "notifications").size());
    }

    /** -1 when unknown or not the local player's own phone. */
    static int unreadFor(String number) {
        Integer count = unreadByNumber.get(number);
        return count == null ? -1 : count;
    }

    // ---- client options --------------------------------------------------------------------------------

    // Stored in config/crazyphone-client.properties next to the other client options: ClientPrefs is the
    // file's only reader and writer, so saving here never drops the phone size, emoji or video settings.
    static void loadConfig() {
        fr.lordfinn.crazyphone.client.ClientPrefs.ensureLoaded();
    }

    static void saveConfig() {
        fr.lordfinn.crazyphone.client.ClientPrefs.save();
    }
}
