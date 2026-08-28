package fr.lordfinn.crazyphone.client;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//?}

import fr.lordfinn.crazyphone.item.CrazyPhoneCaptureShortcut;

/**
 * Client-only counterpart to {@link CrazyPhoneCaptureShortcut} (see its javadoc): NeoForge's
 * PlayerInteractEvent.LeftClickEmpty only ever fires client-side (swinging at literally nothing has no
 * server-side signal at all), so this is its own Dist.CLIENT-scoped class rather than a third handler on
 * the common one - safe to call the overlay directly here, unlike the common class.
 */
//? if neoforge {
@EventBusSubscriber(value = Dist.CLIENT)
//?}
public class CrazyPhoneCaptureEmptyClickHandler {
    //? if neoforge {
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (!CrazyPhoneCaptureShortcut.isHoldingPhone(event.getEntity()))
            return;
        CrazyPhoneCaptureShortcut.triggerOnClient(true);
    }
    //?}
}
