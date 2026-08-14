package fr.lordfinn.crazyphone.mixin;

import de.maxhenkel.camera.net.MessageSetShader;
import fr.lordfinn.crazyphone.utils.CameraModAccess;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
*///? } else {
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MessageSetShader.class)
public class CameraModMessageSetShaderMixin {

    //? if >=1.20.5 {
    /*@Inject(method = "executeServerSide", at = @At("HEAD"), cancellable = true)
    private void injectExecuteServerSide(IPayloadContext context, CallbackInfo ci) {
        if (!(context.player() instanceof ServerPlayer sender)) return;

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = sender.getItemInHand(hand);
            if (CameraModHelper.isSupportedCamera(stack)) {
                // Read the shader field from this instance via cast
                String shader = ((MessageSetShaderAccessor) this).getShader();
                stack.set(CameraModAccess.shaderDataComponent(), shader);
            }
        }

        // Cancel original method to prevent duplicate execution
        ci.cancel();
    }
    *///? } else {
    @Inject(method = "executeServerSide", at = @At("HEAD"), cancellable = true)
    private void injectExecuteServerSide(PlayPayloadContext context, CallbackInfo ci) {
        if (!(context.player().orElse(null) instanceof ServerPlayer sender)) return;

        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = sender.getItemInHand(hand);
            if (CameraModHelper.isSupportedCamera(stack)) {
                String shader = ((MessageSetShaderAccessor) this).getShader();
                CameraModAccess.cameraItem().setShader(stack, shader);
            }
        }

        ci.cancel();
    }
    //?}
}
