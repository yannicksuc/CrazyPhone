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
}
