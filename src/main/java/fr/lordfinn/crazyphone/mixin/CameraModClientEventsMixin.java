package fr.lordfinn.crazyphone.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import de.maxhenkel.camera.ClientEvents;
import de.maxhenkel.camera.Main;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientEvents.class)
public abstract class CameraModClientEventsMixin {

    @Inject(method = "getActiveCamera", at = @At("RETURN"), cancellable = true)
    private void injectGetActiveCamera(CallbackInfoReturnable<ItemStack> cir) {
        if (cir.getReturnValue() != null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = mc.player.getItemInHand(hand);
            if (CameraModHelper.isSupportedCamera(stack) && Main.CAMERA.get().isActive(stack)) {
                cir.setReturnValue(stack);
                return;
            }
        }
    }

    @Inject(method = "renderPlayer", at = @At("HEAD"), cancellable = true)
    private void onRenderPlayer(RenderPlayerEvent.Pre event, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        for (AbstractClientPlayer player : level.players()) {
            if (player == mc.player) continue;

            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                if (CameraModHelper.isSupportedCamera(stack)) {
                    if (Main.CAMERA.get().isActive(stack)) {
                        player.startUsingItem(hand);
                    } else {
                        player.stopUsingItem();
                    }
                }
            }
        }
        ci.cancel();
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    public void onRenderOverlay(RenderGuiLayerEvent.Pre event, CallbackInfo ci) {
        if (ImageTakerAccessor.getTakeScreenshot()) {
            event.setCanceled(true);
            ci.cancel();
        }
    }
}
