package fr.lordfinn.crazyphone.voicechat;

import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.init.ModItems;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Ends a player's call when they drop the phone or move it into a different inventory - but NOT when it's
 * just being carried on the cursor or reorganized within their own inventory (see the periodic sweep below,
 * which checks {@link net.minecraft.world.inventory.AbstractContainerMenu#getCarried()} separately from
 * {@link Inventory} for exactly that reason). Also runs the 5-second alone-in-call auto-kick. There is no
 * general mechanism elsewhere in this codebase for tracking a specific ItemStack instance across inventory
 * moves, so calls are bound to the PLAYER instead: "do they still possess a phone anywhere" is re-checked
 * periodically rather than trying to hook every possible container-click path individually.
 */
@EventBusSubscriber
public class CallTerminationListener {
    private static final int SWEEP_INTERVAL_TICKS = 20;
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");

    @SubscribeEvent
    public static void onItemDropped(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() != ModItems.CRAZY_PHONE.get())
            return;
        if (event.getPlayer() instanceof ServerPlayer serverPlayer) {
            try {
                CallRegistry.leave(serverPlayer);
            } catch (Exception e) {
                LOGGER.error("Failed to end call for {} after dropping their phone", serverPlayer.getGameProfile().getName(), e);
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0)
            return;
        sweepInventoryPossession(server);
        sweepAloneParticipants(server);
        sweepRingTimeouts(server);
    }

    private static void sweepInventoryPossession(MinecraftServer server) {
        for (UUID playerId : CallRegistry.getAllPlayersInCalls()) {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null || player.hasDisconnected()) {
                    if (player != null)
                        CallRegistry.leave(player);
                    continue;
                }
                if (!stillHasPhone(player))
                    CallRegistry.leave(player);
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
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("minecraft:entity.villager.no"));
        if (sound != null)
            player.playNotifySound(sound, SoundSource.PLAYERS, 0.8f, 0.8f);
    }
}
