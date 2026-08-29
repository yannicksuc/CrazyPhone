package fr.lordfinn.crazyphone.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

/** Single choke point for Registry#get(ResourceLocation) - it returned the value directly pre-1.21.10 and
 *  now returns Optional&lt;Holder.Reference&lt;T&gt;&gt;, matching every other registry lookup (see
 *  ModEnchantments for the same shape change on Registry#get(ResourceKey)). Returns null if missing, same
 *  as the old behavior. */
public final class RegistryCompat {
    private RegistryCompat() {
    }

    public static <T> T get(Registry<T> registry, /*$ res_loc {*/ResourceLocation/*$}*/ id) {
        //? if <1.21.10 {
        return registry.get(id);
        //? } else {
        /*return registry.get(id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
        *///?}
    }

    /** RegistryAccess#registryOrThrow(ResourceKey) was renamed #lookupOrThrow in 1.21.10; the resulting
     *  registry's own #getHolderOrThrow(ResourceKey) is unaffected on every version. */
    public static <T> Holder<T> holderOrThrow(RegistryAccess registryAccess, ResourceKey<Registry<T>> registryKey, ResourceKey<T> key) {
        //? if <1.21.10 {
        return registryAccess.registryOrThrow(registryKey).getHolderOrThrow(key);
        //? } else {
        /*return registryAccess.lookupOrThrow(registryKey).getOrThrow(key);
        *///?}
    }
}
