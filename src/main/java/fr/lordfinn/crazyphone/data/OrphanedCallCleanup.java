package fr.lordfinn.crazyphone.data;

import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Runs {@link ConversationSavedData#finalizeOrphanedCalls()} once the server is up - see that method's
 * javadoc for why any "call in progress" entry still on disk at this point is stale rather than genuinely
 * ongoing.
 */
@EventBusSubscriber
public class OrphanedCallCleanup {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ConversationSavedData.get(event.getServer().overworld()).finalizeOrphanedCalls();
    }
}
