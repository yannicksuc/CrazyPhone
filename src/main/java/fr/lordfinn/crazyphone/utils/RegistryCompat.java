package fr.lordfinn.crazyphone.utils;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/** Single choke point for Registry#get(ResourceLocation) - it returned the value directly pre-1.21.10 and
 *  now returns Optional&lt;Holder.Reference&lt;T&gt;&gt;, matching every other registry lookup (see
 *  ModEnchantments for the same shape change on Registry#get(ResourceKey)). Returns null if missing, same
 *  as the old behavior. */
public final class RegistryCompat {
    private RegistryCompat() {
    }

    public static <T> T get(Registry<T> registry, ResourceLocation id) {
        //? if <1.21.10 {
        return registry.get(id);
        //? } else {
        /*return registry.get(id).map(net.minecraft.core.Holder.Reference::value).orElse(null);
        *///?}
    }
}
