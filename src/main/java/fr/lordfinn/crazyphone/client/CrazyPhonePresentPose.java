package fr.lordfinn.crazyphone.client;

/**
 * "Presenting" a photo: sneaking while holding one (either hand) locks both arms straight out, parallel, in
 * the player's own look direction - as if showing the photo to whoever's in front of them - instead of the
 * normal one-handed carry. Loader-neutral: only PlayerPresentPoseMixin (arms, third person / other players
 * watching you) and CrazyPhonePhotoItemRenderer (the held card itself, including your own first-person view -
 * see that class's own presenting branches) actually consume this; there's no first-person-specific render
 * hook here anymore (an earlier version drew a flat GUI overlay for your own view, replaced by rendering the
 * same lit 3D card CrazyPhonePhotoItemRenderer already uses everywhere else, just centered instead of hand-
 * anchored - a flat 2D draw never picked up world lighting and read as visibly wrong next to it).
 * <ul>
 *   <li>{@link #isPresenting} - the shared trigger condition.</li>
 *   <li>{@link #applyArmTransform} - the actual bone-rotation math, called directly by the mixin right after
 *   vanilla's own per-frame animation setup runs (see that method's own doc comment for why this sets the
 *   arm's ModelPart rotation directly instead of going through a real HumanoidModel.ArmPose enum constant).</li>
 * </ul>
 */
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.item.CrazyPhonePhotoItem;

public final class CrazyPhonePresentPose {
    private CrazyPhonePresentPose() {
    }

    public static boolean isPresenting(LivingEntity entity) {
        if (!(entity instanceof Player player) || !player.isCrouching())
            return false;
        return isPhoto(player.getMainHandItem()) || isPhoto(player.getOffhandItem());
    }

    private static boolean isPhoto(ItemStack stack) {
        return stack.getItem() instanceof CrazyPhonePhotoItem;
    }

    // Fixed relative to the body on purpose, not the head: an early version tracked head.xRot/head.yRot so
    // the arms pointed exactly where you looked, but the head can turn independently of the body (limited
    // yaw range) while both arms used that same head yaw applied around their own, different shoulder
    // pivots - the two hands ended up at different depths/reaches instead of staying parallel, breaking the
    // "flat surface held by both hands" illusion. A constant pose relative to the body guarantees both arms
    // always match, and doubles as the compensating rotation CrazyPhonePhotoItemRenderer's presenting branch
    // needs for the card itself (see PRESENT_SCALE's own doc comment there) - since this never changes
    // frame to frame, that compensation can be a fixed constant too instead of tracking this dynamically.
    // Also mirrors this same rotation onto the PlayerModel-specific sleeve/jacket ModelParts (rightSleeve/
    // leftSleeve): those are separate parts from rightArm/leftArm, not children of them, so overriding only
    // the inner arm leaves the outer clothing layer stuck in whatever pose vanilla's own setupAnim gave it.
    // Called directly by PlayerPresentPoseMixin (loader-neutral - a real, normally-importable LivingEntityRenderer
    // target, not string-targeted) right after vanilla's own setupAnim runs, on BOTH loaders.
    public static void applyArmTransform(HumanoidModel<?> model, HumanoidArm arm) {
        ModelPart armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        // Pitch (up/down look) is safe to track dynamically, unlike yaw - it's the identical value applied
        // to both arms regardless of where the head points left/right, so it can't reintroduce the
        // left/right-diverging-reach problem yaw caused. CrazyPhonePhotoItemRenderer's presenting branch
        // cancels whatever the arm's ACTUAL current rotation is via matrix extraction, not a fixed assumed
        // value, so the card's own orientation stays correct regardless of this varying per frame.
        armPart.xRot = model.head.xRot - (float) (Math.PI / 2);
        armPart.yRot = 0f;
        armPart.zRot = 0f;
        // PlayerModel lost its own generic type parameter in 1.21.10's rendering rework (RenderState-based,
        // same broader change CrazyPhonePhotoItemRenderer's own TODOs already track) - this class isn't
        // actually reachable there anyway (PlayerPresentPoseMixin is an inert placeholder on that version),
        // but it still has to compile, so this specific block is version-gated rather than the whole file.
        //? if <1.21.10 {
        if (model instanceof PlayerModel<?> playerModel) {
            ModelPart sleevePart = arm == HumanoidArm.RIGHT ? playerModel.rightSleeve : playerModel.leftSleeve;
            sleevePart.xRot = armPart.xRot;
            sleevePart.yRot = armPart.yRot;
            sleevePart.zRot = armPart.zRot;
        }
        //?}
    }

    // Bridge from the mixin (which sees the entity) to CrazyPhonePhotoItemRenderer (which, rendering through
    // vanilla's own IClientItemExtensions/BuiltinItemRendererRegistry item-render entry point, never gets an
    // entity reference at all - only the ItemStack/ItemDisplayContext). Set once per entity per frame, right
    // before that same entity's held-item render happens later in the same top-level render() call - safe
    // since Minecraft's rendering is single-threaded and entities render one at a time, never interleaved.
    // Only meaningful for THIRD-person presenting (watching another player, or yourself in F5) - your own
    // first-person view checks isPresenting(Minecraft.getInstance().player) directly instead, since it
    // always concerns the local player and never needs this cross-render bridge.
    public static boolean presentingThisRender = false;
    // The same entityYaw LivingEntityRenderer#render itself uses for its own body-facing rotation
    // (poseStack.mulPose(Axis.YP.rotationDegrees(180 - entityYaw))). CrazyPhonePhotoItemRenderer's presenting
    // branch fully cancels whatever rotation the arm bone baked into its poseStack (rather than guessing a
    // fixed compensating angle - two guesses in a row still came out lying flat, meaning the real composed
    // transform isn't the simple single-axis rotation it looked like) and then re-applies this exact same
    // formula, landing back on "faces the same way the body currently does" by construction instead of luck.
    public static float presentingEntityYaw = 0f;
    // The arm's own extra pitch tilt on top of its fixed baseline (see applyArmTransform - armPart.xRot is
    // head.xRot minus a constant, so head.xRot alone is exactly the "extra" part due to looking up/down).
    // CrazyPhonePhotoItemRenderer's presenting branch applies this same delta to the card itself so it tilts
    // with the arms instead of staying perfectly upright regardless of where you're looking.
    public static float presentingHeadPitch = 0f;
}
