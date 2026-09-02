package fr.lordfinn.crazyphone.mixin;

// >=26 Fabric-only: NeoForge's own build of ItemModels#bootstrap() is patched directly to fire
// RegisterItemModelsEvent right after vanilla's own ID_MAPPER.put(...) calls (confirmed via decompiled
// source - net.neoforged.fml.ModLoader.postEvent(new RegisterItemModelsEvent(ID_MAPPER)) sits inline at the
// end of that method on the NeoForge jar). Fabric ships plain, unpatched vanilla here and has no equivalent
// event yet - injecting straight into the same method, at the same point (TAIL, after vanilla's own
// registrations), reaches the exact same underlying ID_MAPPER NeoForge's event exposes, so
// CrazyPhonePhotoItemRenderer's ModelImpl/SpecialRendererImpl (loader-neutral, pure vanilla ItemModel API)
// can be shared verbatim between both loaders without needing its own Fabric-specific variant.
//? if fabric && >=26 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.render.CrazyPhonePhotoItemRenderer;

@Mixin(net.minecraft.client.renderer.item.ItemModels.class)
public abstract class CrazyPhoneItemModelRegistrationMixin {
    @Shadow
    @Final
    private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void crazyphone$registerPhotoModel(CallbackInfo ci) {
        ID_MAPPER.put(Crazyphone.resource("photo_card_model"), CrazyPhonePhotoItemRenderer.ModelImpl.Unbaked.MAP_CODEC);
    }
}
*///?}
