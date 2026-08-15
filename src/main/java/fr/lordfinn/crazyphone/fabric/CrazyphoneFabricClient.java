package fr.lordfinn.crazyphone.fabric;

/**
 * Fabric's client-side entrypoint (declared in src/main/templates/fabric/fabric.mod.json under
 * entrypoints.client) - the loader-specific equivalent of the client-only registration NeoForge does via
 * @EventBusSubscriber(Dist.CLIENT)/mod-bus listeners. Walking-skeleton stage: screen/menu/renderer
 * registration gets added here incrementally as each area is ported from the NeoForge implementation.
 * Compiles to nothing on NeoForge nodes - see CrazyphoneFabric.java for why.
 */
//? if fabric {
/*import net.fabricmc.api.ClientModInitializer;

public class CrazyphoneFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CrazyphoneFabric.LOGGER.info("CrazyPhone (Fabric) client initializing");
    }
}
*///?}
