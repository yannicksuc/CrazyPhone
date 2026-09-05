package fr.lordfinn.crazyphone.data;

//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
//?}
//? if fabric {
/*import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
*///?}

import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

/**
 * Auto-locks every opted-in, still-unlocked CrazyPhone in a player's inventory the moment they disconnect -
 * see {@link CrazyPhoneHelper#applyAutoLockOnDisconnect} for the actual per-phone check (the "autoLock" NBT
 * preference is opt-in and defaults to off, so a phone that never turned it on keeps its current
 * manual-only locking behavior unchanged).
 *
 * Registered the same way as OrphanedCallCleanup/CrazyPhonePictureCacheReset elsewhere in this mod: an
 * {@code @EventBusSubscriber} class picked up automatically on NeoForge, a plain {@link #register()} called
 * once from CrazyphoneFabric#onInitialize on Fabric. Both loaders expose a "player is about to disconnect,
 * still fully valid (inventory intact, not yet removed from the world)" hook - NeoForge's
 * {@code PlayerEvent.PlayerLoggedOutEvent}, Fabric's {@code ServerPlayConnectionEvents.DISCONNECT}.
 */
//? if neoforge {
@EventBusSubscriber
//?}
public class AutoLockOnDisconnect {
    //? if neoforge {
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            CrazyPhoneHelper.applyAutoLockOnDisconnect(player);
    }
    //?}
    //? if fabric {
    /*public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CrazyPhoneHelper.applyAutoLockOnDisconnect(handler.getPlayer()));
    }
    *///?}
}
