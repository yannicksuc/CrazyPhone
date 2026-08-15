package fr.lordfinn.crazyphone.fabric;

/**
 * Fabric's common-side entrypoint (declared in src/main/templates/fabric/fabric.mod.json under
 * entrypoints.main) - the loader-specific equivalent of the NeoForge side's @Mod-annotated Crazyphone.java.
 * Registration order here matters the same way NeoForge's DeferredRegister event-listener order does -
 * items before anything that references them (tabs, menus, ...). This whole file compiles to nothing on
 * NeoForge nodes (Stonecutter still runs its preprocessor over every file regardless of which loader's
 * sourceSet ends up including it) since net.fabricmc.api isn't on that classpath at all.
 */
//? if fabric {
/*import fr.lordfinn.crazyphone.init.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrazyphoneFabric implements ModInitializer {
    public static final String MODID = "crazyphone";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ModItems.register();
        LOGGER.info("CrazyPhone (Fabric) initializing");
    }
}
*///?}
