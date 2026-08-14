package fr.lordfinn.crazyphone.enchantment;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.LevelAccessor;

import fr.lordfinn.crazyphone.Crazyphone;

// Registry key for the data-driven "soulbound" enchantment (see resources/data/crazyphone/enchantment) -
// data-driven enchantments have no static Java instance to reference directly, only a key resolved against
// whichever level's registry access is on hand at the point of use. Only actually registered on >=1.20.5
// (see the enchantment JSON and SoulboundHandler, both guarded out below that) - harmless to keep this key
// itself version-agnostic, since RegistryAccess/Registries.ENCHANTMENT are unchanged that far back.
public class ModEnchantments {
    public static final ResourceKey<Enchantment> SOULBOUND = ResourceKey.create(Registries.ENCHANTMENT, Crazyphone.resource("soulbound"));

    /** Resolves {@link #SOULBOUND} against the given level's registries, or null if it's missing/unregistered
     * (e.g. the enchantment JSON failed to load) - single choke point for two 1.21.10 renames at once:
     * RegistryAccess#registryOrThrow -> #lookupOrThrow, and Registry#getHolder -> #get (both still return
     * Optional&lt;Holder.Reference&lt;T&gt;&gt;, only the method name moved). */
    public static Holder<Enchantment> resolve(LevelAccessor world) {
        RegistryAccess access = world.registryAccess();
        //? if <1.21.10 {
        Registry<Enchantment> registry = access.registryOrThrow(Registries.ENCHANTMENT);
        return registry.getHolder(SOULBOUND).orElse(null);
        //? } else {
        /*Registry<Enchantment> registry = access.lookupOrThrow(Registries.ENCHANTMENT);
        return registry.get(SOULBOUND).orElse(null);
        *///?}
    }
}
