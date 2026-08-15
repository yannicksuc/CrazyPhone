package fr.lordfinn.crazyphone.fabric;

/**
 * Fabric's common-side entrypoint (declared in src/main/templates/fabric/fabric.mod.json under
 * entrypoints.main) - the loader-specific equivalent of the NeoForge side's @Mod-annotated Crazyphone.java.
 * Registration order here matters the same way NeoForge's DeferredRegister event-listener order does -
 * items before anything that references them (tabs, menus, ...). This whole file compiles to nothing on
 * NeoForge nodes (Stonecutter still runs its preprocessor over every file regardless of which loader's
 * sourceSet ends up including it) since net.fabricmc.api isn't on that classpath at all.
 *
 * Two flat, independent blocks below (>=1.20.5 vs <1.20.5) rather than nesting a version check inside the
 * fabric one - see NetworkAccess.java's doc comment for why nesting a version-only //? if inside a
 * loader-only one corrupts Stonecutter's output. ModPackets (network registration) doesn't exist yet for
 * <1.20.5 (1.20.1-fabric's own networking pass is still a follow-up), hence the split.
 */
//? if fabric && >=1.20.5 {
/*import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.init.ModSounds;
import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrazyphoneFabric implements ModInitializer {
    public static final String MODID = "crazyphone";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ModItems.register();
        ModSounds.register();
        ModMenus.register();
        ModPackets.registerCommon();
        ModPackets.registerServer();
        PhoneAttachmentTypes.register();
        LOGGER.info("CrazyPhone (Fabric) initializing");
    }
}
*///?}
//? if fabric && <1.20.5 {
/*import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.init.ModSounds;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CrazyphoneFabric implements ModInitializer {
    public static final String MODID = "crazyphone";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitialize() {
        ModItems.register();
        ModSounds.register();
        LOGGER.info("CrazyPhone (Fabric) initializing");
    }
}
*///?}
