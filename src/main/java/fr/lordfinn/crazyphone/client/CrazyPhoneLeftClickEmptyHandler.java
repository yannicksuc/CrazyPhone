package fr.lordfinn.crazyphone.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
import net.neoforged.fml.common.EventBusSubscriber;
//? } else {
/*import net.neoforged.fml.common.Mod.EventBusSubscriber;
*///?}
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.InteractionHand;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneTakePhotoRequestPacket;

/**
 * Client-only counterpart to CrazyPhoneLeftClickInterceptor (see its javadoc): PlayerInteractEvent.LeftClickEmpty
 * only ever fires client-side and can't be cancelled - NeoForge's own docs say the server has no idea this
 * happened unless told, so this just forwards it as a request packet, which re-checks the held item
 * server-side before doing anything.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class CrazyPhoneLeftClickEmptyHandler {
    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (event.getEntity().getItemInHand(InteractionHand.MAIN_HAND).getItem() != ModItems.CRAZY_PHONE.get())
            return;
        //? if >=1.20.5 {
        PacketDistributor.sendToServer(new CrazyPhoneTakePhotoRequestPacket());
        //? } else {
        /*PacketDistributor.SERVER.noArg().send(new CrazyPhoneTakePhotoRequestPacket());
        *///?}
    }
}
