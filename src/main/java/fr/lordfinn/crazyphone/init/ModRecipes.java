package fr.lordfinn.crazyphone.init;

// NeoForge-1.21.10 has no entries here yet - see CrazyPhoneDuplicatePhotoRecipe's own doc comment for why.
// Flat "loader && version" conditions below rather than nesting a loader-only //? if inside a version-only
// one (or vice versa) - see NetworkAccess.java's own doc comment for why that nesting corrupts Stonecutter's
// output; this mirrors its flat-sibling-blocks shape instead.
//? if neoforge && <1.21.10 {
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.recipe.CrazyPhoneDuplicatePhotoRecipe;

public class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> REGISTRY = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Crazyphone.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<CrazyPhoneDuplicatePhotoRecipe>> DUPLICATE_PHOTO =
            REGISTRY.register("crafting_special_duplicate_photo", () -> new SimpleCraftingRecipeSerializer<>(CrazyPhoneDuplicatePhotoRecipe::new));
}
//?}
//? if fabric && >=1.20.5 {
/*import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.recipe.CrazyPhoneDuplicatePhotoRecipe;
import fr.lordfinn.crazyphone.utils.RegistryEntry;

public class ModRecipes {
    public static RegistryEntry<SimpleCraftingRecipeSerializer<CrazyPhoneDuplicatePhotoRecipe>> DUPLICATE_PHOTO;

    public static void register() {
        DUPLICATE_PHOTO = new RegistryEntry<>(Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
                Crazyphone.resource("crafting_special_duplicate_photo"),
                new SimpleCraftingRecipeSerializer<>(CrazyPhoneDuplicatePhotoRecipe::new)));
    }
}
*///?}
