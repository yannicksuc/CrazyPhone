package fr.lordfinn.crazyphone.mixin;

import de.maxhenkel.camera.ServerEvents;
import de.maxhenkel.camera.items.CameraItem;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.utils.CameraModAccess;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
//? if <1.21.10 {
import net.neoforged.neoforge.common.util.TriState;
//? } else {
/*import net.minecraft.util.TriState;
*///?}
//? if >=1.20.5 {
/*import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
*///? } else {
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.entity.living.LivingAttackEvent;
//?}
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerEvents.class)
public class CameraModServerEventsMixin {

    //? if >=1.20.5 {
    /*@Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private static void injectOnTick(PlayerTickEvent.Pre event, CallbackInfo ci) {
        // Replace original check with your flexible one
        if (CameraModHelper.isSupportedCamera(event.getEntity().getMainHandItem()) ||
            CameraModHelper.isSupportedCamera(event.getEntity().getOffhandItem())) {
            ci.cancel(); // Cancel the rest of the method to prevent disabling your custom camera
        }
    }
    *///? } else {
    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private static void injectOnTick(TickEvent.PlayerTickEvent event, CallbackInfo ci) {
        // Camera mod's own onTick doesn't filter by phase at all - NeoForge fires PlayerTickEvent for both
        // START and END, so it runs its "is this still a real camera item" deactivation check twice per
        // tick. Skipping the cancellation on one of those phases (as this used to do) let the other phase's
        // call through unguarded, deactivating the camera almost immediately after it was ever set active.
        if (CameraModHelper.isSupportedCamera(event.player.getMainHandItem()) ||
            CameraModHelper.isSupportedCamera(event.player.getOffhandItem())) {
            ci.cancel();
        }
    }
    //?}
    @Inject(method = "disableCamera", at = @At("HEAD"), cancellable = true)
    private static void injectDisableCamera(ItemStack stack, CallbackInfo ci) {
        if (CameraModHelper.isSupportedCamera(stack)) {
            CameraModAccess.cameraItem().setActive(stack, false);
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
                ((CameraItem) CameraModAccess.cameraItem()).isActive(item)) {
                //? if >=1.20.5 {
                /*event.setUseBlock(TriState.FALSE);
                *///? } else {
                event.setUseBlock(Event.Result.DENY);
                //?}
                event.setCanceled(true);
                ci.cancel();
                break;
            }
        }
    }

    //? if >=1.20.5 {
    /*@Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private static void injectOnHit(LivingIncomingDamageEvent event, CallbackInfo ci) {
        Entity source = event.getSource().getDirectEntity();
        if (source instanceof Player player) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (CameraModHelper.isSupportedCamera(stack) &&
                    ((CameraItem) CameraModAccess.cameraItem()).isActive(stack)) {
                    event.setCanceled(true);
                    ci.cancel();
                    break;
                }
            }
        }
    }
    *///? } else {
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private static void injectOnHit(LivingAttackEvent event, CallbackInfo ci) {
        Entity source = event.getSource().getDirectEntity();
        if (source instanceof Player player) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (CameraModHelper.isSupportedCamera(stack) &&
                    ((CameraItem) CameraModAccess.cameraItem()).isActive(stack)) {
                    event.setCanceled(true);
                    ci.cancel();
                    break;
                }
            }
        }
    }
    //?}
}
