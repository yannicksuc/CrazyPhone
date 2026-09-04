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
            CrazyPhonePhotoItem.clientViewerOpener = (photoId, borderRgb) ->
                    net.minecraft.client.Minecraft.getInstance()./*$ mc_set_screen {*/setScreen/*$}*/(new fr.lordfinn.crazyphone.client.gui.CrazyPhonePhotoViewerScreen(photoId, true, borderRgb));
            CrazyPhoneCaptureShortcut.clientOpenOverlay = () -> CrazyPhoneCaptureMode.enter("");
        });
    }
    //?}
    //? if neoforge && >=26 {
    /*// Registers the custom ItemModel type crazy_phone_photo.json's "type" field references - see
    // CrazyPhonePhotoItemRenderer's own >=26 block for the full explanation/caveats (untested). Scoped to
    // >=26 only, not the full >=1.21.10 range that block's own doc comment discusses as the real gap -
    // 1.21.10 itself has a meaningfully different ItemModel.Unbaked/SpecialModelRenderer API shape
    // (bake()'s parameter list, getExtents()'s callback type) that wasn't worth reconciling in the same
    // pass; 1.21.10 still has no working custom photo rendering at all, unchanged from before this work.
    @SubscribeEvent
    public static void onRegisterItemModels(net.neoforged.neoforge.client.event.RegisterItemModelsEvent event) {
        event.register(fr.lordfinn.crazyphone.Crazyphone.resource("photo_card_model"),
                fr.lordfinn.crazyphone.client.render.CrazyPhonePhotoItemRenderer.ModelImpl.Unbaked.MAP_CODEC);
    }
    *///?}
}
