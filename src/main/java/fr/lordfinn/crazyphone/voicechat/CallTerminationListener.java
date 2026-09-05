package fr.lordfinn.crazyphone.voicechat;

import fr.lordfinn.crazyphone.Crazyphone;

//? if neoforge {
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
//? if >=1.20.5 {
/*import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if fabric {
/*import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
*///?}

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ends a player's call when they've gone without the phone (dropped, or moved into a different inventory)
 * for longer than {@code Config.phoneDropGraceSeconds} - but NOT when it's just being carried on the cursor
 * or reorganized within their own inventory (see {@link #stillHasPhone}, which checks {@link
 * net.minecraft.world.inventory.AbstractContainerMenu#getCarried()} separately from {@link Inventory} for
 * exactly that reason), and not at all if they pick it back up within the grace window - a quick drop/pickup
 * (bumping a hotbar key, a stray click) shouldn't interrupt an otherwise-fine call. Also runs the 5-second
 * alone-in-call auto-kick. There is no general mechanism elsewhere in this codebase for tracking a specific
 * ItemStack instance across inventory moves, so calls are bound to the PLAYER instead: "do they still
 * possess a phone anywhere" is re-checked periodically rather than trying to hook every possible
 * container-click path individually.
 *
 * {@link #onItemDropped} (a precise timestamp seed for the instant a phone is actually tossed) is NeoForge-
 * only - Fabric has no built-in equivalent of NeoForge's {@code ItemTossEvent} to hook here. On Fabric the
 * periodic sweep alone covers it: {@link #sweepInventoryPossession}'s own {@code computeIfAbsent} already
 * seeds the same map from the sweep's own current tick the first time it notices the phone gone, so the only
 * difference is up to one sweep interval (1s) of extra precision on the grace-period start, not a correctness
 * gap.
 */
//? if neoforge {
@EventBusSubscriber
//?}
public class CallTerminationListener {
    private static final int SWEEP_INTERVAL_TICKS = 20;
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    /** Game time each currently-phoneless in-call player was first noticed without their phone - absent
     * entirely while they still have one. Seeded by {@link #onItemDropped} for a precise start time (rather
     * than waiting for the next periodic sweep tick) on NeoForge, read and ultimately acted on by {@link
     * #sweepInventoryPossession} on every loader, which is also what clears an entry the moment the phone
     * comes back. */
    private static final Map<UUID, Long> phonelessSinceGameTime = new HashMap<>();

    //? if neoforge {
    /** Just seeds {@link #phonelessSinceGameTime} with a precise timestamp - does NOT end the call itself
     * (see the class javadoc for why: the periodic sweep owns that decision, after the grace period). */
    @SubscribeEvent
    public static void onItemDropped(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() != ModItems.CRAZY_PHONE.get())
            return;
        if (event.getPlayer() instanceof ServerPlayer serverPlayer && CallRegistry.getSessionFor(serverPlayer.getUUID()).isPresent()) {
            MinecraftServer server = serverPlayer.level().getServer();
            if (server != null)
                phonelessSinceGameTime.putIfAbsent(serverPlayer.getUUID(), server.overworld().getGameTime());
        }
    }
    //?}

    //? if neoforge {
    //? if >=1.20.5 {
    /*@SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }
    *///? } else {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick(event.getServer());
    }
    //?}
    //?}
    //? if fabric {
    /*// Called from CrazyphoneFabric#onInitialize.
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CallTerminationListener::tick);
    }
    *///?}

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0)
            return;
        sweepInventoryPossession(server);
        sweepAloneParticipants(server);
        sweepRingTimeouts(server);
    }

    private static void sweepInventoryPossession(MinecraftServer server) {
        long currentGameTime = server.overworld().getGameTime();
        for (UUID playerId : CallRegistry.getAllPlayersInCalls()) {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                // player == null is the NORMAL disconnect case, not a rare corner one - PlayerList#getPlayer
                // stops returning someone the instant they're actually gone, whereas hasDisconnected() only
                // covers the brief mid-disconnect window on the way out. A previous version of this only
                // called CallRegistry.leave() in the hasDisconnected() branch, silently skipping cleanup for
                // anyone who'd already fully logged off - their call session (and its participants/ringing
                // sets) then never emptied out, so it never ended: the "call in progress" chat entry kept
                // ticking forever and the conversation became a phantom nobody could cleanly rejoin.
                if (player == null) {
                    CallRegistry.leave(playerId, server);
                    phonelessSinceGameTime.remove(playerId);
                    continue;
                }
                if (player.hasDisconnected()) {
                    CallRegistry.leave(player);
                    phonelessSinceGameTime.remove(playerId);
                    continue;
                }
                if (stillHasPhone(player)) {
                    // Recovered within the grace window (or never lost it) - no interruption at all, and
                    // critically, no clearCallStateForAllPhones() ever ran on the item while it was out of
                    // their inventory, so its in_call texture flag was never touched and stays correct.
                    phonelessSinceGameTime.remove(playerId);
                    continue;
                }
                long since = phonelessSinceGameTime.computeIfAbsent(playerId, id -> currentGameTime);
                if (currentGameTime - since >= Config.phoneDropGraceSeconds * 20L) {
                    CallRegistry.leave(player);
                    phonelessSinceGameTime.remove(playerId);
                }
            } catch (Exception e) {
                // One player's call-teardown failing (eg. a mid-tick disconnect/packet-send hiccup) must
                // never take down the whole server tick loop - every other player in a call still needs
                // this same sweep to run this tick, and every future tick still needs to run at all.
                LOGGER.error("Failed to sweep call-inventory-possession for player {}", playerId, e);
            }
        }
    }

    private static boolean stillHasPhone(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() == ModItems.CRAZY_PHONE.get())
                return true;
        }
        // Carried-on-cursor stacks live in the open menu, not the player's Inventory - checked separately
        // so vanilla drag/pick-up-to-move (including mid-drag into a chest) doesn't falsely end the call.
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (carried.getItem() == ModItems.CRAZY_PHONE.get())
                return true;
        }
        return false;
    }

    private static void sweepAloneParticipants(MinecraftServer server) {
        long currentGameTime = server.overworld().getGameTime();
        for (CallRegistry.CallSession session : CallRegistry.getActiveSessions()) {
            try {
                // A call that still has people ringing isn't "alone" in the disconnected sense yet - that's
                // a still-unanswered call, handled on its own (longer, distinct) timeout by sweepRingTimeouts
                // below. Applying this kick during ringing used to end nearly every real call before the
                // callee had a chance to answer, since aloneInCallKickSeconds (5s) is far shorter than a
                // realistic answer time.
                if (session.participants.size() != 1 || !session.ringing.isEmpty()) {
                    session.soleParticipantSinceGameTime = -1;
                    continue;
                }
                UUID lastParticipantId = session.participants.iterator().next();
                ServerPlayer lastParticipant = server.getPlayerList().getPlayer(lastParticipantId);
                if (lastParticipant == null)
                    continue;

                if (session.soleParticipantSinceGameTime <= 0) {
                    session.soleParticipantSinceGameTime = currentGameTime;
                    playDisconnectSound(lastParticipant);
                } else if (currentGameTime - session.soleParticipantSinceGameTime >= Config.aloneInCallKickSeconds * 20L) {
                    CallRegistry.leave(lastParticipant);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to sweep alone-in-call state for call {}", session.callId, e);
            }
        }
    }

    /** A call that's been ringing longer than callRingTimeoutSeconds without being answered is a missed
     * call, not an indefinite ring - see CallRegistry#expireRinging for what happens to the still-ringing
     * callees and, if nobody ever answered at all, the call itself. */
    private static void sweepRingTimeouts(MinecraftServer server) {
        long currentGameTime = server.overworld().getGameTime();
        for (CallRegistry.CallSession session : CallRegistry.getActiveSessions()) {
            try {
                if (session.ringing.isEmpty())
                    continue;
                if (currentGameTime - session.startedAtGameTime >= Config.callRingTimeoutSeconds * 20L)
                    CallRegistry.expireRinging(session, server);
            } catch (Exception e) {
                LOGGER.error("Failed to sweep ring-timeout for call {}", session.callId, e);
            }
        }
    }

    private static void playDisconnectSound(ServerPlayer player) {
        SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, Crazyphone.parseId("minecraft:entity.villager.no"));
        if (sound != null)
            CrazyPhoneHelper.playNotifySound(player, sound, SoundSource.PLAYERS, 0.8f, 0.8f);
    }
}
