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

    // The synced angles only change as often as the framing player's packets arrive (~20 Hz at best, plus
    // network jitter), which looks stepped at 60+ fps - ease the value shown toward the latest target instead.
    private static final class Smoothed {
        float x, y;
        long lastNanos;
        boolean initialized;
    }

    private static final java.util.Map<java.util.UUID, Smoothed> SMOOTHED = new java.util.concurrent.ConcurrentHashMap<>();
    private static final double SMOOTHING_SECONDS = 0.06;

    private static Smoothed smoothed(LivingEntity entity) {
        Smoothed s = SMOOTHED.computeIfAbsent(entity.getUUID(), id -> new Smoothed());
        float targetX = CrazyPhoneHelper.getPhoneSelfieStickX(heldPhone(entity));
        float targetY = CrazyPhoneHelper.getPhoneSelfieStickY(heldPhone(entity));
        long now = System.nanoTime();
        if (!s.initialized || !isSelfieActive(entity)) {
            s.x = targetX;
            s.y = targetY;
            s.initialized = isSelfieActive(entity);
        } else {
            double dt = (now - s.lastNanos) / 1.0e9;
            double alpha = 1.0 - Math.exp(-Math.max(0.0, dt) / SMOOTHING_SECONDS);
            s.x += (targetX - s.x) * (float) alpha;
            s.y += (targetY - s.y) * (float) alpha;
        }
        s.lastNanos = now;
        return s;
    }

    public static float stickX(LivingEntity entity) {
        return smoothed(entity).x;
    }

    public static float stickY(LivingEntity entity) {
        return smoothed(entity).y;
    }
}
