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

    @SubscribeEvent
    public static void onItemDropped(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() != ModItems.CRAZY_PHONE.get())
            return;
        if (event.getPlayer() instanceof ServerPlayer serverPlayer)
            CallRegistry.leave(serverPlayer);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SWEEP_INTERVAL_TICKS != 0)
            return;
        sweepInventoryPossession(server);
        sweepAloneParticipants(server);
    }

    private static void sweepInventoryPossession(MinecraftServer server) {
        for (UUID playerId : CallRegistry.getAllPlayersInCalls()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || player.hasDisconnected()) {
                if (player != null)
                    CallRegistry.leave(player);
                continue;
            }
            if (!stillHasPhone(player))
                CallRegistry.leave(player);
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
            if (session.participants.size() != 1) {
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
        }
    }

    private static void playDisconnectSound(ServerPlayer player) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("minecraft:entity.villager.no"));
        if (sound != null)
            player.playNotifySound(sound, SoundSource.PLAYERS, 0.8f, 0.8f);
    }
}
