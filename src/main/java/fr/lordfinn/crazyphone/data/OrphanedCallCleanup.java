package fr.lordfinn.crazyphone.data;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
