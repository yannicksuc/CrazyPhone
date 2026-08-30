package fr.lordfinn.crazyphone.client;

/**
 * Duck interface a mixin attaches to {@code LivingEntityRenderState} (>=1.21.10 only) so
 * {@code PlayerPresentPoseMixin}'s two injection points - {@code extractRenderState} (which sees the live
 * entity) and {@code submit} (which only ever sees the per-frame render state, never the entity) - can share
 * "is this player presenting a photo" across the state object each render actually threads through, instead
 * of a shared static field racing across entities the way the pre-1.21.10 code path could get away with.
 */
public interface ICrazyPhonePresentingState {
    boolean crazyphone$isPresenting();

    void crazyphone$setPresenting(boolean value);

    // Same bridge, for CrazyPhonePresentPose#isDualPresenting (a photo in EACH hand at once) - needed once
    // the third-person presenting branch had to distinguish "one shared card" from "two separate photos, one
    // per arm" the same way the first-person branch already does, and CrazyPhonePhotoItemRenderer has no
    // entity reference of its own to check this directly (see this interface's own doc comment on why).
    boolean crazyphone$isDualPresenting();

    void crazyphone$setDualPresenting(boolean value);
}
