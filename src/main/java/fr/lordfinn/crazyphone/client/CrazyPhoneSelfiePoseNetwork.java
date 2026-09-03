package fr.lordfinn.crazyphone.client;

/**
 * Reads an OTHER (non-local) entity's selfie pose off their own held phone stack - see
 * {@link CrazyPhoneSelfiePose}'s own doc comment for why this exists (make selfie framing visible to
 * bystanders) and {@link fr.lordfinn.crazyphone.utils.CrazyPhoneHelper#setPhoneSelfiePose} for where the
 * data actually comes from (written server-side, propagated by vanilla's own equipment sync). Kept separate
 * from CrazyPhoneSelfiePose itself just to keep "read state off an arbitrary entity's inventory" isolated
 * from the pure pose-math methods.
 */
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

public final class CrazyPhoneSelfiePoseNetwork {
    private CrazyPhoneSelfiePoseNetwork() {
    }

    private static ItemStack heldPhone(LivingEntity entity) {
        if (!(entity instanceof Player player))
            return ItemStack.EMPTY;
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        return held.getItem() == ModItems.CRAZY_PHONE.get() ? held : ItemStack.EMPTY;
    }

    public static boolean isSelfieActive(LivingEntity entity) {
        ItemStack phone = heldPhone(entity);
        return !phone.isEmpty() && CrazyPhoneHelper.isPhoneSelfieActive(phone);
    }

    public static float stickX(LivingEntity entity) {
        return CrazyPhoneHelper.getPhoneSelfieStickX(heldPhone(entity));
    }

    public static float stickY(LivingEntity entity) {
        return CrazyPhoneHelper.getPhoneSelfieStickY(heldPhone(entity));
    }
}
