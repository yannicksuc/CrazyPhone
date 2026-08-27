package fr.lordfinn.crazyphone.fabric;

/**
 * Fabric's client-side entrypoint (declared in src/main/templates/fabric/fabric.mod.json under
 * entrypoints.client) - the loader-specific equivalent of the client-only registration NeoForge does via
 * @EventBusSubscriber(Dist.CLIENT)/mod-bus listeners. Walking-skeleton stage: screen/menu/renderer
 * registration gets added here incrementally as each area is ported from the NeoForge implementation.
 * Compiles to nothing on NeoForge nodes - see CrazyphoneFabric.java for why. Two flat, independent
 * >=1.20.5/<1.20.5 blocks for the same reason as CrazyphoneFabric.java.
 */
//? if fabric && >=1.20.5 {
/*import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import fr.lordfinn.crazyphone.init.ModScreens;
import fr.lordfinn.crazyphone.item.CrazyPhoneItemProperties;
import fr.lordfinn.crazyphone.client.PhoneClickableCursorHandler;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneItemInInventoryTickProcedure;
import fr.lordfinn.crazyphone.client.CallRingtoneManager;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCapture;
import fr.lordfinn.crazyphone.item.CrazyPhonePhotoItem;

public class CrazyphoneFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModPackets.registerClient();
        ModScreens.register();
        CrazyPhoneItemProperties.register();
        PhoneClickableCursorHandler.register();
        CrazyPhoneItemInInventoryTickProcedure.register();
        CallRingtoneManager.register();
        CrazyPhonePhotoItem.registerFabricRenderer();
        CrazyPhonePhotoItem.clientViewerOpener = photoId ->
                net.minecraft.client.Minecraft.getInstance().setScreen(new fr.lordfinn.crazyphone.client.gui.CrazyPhonePhotoViewerScreen(photoId));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FabricPictureCapture.onClientTick();
            if (client.screen instanceof fr.lordfinn.crazyphone.client.gui.CrazyPhoneCaptureOverlayScreen overlay)
                overlay.onClientTick();
        });
        CrazyphoneFabric.LOGGER.info("CrazyPhone (Fabric) client initializing");
    }
}
*///?}
//? if fabric && <1.20.5 {
/*import net.fabricmc.api.ClientModInitializer;

// CrazyPhoneItemProperties/CrazyPhoneItemInInventoryTickProcedure aren't wired here: both reach
// CrazyPhoneCallStateSyncPacket.State, which is >=1.20.5-only (see build.fabric.gradle.kts) - 1.20.1-fabric
// stays at the narrower walking-skeleton scope until its own pre-1.20.5 networking pass (task #161 follow-up).
public class CrazyphoneFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CrazyphoneFabric.LOGGER.info("CrazyPhone (Fabric) client initializing");
    }
}
*///?}
