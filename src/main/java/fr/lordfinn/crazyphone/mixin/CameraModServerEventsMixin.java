package fr.lordfinn.crazyphone.mixin;

import de.maxhenkel.camera.Main;
import de.maxhenkel.camera.ServerEvents;
import de.maxhenkel.camera.items.CameraItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerEvents.class)
public class CameraModServerEventsMixin {

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private static void injectOnTick(PlayerTickEvent.Pre event, CallbackInfo ci) {
        // Replace original check with your flexible one
        if (CameraModHelper.isSupportedCamera(event.getEntity().getMainHandItem()) ||
            CameraModHelper.isSupportedCamera(event.getEntity().getOffhandItem())) {
            ci.cancel(); // Cancel the rest of the method to prevent disabling your custom camera
        }
    }
    @Inject(method = "disableCamera", at = @At("HEAD"), cancellable = true)
    private static void injectDisableCamera(ItemStack stack, CallbackInfo ci) {
        if (CameraModHelper.isSupportedCamera(stack)) {
            Main.CAMERA.get().setActive(stack, false);
        }
        // Cancel original method to prevent duplicate execution
        ci.cancel();
    }

        @Inject(method = "onRightClick", at = @At("HEAD"), cancellable = true)
    private static void injectOnRightClick(PlayerInteractEvent.RightClickBlock event, CallbackInfo ci) {
        Player player = event.getEntity();

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack item = player.getItemInHand(hand);
            if (CameraModHelper.isSupportedCamera(item) &&
                ((CameraItem) Main.CAMERA.get()).isActive(item)) {
                event.setUseBlock(TriState.FALSE);
                event.setCanceled(true);
                ci.cancel();
                break;
            }
        }
    }

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private static void injectOnHit(LivingIncomingDamageEvent event, CallbackInfo ci) {
        Entity source = event.getSource().getDirectEntity();
        if (source instanceof Player player) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (CameraModHelper.isSupportedCamera(stack) &&
                    ((CameraItem) Main.CAMERA.get()).isActive(stack)) {
                    event.setCanceled(true);
                    ci.cancel();
                    break;
                }
            }
        }
    }
}
