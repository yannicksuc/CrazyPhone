package fr.lordfinn.crazyphone.item;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneTakePhotoProcedure;

/**
 * Left-clicking (attacking/punching) while holding the CrazyPhone opens the camera instead of breaking a
 * block or hitting an entity - mirrors the Home screen's Photo button (see
 * CrazyphoneHomeScreenButtonMessage's buttonID 0 handler, which this calls the exact same procedure as).
 * Covers the two cases NeoForge fires server-side, cancelling the vanilla action and taking the photo in
 * the same handler; the third case (left-clicking empty space) is client-only per NeoForge's own design -
 * see CrazyPhoneTakePhotoRequestPacket for how that one reaches the server instead.
 */
@EventBusSubscriber
public class CrazyPhoneLeftClickInterceptor {
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!isHoldingPhone(event.getEntity()))
            return;
        event.setCanceled(true);
        if (!event.getLevel().isClientSide())
            CrazyPhoneTakePhotoProcedure.execute(event.getLevel(), event.getEntity());
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!isHoldingPhone(event.getEntity()))
            return;
        event.setCanceled(true);
        if (!event.getEntity().level().isClientSide())
            CrazyPhoneTakePhotoProcedure.execute(event.getEntity().level(), event.getEntity());
    }

    private static boolean isHoldingPhone(Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == ModItems.CRAZY_PHONE.get();
    }
}
