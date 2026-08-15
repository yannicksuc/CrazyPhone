package fr.lordfinn.crazyphone.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric's common-side entrypoint (declared in src/main/templates/fabric/fabric.mod.json under
 * entrypoints.main) - the loader-specific equivalent of the NeoForge side's @Mod-annotated Crazyphone.java.
 * Walking-skeleton stage: registries/network/data wiring gets added here incrementally as each area is
 * ported from the NeoForge implementation.
 */
public class CrazyphoneFabric implements ModInitializer {
    public static final String MODID = "crazyphone";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        LOGGER.info("CrazyPhone (Fabric) initializing");
    }
}
