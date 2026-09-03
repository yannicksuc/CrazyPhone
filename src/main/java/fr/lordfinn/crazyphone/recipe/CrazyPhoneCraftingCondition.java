package fr.lordfinn.crazyphone.recipe;

/**
 * Recipe-loading condition for crazy_phone.json, read via NeoForge's own "neoforge:conditions" key and
 * Fabric's "fabric:load_conditions" key (both present in that one shared JSON file - each loader's resource
 * loader only looks for its own key and ignores the other, a known-safe pattern for keeping one recipe file
 * cross-loader). Re-evaluated on every datapack reload (server start, /reload) against
 * {@link fr.lordfinn.crazyphone.Config#crazyPhoneCraftingEnabled} - NOT instant like the /crazyphone feature
 * toggles (those flip a live boolean an event handler checks every time; this one only gets read again when
 * the recipe JSON itself gets re-parsed), a deliberate tradeoff for staying entirely inside each loader's
 * own established, version-stable condition system instead of hooking crafting-result computation directly
 * (which would also need reconciling the CraftingRecipe API break at 1.21.10 - see
 * CrazyPhoneDuplicatePhotoRecipe's own doc comment for that whole story).
 *
 * Fabric's own ResourceCondition#test parameter type changed between fabric-resource-conditions-api-v1 4.x
 * (net.minecraft.core.HolderLookup.Provider - what 1.21.1-fabric's own fabric-api version bundles) and 6.x
 * (net.minecraft.resources.RegistryOps.RegistryInfoLookup - what 26.1-fabric's bundles) - confirmed via
 * javap against each version's real jar (intermediary names class_7225$class_7874 vs the new type), not
 * documented anywhere obvious - hence three independent top-level blocks below (neoforge, fabric &lt;26,
 * fabric &gt;=26) rather than nesting a version toggle inside the fabric one, which breaks Stonecutter's
 * output the same way CrazyPhoneSelfieCameraMixin's own doc comment already found once for a different file.
 */
//? if neoforge {
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.Crazyphone;

public record CrazyPhoneCraftingCondition() implements ICondition {
    public static final DeferredRegister<MapCodec<? extends ICondition>> REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Crazyphone.MODID);

    public static final MapCodec<CrazyPhoneCraftingCondition> CODEC = MapCodec.unit(CrazyPhoneCraftingCondition::new);

    static {
        REGISTRY.register("crafting_enabled", () -> CODEC);
    }

    @Override
    public boolean test(IContext context) {
        return Config.crazyPhoneCraftingEnabled;
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
//?}
//? if fabric && <26 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.Crazyphone;

public record CrazyPhoneCraftingCondition() implements ResourceCondition {
    public static final ResourceConditionType<CrazyPhoneCraftingCondition> TYPE =
            ResourceConditionType.create(Crazyphone.resource("crafting_enabled"), MapCodec.unit(CrazyPhoneCraftingCondition::new));

    public static void register() {
        ResourceConditions.register(TYPE);
    }

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(HolderLookup.Provider lookup) {
        return Config.crazyPhoneCraftingEnabled;
    }
}
*///?}
//? if fabric && >=26 {
/*import com.mojang.serialization.MapCodec;
import net.minecraft.resources.RegistryOps;

import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditionType;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.Crazyphone;

public record CrazyPhoneCraftingCondition() implements ResourceCondition {
    public static final ResourceConditionType<CrazyPhoneCraftingCondition> TYPE =
            ResourceConditionType.create(Crazyphone.resource("crafting_enabled"), MapCodec.unit(CrazyPhoneCraftingCondition::new));

    public static void register() {
        ResourceConditions.register(TYPE);
    }

    @Override
    public ResourceConditionType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean test(RegistryOps.RegistryInfoLookup lookup) {
        return Config.crazyPhoneCraftingEnabled;
    }
}
*///?}
