package fr.lordfinn.crazyphone.data;

//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.neoforge.event.server.ServerStartedEvent;
//?}
//? if fabric {
/*import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
*///?}

/**
 * Runs {@link ConversationSavedData#finalizeOrphanedCalls()} once the server is up - see that method's
 * javadoc for why any "call in progress" entry still on disk at this point is stale rather than genuinely
 * ongoing.
 */
//? if neoforge {
@EventBusSubscriber
//?}
public class OrphanedCallCleanup {
    //? if neoforge {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ConversationSavedData.get(event.getServer().overworld()).finalizeOrphanedCalls();
    }
    //?}
    //? if fabric {
    /*public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                ConversationSavedData.get(server.overworld()).finalizeOrphanedCalls());
    }
    *///?}
}
