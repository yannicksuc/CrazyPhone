package fr.lordfinn.crazyphone.item;

//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//?}

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import fr.lordfinn.crazyphone.init.ModItems;

/**
 * Punch-to-shoot: attacking (left-click, whether a block, an entity, or empty air) while holding the phone
 * opens the same full-screen capture overlay the conversation camera icon and the home screen's Photo icon
 * use, instead of performing the normal attack/block-break action - a quick shortcut into framing a shot,
 * not a separate capture mechanism of its own (unlike Camera mod's own two-step arm-then-shoot flow, this
 * needs no state on the phone item: one punch, one overlay).
 *
 * These NeoForge events are registered unconditionally (both sides) because their side-firing behavior is
 * genuinely mixed depending on the exact interaction and NeoForge version, matching the same registration
 * shape this codebase's own Camera-mod-era equivalent used - see git history at
 * {@code fr.lordfinn.crazyphone.item.CrazyPhoneLeftClickInterceptor} (commit a60c197's parent) for the
 * proven-in-production reference. The actual overlay-opening call is client-only: since this class is
 * common-loaded (registered unconditionally, not Dist.CLIENT-scoped), it must never reference
 * net.minecraft.client.* types directly in its own bytecode - see {@link CrazyPhonePhotoItem}'s own doc
 * comment for why. {@link #clientOpenOverlay} is set once from each loader's own client-only entrypoint, the
 * same indirection pattern used there.
 */
//? if neoforge {
@EventBusSubscriber
//?}
public class CrazyPhoneCaptureShortcut {
    public static Runnable clientOpenOverlay = null;

    //? if neoforge {
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!isHoldingPhone(event.getEntity()))
            return;
        event.setCanceled(true);
        triggerOnClient(event.getLevel().isClientSide());
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!isHoldingPhone(event.getEntity()))
            return;
        event.setCanceled(true);
        triggerOnClient(event.getEntity().level().isClientSide());
    }
    //?}

    public static void triggerOnClient(boolean isClientSide) {
        if (isClientSide && clientOpenOverlay != null)
            clientOpenOverlay.run();
    }

    public static boolean isHoldingPhone(Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == ModItems.CRAZY_PHONE.get();
    }
}
