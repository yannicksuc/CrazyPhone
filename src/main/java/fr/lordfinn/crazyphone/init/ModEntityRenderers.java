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
// Real, original Forge 1.20.1: same EntityRenderersEvent.RegisterRenderers, on the MOD bus (old Forge's
// @EventBusSubscriber needs the bus spelled out, it does not route mod-bus events on its own).
//? if legacyforge {
/*@net.minecraftforge.fml.common.Mod.EventBusSubscriber(value = net.minecraftforge.api.distmarker.Dist.CLIENT, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD)
public class ModEntityRenderers {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void registerRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.PHOTO_FRAME.get(), CrazyPhonePhotoFrameRenderer::new);
    }
}
*///?}
//? if (fabric || neoforge) && <1.20.5 {
public class ModEntityRenderers {
}
//?}
