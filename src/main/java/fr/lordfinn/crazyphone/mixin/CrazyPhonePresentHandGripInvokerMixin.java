package fr.lordfinn.crazyphone.mixin;

/**
 * Exposes the otherwise-private {@code ItemInHandRenderer#renderPlayerArm} so CrazyPhonePresentHandGripMixin
 * can trigger a bare-arm render manually. Split into its own file because an {@literal @}Invoker and an
 * {@literal @}Inject targeting the same method name in one mixin class trips a mapping conflict in Loom's
 * mixin annotation processor - two separate mixin classes targeting the same target class works fine.
 * Loader-neutral (both loaders lack any dedicated event for this, so a mixin is the right tool on either
 * one, not a Fabric workaround) but version-gated the same way PlayerPresentPoseMixin is: three branches,
 * not one <1.21.10/else split - 1.21.10 itself has yet another incompatible API shape (matching
 * PlayerPresentPoseMixin's own finding), confirmed by trying to compile the >=26 branch against it, so it
 * stays its own inert placeholder. >=26's renderPlayerArm keeps the exact same shape as <1.21.10's, just
 * with MultiBufferSource swapped for SubmitNodeCollector (confirmed against the real 26.1.2.100 decompiled
 * source) - the same broader buffer-type rework seen everywhere else in this codebase's 26.x work.
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
//?}
//? if >=1.21.10 <26 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class CrazyPhonePresentHandGripInvokerMixin {
}
*///?}
//? if >=26 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemInHandRenderer.class)
public interface CrazyPhonePresentHandGripInvokerMixin {
    @Invoker("renderPlayerArm")
    void crazyphone$renderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light, float equipProgress, float swingProgress, HumanoidArm arm);
}
*///?}
