package fr.lordfinn.crazyphone.utils;

/** Minimal stand-in for NeoForge's DeferredHolder/DeferredItem on the Fabric side, where a plain
 *  Registry#register(...) call just returns the registered value directly with no wrapper at all. Every
 *  ModItems/ModMenus/ModSounds/ModTabs field across this codebase is read via a trailing ".get()" (the
 *  NeoForge convention, used ~40+ times in procedures/screens/items) - wrapping the Fabric-registered value
 *  in this instead of exposing it bare keeps every one of those call sites compiling unchanged on both
 *  loaders, rather than special-casing each one. */
public final class RegistryEntry<T> {
    private final T value;

    public RegistryEntry(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}
