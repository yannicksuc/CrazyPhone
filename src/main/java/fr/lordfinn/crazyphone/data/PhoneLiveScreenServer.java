package fr.lordfinn.crazyphone.data;

//? if neoforge {
//? if >=1.20.5 {
/*import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if legacyforge {
/*import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
*///?}
//? if fabric {
/*import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
*///?}

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.NbtCompat;
import fr.lordfinn.crazyphone.utils.PhoneLiveScreenData;
import fr.lordfinn.crazyphone.utils.PhoneScreenApp;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the live phone screen: keeps the two privacy-safe tags (PhoneLiveScreenData) on the phones
 * players are HOLDING in line with what each player reported, and with the phone's own call state.
 *
 * Robustness rules, all enforced here rather than trusted from any client:
 * <ul>
 *     <li>Reports ({@link #onReport}) are validated (unknown ids are dropped) and rate limited per player
 *     (a small token bucket). A report back to NONE is always accepted, so a spammy or buggy client can
 *     only ever fail towards "nothing shown", never towards a stuck screen.</li>
 *     <li>The tags are only ever written onto the reporter's own hand items (main hand phone, or the off
 *     hand one if the main hand holds no phone), never another player's stack.</li>
 *     <li>A periodic sweep re-derives every held phone's tags from scratch: a phone passed to someone else,
 *     picked back up from the ground, swapped between hands, or held after a death/respawn/dimension change
 *     ends up with exactly its new holder's state within half a second, whatever happened in between.
 *     Phones that are not in a hand are never rendered live, so their leftover tags are harmless.</li>
 *     <li>With the feature switched off (config/command/permission), every held phone is cleared.</li>
 *     <li>Disconnected players are forgotten on the next sweep (no logout hook needed, so nothing can leak
 *     if a loader event is missed).</li>
 * </ul>
 */
//? if neoforge || legacyforge {
@EventBusSubscriber
//?}
public final class PhoneLiveScreenServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    private static final int SWEEP_INTERVAL_TICKS = 10;
    private static final int FORGET_INTERVAL_TICKS = 200;
    /** Token bucket: up to BURST reports at once, refilled by one token every REFILL_TICKS. */
    private static final int BURST = 8;
    private static final int REFILL_TICKS = 2;

    private static final Map<UUID, Reporter> REPORTERS = new ConcurrentHashMap<>();

    private static final class Reporter {
        volatile PhoneScreenApp app = PhoneScreenApp.NONE;
        int tokens = BURST;
        long lastRefillTick;
        int droppedReports;
    }

    private PhoneLiveScreenServer() {
    }

    /** Called on the server thread for every PhoneScreenAppSyncPacket. */
    public static void onReport(ServerPlayer player, int rawApp) {
        MinecraftServer server = player == null ? null : player.level().getServer();
        if (server == null)
            return;
        PhoneScreenApp app = PhoneScreenApp.byId(rawApp);
        if (app == null)
            return; // malformed or from a newer client: ignore, the current state stays valid
        Reporter reporter = REPORTERS.computeIfAbsent(player.getUUID(), id -> new Reporter());
        long now = server.getTickCount();
        long refill = (now - reporter.lastRefillTick) / REFILL_TICKS;
        if (refill > 0) {
            reporter.tokens = (int) Math.min(BURST, reporter.tokens + refill);
            reporter.lastRefillTick = now;
        }
        if (app != PhoneScreenApp.NONE) {
            if (reporter.tokens <= 0) {
                if (reporter.droppedReports++ == 0)
                    LOGGER.warn("CrazyPhone: {} sends phone screen updates too fast, ignoring the excess", player.getName().getString());
                return;
            }
            reporter.tokens--;
        }
        reporter.app = app;
        applyTo(player);
    }

    //? if neoforge || legacyforge {
    //? if >=1.20.5 {
    /*@SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }
    *///? } else {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        tick(event.getServer());
    }
    //?}
    //?}
    //? if fabric {
    /*// Called from CrazyphoneFabric#onInitialize.
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PhoneLiveScreenServer::tick);
    }
    *///?}

    private static void tick(MinecraftServer server) {
        if (server == null)
            return;
        int tickCount = server.getTickCount();
        if (tickCount % SWEEP_INTERVAL_TICKS != 0)
            return;
        try {
            for (ServerPlayer player : server.getPlayerList().getPlayers())
                applyTo(player);
            if (tickCount % FORGET_INTERVAL_TICKS == 0)
                REPORTERS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        } catch (RuntimeException e) {
            // Never let a cosmetic feature take the server tick down.
            LOGGER.error("CrazyPhone: live phone screen sweep failed", e);
        }
    }

    /** Re-derives the tags of both of this player's hand phones from their reported app and call state. */
    private static void applyTo(ServerPlayer player) {
        boolean sharing = FeatureFlag.LIVE_SCREEN.isEnabledFor(player);
        Reporter reporter = REPORTERS.get(player.getUUID());
        PhoneScreenApp reported = sharing && reporter != null && player.isAlive() ? reporter.app : PhoneScreenApp.NONE;
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean mainIsPhone = isPhone(main);
        boolean changed = false;
        if (mainIsPhone)
            changed |= sync(player, main, reported, sharing);
        if (isPhone(off))
            changed |= sync(player, off, mainIsPhone ? PhoneScreenApp.NONE : reported, sharing);
        // A custom phone menu never wraps the player inventory, so vanilla's per-tick slot broadcast stops
        // looking at the hotbar while one is open (see CrazyPhoneHelper#setCallStateForMatchingPhones).
        if (changed)
            player.inventoryMenu.broadcastChanges();
    }

    private static boolean sync(ServerPlayer player, ItemStack phone, PhoneScreenApp app, boolean sharing) {
        boolean changed = PhoneLiveScreenData.writeApp(phone, app);
        CompoundTag tag = PhoneTagAccess.getTag(phone);
        boolean active = sharing && "ACTIVE".equals(NbtCompat.getString(tag, "callState"));
        long since = PhoneLiveScreenData.readCallSince(tag);
        long now = player.level().getGameTime();
        if (active) {
            // A timestamp from the future (another world, a rolled back save) is as good as none.
            if (since < 0 || since > now)
                changed |= PhoneLiveScreenData.writeCallSince(phone, now);
        } else if (since >= 0) {
            changed |= PhoneLiveScreenData.writeCallSince(phone, -1);
        }
        return changed;
    }

    private static boolean isPhone(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() == ModItems.CRAZY_PHONE.get();
    }
}
