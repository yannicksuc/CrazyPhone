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
import fr.lordfinn.crazyphone.item.CrazyPhoneCaptureShortcut;
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
                net.minecraft.client.Minecraft.getInstance()./^$ mc_set_screen {^/setScreen/^$}^/(new fr.lordfinn.crazyphone.client.gui.CrazyPhonePhotoViewerScreen(photoId, true));
        ClientTickEvents.END_CLIENT_TICK.register(client -> FabricPictureCapture.tickAll());
        // FabricPictureCache's maps are static and otherwise survive a disconnect - a request still
        // IN_FLIGHT the moment the connection drops never gets its response, permanently blocking that one
        // photo from ever loading again even after reconnecting (see that class's own reset() doc comment).
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
                (handler, sender, client) -> fr.lordfinn.crazyphone.client.picture.FabricPictureCache.reset());
        fr.lordfinn.crazyphone.client.CrazyPhonePresentDebugCommand.register();
        // Punch-to-shoot: fires every tick the attack key is held, clickCount != 0 only on the actual
        // click-down tick (see ClientPreAttackCallback's own doc comment) - returning true cancels the
        // vanilla attack/block-break/hand-swing entirely, covering block/entity/empty-air uniformly (unlike
        // NeoForge, which needs three separate events for the same three cases - see CrazyPhoneCaptureShortcut).
        net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (clickCount == 0 || !CrazyPhoneCaptureShortcut.isHoldingPhone(player))
                return false;
            fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode.enter("");
            return true;
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
