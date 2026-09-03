package fr.lordfinn.crazyphone.mixin;

// >=26 Fabric-only: same strategy as CrazyPhoneItemModelRegistrationMixin (see that mixin's own doc comment
// for the fuller explanation) - NeoForge's own build of ConditionalItemModelProperties#bootstrap() is
// patched directly to fire RegisterConditionalItemModelPropertyEvent right after vanilla's own
// ID_MAPPER.put(...) calls (confirmed via decompiled source). Fabric ships plain, unpatched vanilla here
// too, so this injects straight into the same method, at the same point (TAIL), reaching the exact same
// underlying ID_MAPPER - letting CrazyPhoneItemProperties' ScreenOn/CallState records (loader-neutral, pure
// vanilla ConditionalItemModelProperty API) be shared verbatim between both loaders, same as ModelImpl
// already is for item rendering.
//? if fabric && >=26 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.item.CrazyPhoneItemProperties;

@Mixin(net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties.class)
public abstract class CrazyPhoneConditionalItemModelPropertyMixin {
    @Shadow
    @Final
    private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ConditionalItemModelProperty>> ID_MAPPER;

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private static void crazyphone$registerConditionalProperties(CallbackInfo ci) {
        ID_MAPPER.put(Crazyphone.resource("screen_on"), CrazyPhoneItemProperties.ScreenOn.MAP_CODEC);
        ID_MAPPER.put(Crazyphone.resource("calling"), CrazyPhoneItemProperties.CallState.CALLING_CODEC);
        ID_MAPPER.put(Crazyphone.resource("called_in"), CrazyPhoneItemProperties.CallState.CALLED_IN_CODEC);
        ID_MAPPER.put(Crazyphone.resource("in_call"), CrazyPhoneItemProperties.CallState.IN_CALL_CODEC);
        ID_MAPPER.put(Crazyphone.resource("selfie_mode"), CrazyPhoneItemProperties.SelfieMode.MAP_CODEC);
        ID_MAPPER.put(Crazyphone.resource("selfie_mode_self"), CrazyPhoneItemProperties.SelfieModeSelf.MAP_CODEC);
    }
}
*///?}
