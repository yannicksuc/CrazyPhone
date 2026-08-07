package fr.lordfinn.crazyphone.mixin;

import de.maxhenkel.camera.net.MessageDisableCameraMode;
import de.maxhenkel.camera.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

@Mixin(MessageDisableCameraMode.class)
public class CameraModMessageDisableCameraModeMixin {

    @Inject(method = "executeServerSide", at = @At("HEAD"), cancellable = true)
    private void injectExecuteServerSide(IPayloadContext context, CallbackInfo ci) {
        if (!(context.player() instanceof ServerPlayer sender)) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = sender.getItemInHand(hand);
            if (CameraModHelper.isSupportedCamera(stack)) {
                Main.CAMERA.get().setActive(stack, false);
            }
        }
        // Cancel original method to prevent duplicate execution
        ci.cancel();
    }
}
