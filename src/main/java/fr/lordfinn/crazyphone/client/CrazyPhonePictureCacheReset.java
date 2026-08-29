package fr.lordfinn.crazyphone.client;

/**
 * Clears FabricPictureCache's static maps on every (re)connect to a server - see that class's own
 * {@code reset()} doc comment for why a stale entry otherwise survives a disconnect forever and permanently
 * blocks that one photo from ever loading again. A dedicated class of its own for the same reason
 * CrazyPhonePhotoItemClientBinding is: NeoForge's {@code @EventBusSubscriber} pins a class to one specific
 * event bus (MOD for the client-setup event that class handles pre-1.20.5), and mixing a GAME-bus event
 * (ClientPlayerNetworkEvent.LoggingIn, fired here) into that same class would silently fail to register
 * there instead of throwing.
 */
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if <1.20.5 {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?} else {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///?}
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@EventBusSubscriber(value = Dist.CLIENT)
public final class CrazyPhonePictureCacheReset {
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        fr.lordfinn.crazyphone.client.picture.FabricPictureCache.reset();
    }
}
//?}
