package fr.lordfinn.crazyphone.mixin;

/**
 * Exposes the otherwise-private {@code ItemInHandRenderer#renderPlayerArm} so CrazyPhonePresentHandGripMixin
 * can trigger a bare-arm render manually. Split into its own file because an {@literal @}Invoker and an
 * {@literal @}Inject targeting the same method name in one mixin class trips a mapping conflict in Loom's
 * mixin annotation processor - two separate mixin classes targeting the same target class works fine.
 * Loader-neutral (both loaders lack any dedicated event for this, so a mixin is the right tool on either
 * one, not a Fabric workaround) but version-gated the same way PlayerPresentPoseMixin is:
 * ItemInHandRenderer's own signatures aren't confirmed stable across 1.21.10's broader rendering rework, so
 * the {@code >=1.21.10} branch is a deliberately inert placeholder rather than attempting that blind.
 */
//? if <1.21.10 {
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemInHandRenderer.class)
public interface CrazyPhonePresentHandGripInvokerMixin {
    @Invoker("renderPlayerArm")
    void crazyphone$renderPlayerArm(PoseStack poseStack, MultiBufferSource bufferSource, int light, float equipProgress, float swingProgress, HumanoidArm arm);
}
//?} else {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class CrazyPhonePresentHandGripInvokerMixin {
}
*///?}
