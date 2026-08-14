package fr.lordfinn.crazyphone.mixin;

import de.maxhenkel.camera.net.MessageDisableCameraMode;
import fr.lordfinn.crazyphone.utils.CameraModAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
*///? } else {
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
//?}
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

@Mixin(MessageDisableCameraMode.class)
public class CameraModMessageDisableCameraModeMixin {

    //? if >=1.20.5 {
    /*@Inject(method = "executeServerSide", at = @At("HEAD"), cancellable = true)
    private void injectExecuteServerSide(IPayloadContext context, CallbackInfo ci) {
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = sender.getItemInHand(hand);
            if (CameraModHelper.isSupportedCamera(stack)) {
                CameraModAccess.cameraItem().setActive(stack, false);
            }
        }
        // Cancel original method to prevent duplicate execution
        ci.cancel();
    }
    *///? } else {
    @Inject(method = "executeServerSide", at = @At("HEAD"), cancellable = true)
    private void injectExecuteServerSide(PlayPayloadContext context, CallbackInfo ci) {
        if (!(context.player().orElse(null) instanceof ServerPlayer sender)) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = sender.getItemInHand(hand);
            if (CameraModHelper.isSupportedCamera(stack)) {
                CameraModAccess.cameraItem().setActive(stack, false);
            }
        }
        ci.cancel();
    }
    //?}
}
