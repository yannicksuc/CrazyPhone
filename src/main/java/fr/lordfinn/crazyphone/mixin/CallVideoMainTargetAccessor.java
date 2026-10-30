package fr.lordfinn.crazyphone.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
//? if <26 {
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
//?}

/**
 * Lets the call video selfie camera (CallVideoFrontCamera) point Minecraft's main render target at its own small
 * offscreen target for the duration of one extra world pass, then back. Below 26.x only: 26.x moved the main target
 * into GameRenderer and the selfie camera is not offered there.
 */
@Mixin(Minecraft.class)
public interface CallVideoMainTargetAccessor {
    //? if <26 {
    @Accessor("mainRenderTarget")
    @Mutable
    void crazyphone$setMainRenderTarget(com.mojang.blaze3d.pipeline.RenderTarget target);
    //?}
}
