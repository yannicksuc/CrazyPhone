package fr.lordfinn.crazyphone.mixin;

import de.maxhenkel.camera.net.MessageSetShader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MessageSetShader.class)
public interface MessageSetShaderAccessor {
    @Accessor("shader")
    String getShader();
}
