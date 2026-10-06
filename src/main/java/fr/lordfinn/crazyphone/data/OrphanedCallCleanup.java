package fr.lordfinn.crazyphone.data;

//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
//?}
//? if fabric {
/*import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
*///?}
// Real, original Forge 1.20.1 - same bare (FORGE-bus) @EventBusSubscriber/@SubscribeEvent shape as
// PhoneAttachmentTypes.java's own player-lifecycle handlers; ServerStartedEvent itself lives at the same
// package path old Forge always had it at, which NeoForge's own copy above just carried forward.
//? if legacyforge {
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
//?}

/**
 * Runs two one-time-per-boot fixups once the server is up: {@link ConversationSavedData#finalizeOrphanedCalls()}
 * (see that method's javadoc for why any "call in progress" entry still on disk at this point is stale
 * rather than genuinely ongoing) and {@link LegacyPhotoMigration#migrate} (backfills any conversation photo
 * that predates {@link PhotoSavedData} itself - see that class's own doc comment). Once the server has
 * stopped, it also waits for the last queued photo file writes (see {@link PhotoFileStore#closeAll()}).
 */
//? if neoforge || legacyforge {
@EventBusSubscriber
//?}
public class OrphanedCallCleanup {
    //? if neoforge || legacyforge {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ConversationSavedData.get(event.getServer().overworld()).finalizeOrphanedCalls();
        LegacyPhotoMigration.migrate(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PhotoFileStore.closeAll();
    }
    //?}
    //? if fabric {
    /*public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ConversationSavedData.get(server.overworld()).finalizeOrphanedCalls();
            LegacyPhotoMigration.migrate(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PhotoFileStore.closeAll());
    }
    *///?}
}
