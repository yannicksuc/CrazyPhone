package fr.lordfinn.crazyphone.client;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if <1.20.5 {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?} else {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///?}
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
//?}

import fr.lordfinn.crazyphone.item.CrazyPhoneCaptureShortcut;
import fr.lordfinn.crazyphone.item.CrazyPhonePhotoItem;

/**
 * Wires {@link CrazyPhonePhotoItem#clientViewerOpener} and {@link CrazyPhoneCaptureShortcut#clientOpenOverlay}
 * on NeoForge. Deliberately a class of its own, never
 * instantiated from common code (only ever used via NeoForge's own annotation-driven event scanning, exactly
 * like {@code CallRingtoneManager}/{@code CrazyPhoneItemProperties}) - unlike {@code CrazyPhonePhotoItem}
 * itself, which IS instantiated from common code (ModItems' DeferredRegister) and crashed a dedicated server
 * outright the one time this same wiring briefly lived on it directly (NeoForge's own dist transformer
 * inspects every method of a class the moment that class is actually loaded, {@code @EventBusSubscriber}'s
 * {@code Dist.CLIENT} filter only controls event registration, not classloading eligibility for a class
 * something else instantiates regardless of dist).
 */
//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber(value = Dist.CLIENT)
*///?}
//?}
public class CrazyPhonePhotoItemClientBinding {
    //? if neoforge {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            CrazyPhonePhotoItem.clientViewerOpener = photoId ->
                    net.minecraft.client.Minecraft.getInstance().setScreen(new fr.lordfinn.crazyphone.client.gui.CrazyPhonePhotoViewerScreen(photoId, true));
            CrazyPhoneCaptureShortcut.clientOpenOverlay = () -> CrazyPhoneCaptureMode.enter("");
        });
    }
    //?}
}
