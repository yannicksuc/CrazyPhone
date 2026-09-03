package fr.lordfinn.crazyphone.init;

//? if neoforge {
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
//?}

import fr.lordfinn.crazyphone.client.render.CrazyPhonePhotoFrameRenderer;

/** Client-only registration for {@link CrazyPhonePhotoFrameRenderer} - the mod's first custom entity
 * renderer, so this is a new file rather than reusing ModScreens.java (menu/screen registration only). */
//? if neoforge && >=1.20.5 {
/*@EventBusSubscriber(value = Dist.CLIENT)
public class ModEntityRenderers {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PHOTO_FRAME.get(), CrazyPhonePhotoFrameRenderer::new);
    }
}
*///?}
//? if fabric && >=1.20.5 {
/*public class ModEntityRenderers {
    public static void register() {
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(ModEntities.PHOTO_FRAME.get(), CrazyPhonePhotoFrameRenderer::new);
    }
}
*///?}
//? if <1.20.5 {
public class ModEntityRenderers {
}
//?}
