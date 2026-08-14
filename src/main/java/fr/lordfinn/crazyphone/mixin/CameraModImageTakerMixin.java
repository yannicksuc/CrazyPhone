package fr.lordfinn.crazyphone.mixin;

import de.maxhenkel.camera.ImageTaker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.20.5 {
/*import net.neoforged.neoforge.client.event.RenderFrameEvent;
*///?}
//? if <1.20.5 {
import net.neoforged.neoforge.event.TickEvent;
//?}
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ImageTaker.class)
public class CameraModImageTakerMixin {

    @Shadow private static boolean takeScreenshot;
    @Shadow private static UUID uuid;
    @Shadow private static boolean hide;

    @Unique
    private static int delayTicks = 0;

    @Inject(method = "takeScreenshot", at = @At("HEAD"), cancellable = true)
    private static void injectTakeScreenshot(UUID id, CallbackInfo ci) {
        if (!takeScreenshot || !id.equals(uuid)) {
            Minecraft mc = Minecraft.getInstance();
            hide = mc.options.hideGui;
            mc.options.hideGui = true;
            uuid = id;
            delayTicks = 40; // Delay for 5 frames
            takeScreenshot = true;
            mc.setScreen((Screen) null);
        } else {
            ci.cancel(); // Don't proceed if already preparing screenshot for same UUID
        }
    }

    //? if >=1.20.5 <1.21.10 {
    /*@Inject(method = "onRenderTickEnd", at = @At("HEAD"), cancellable = true)
private static void injectOnRenderTickEnd(RenderFrameEvent.Post event, CallbackInfo ci) {
    Minecraft mc = Minecraft.getInstance();
    if (takeScreenshot) {
        if (mc.screen != null || !mc.options.hideGui) {
            // On attend que l'écran soit fermé ET que le HUD soit caché
            ci.cancel();
            return;
        }

        NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        mc.options.hideGui = hide;
        takeScreenshot = false;
        de.maxhenkel.camera.ImageProcessor.sendScreenshotThreaded(uuid, image);
        ci.cancel();
    }
}
    *///?}
    //? if >=1.21.10 {
    /*// Screenshot.takeScreenshot became asynchronous in 1.21.10 (a Consumer<NativeImage> callback instead
    // of a direct return) - the surrounding hideGui/takeScreenshot state resets and the actual send now
    // happen inside that callback instead of immediately after the call, but the ordering relative to
    // ci.cancel() is unchanged (cancel just stops vanilla's own tick handling, independent of when the
    // screenshot capture itself finishes).
    @Inject(method = "onRenderTickEnd", at = @At("HEAD"), cancellable = true)
private static void injectOnRenderTickEnd(RenderFrameEvent.Post event, CallbackInfo ci) {
    Minecraft mc = Minecraft.getInstance();
    if (takeScreenshot) {
        if (mc.screen != null || !mc.options.hideGui) {
            // On attend que l'écran soit fermé ET que le HUD soit caché
            ci.cancel();
            return;
        }

        Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
            mc.options.hideGui = hide;
            takeScreenshot = false;
            de.maxhenkel.camera.ImageProcessor.sendScreenshotThreaded(uuid, image);
        });
        ci.cancel();
    }
}
    *///?}
    //? if <1.20.5 {
    @Inject(method = "onRenderTickEnd", at = @At("HEAD"), cancellable = true)
    private static void injectOnRenderTickEnd(TickEvent.RenderTickEvent event, CallbackInfo ci) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (takeScreenshot) {
            if (mc.screen != null || !mc.options.hideGui) {
                ci.cancel();
                return;
            }

            NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
            mc.options.hideGui = hide;
            takeScreenshot = false;
            de.maxhenkel.camera.ImageProcessor.sendScreenshotThreaded(uuid, image);
            ci.cancel();
        }
    }
    //?}
}
