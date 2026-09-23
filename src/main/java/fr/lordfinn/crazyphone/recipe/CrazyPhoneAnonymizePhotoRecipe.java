package fr.lordfinn.crazyphone.recipe;

/**
 * "Anonymize a photo" special crafting recipe: exactly one Photo item + one Sponge, nothing else, produces
 * a Photo item pointing at the SAME stored photo but with its {@code owner}/{@code created} tags blanked
 * out - {@link fr.lordfinn.crazyphone.procedures.CrazyPhoneItemInInventoryTickProcedure} skips the "Taken
 * by"/"Taken on" tooltip lines entirely once {@code owner} reads empty (live request: "supprime le taken
 * by et taken on"). The photoId itself is untouched - this only strips the item's own DISPLAYED
 * attribution, it does not (and cannot) touch {@code PhotoSavedData}'s own server-side owner/authorization
 * record for that id, which every other feature (give-as-item, add-to-album, canAccessPhoto, ...) keeps
 * reading unaffected.
 * <p>
 * The sponge is NOT consumed (live request: "l'eponge est pas consommee") - see getRemainingItems, which
 * puts a fresh copy of the matched sponge stack back into its own grid slot instead of leaving it empty,
 * the same mechanism {@link CrazyPhoneDuplicatePhotoRecipe} already uses for the photo it keeps in place -
 * just aimed at the OTHER ingredient here, since this recipe consumes the photo (as an ingredient) to
 * produce its anonymized replacement instead of keeping the original stack.
 * <p>
 * Same NeoForge-1.21.10+ scope limit as CrazyPhoneDuplicatePhotoRecipe (see that class's own doc comment
 * for why) - not backported yet, and this file mirrors that one's exact version-gating for the same reason.
 */
//? if <1.21.10 {
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.init.ModRecipes;
import fr.lordfinn.crazyphone.item.CrazyPhonePhotoItem;
import fr.lordfinn.crazyphone.utils.PhotoItemData;

import java.util.List;

public class CrazyPhoneAnonymizePhotoRecipe extends CustomRecipe {
    //? if fabric || neoforge {
    public CrazyPhoneAnonymizePhotoRecipe(CraftingBookCategory category) {
        super(category);
    }
    //?}
    // Real 1.20.1 vanilla's CustomRecipe still carries an explicit ResourceLocation id - same reasoning as
    // CrazyPhoneDuplicatePhotoRecipe's own matching constructor.
    //? if legacyforge {
    public CrazyPhoneAnonymizePhotoRecipe(net.minecraft.resources.ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }
    //?}

    // Returns the matched photo stack, or null unless the grid holds exactly one Photo + one Sponge and
    // nothing else.
    private static ItemStack findPhoto(List<ItemStack> items) {
        ItemStack photo = null;
        boolean foundSponge = false;
        for (ItemStack stack : items) {
            if (stack.isEmpty())
                continue;
            if (stack.getItem() instanceof CrazyPhonePhotoItem) {
                if (photo != null)
                    return null;
                photo = stack;
            } else if (stack.is(Items.SPONGE)) {
                if (foundSponge)
                    return null;
                foundSponge = true;
            } else {
                return null;
            }
        }
        return photo != null && foundSponge ? photo : null;
    }

    // A full copy (keeps photoId and any other component the item carries) with just owner/created
    // blanked - shared by both CraftingContainer/CraftingInput assemble() bodies below.
    private static ItemStack anonymized(ItemStack photo) {
        ItemStack result = photo.copyWithCount(1);
        PhotoItemData data = PhotoItemData.fromStack(result);
        if (data != null)
            new PhotoItemData(data.photoId(), "", 0).writeTo(result);
        return result;
    }

    //? if <1.20.5 {
    public boolean matches(net.minecraft.world.inventory.CraftingContainer input, Level level) {
        return findPhoto(input.getItems()) != null;
    }

    public ItemStack assemble(net.minecraft.world.inventory.CraftingContainer input, net.minecraft.core.RegistryAccess registries) {
        ItemStack photo = findPhoto(input.getItems());
        return photo != null ? anonymized(photo) : ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.inventory.CraftingContainer input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
        List<ItemStack> items = input.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).is(Items.SPONGE)) {
                remaining.set(i, items.get(i).copyWithCount(1));
                break;
            }
        }
        return remaining;
    }
    //?} else {
    /*public boolean matches(net.minecraft.world.item.crafting.CraftingInput input, Level level) {
        return findPhoto(input.items()) != null;
    }

    public ItemStack assemble(net.minecraft.world.item.crafting.CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) {
        ItemStack photo = findPhoto(input.items());
        return photo != null ? anonymized(photo) : ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.item.crafting.CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        List<ItemStack> items = input.items();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).is(Items.SPONGE)) {
                remaining.set(i, items.get(i).copyWithCount(1));
                break;
            }
        }
        return remaining;
    }
    *///?}

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.ANONYMIZE_PHOTO.get();
    }
}
//?}
