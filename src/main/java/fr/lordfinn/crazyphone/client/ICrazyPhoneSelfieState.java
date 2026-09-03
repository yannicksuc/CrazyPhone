package fr.lordfinn.crazyphone.client;

/**
 * Duck interface a mixin attaches to {@code LivingEntityRenderState} (>=1.21.10 only) so
 * {@code CrazyPhoneSelfieArmPoseMixin}'s two injection points - {@code extractRenderState} (which sees the
 * live entity) and {@code submit} (which only ever sees the per-frame render state, never the entity) - can
 * share "is this the local player mid-selfie" across the state object each render actually threads through.
 * Deliberately a separate interface/field from {@link ICrazyPhonePresentingState} - selfie framing (capture
 * mode, holding the phone itself, before any photo exists) and presenting (sneaking while holding an
 * already-taken photo item) are unrelated triggers with unrelated poses, sharing only the general "override
 * the arm bone after setupAnim runs" mechanism, not any state or code.
 */
public interface ICrazyPhoneSelfieState {
    boolean crazyphone$isSelfie();

    void crazyphone$setSelfie(boolean value);
}
