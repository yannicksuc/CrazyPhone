package fr.lordfinn.crazyphone.recipe;

/**
 * "Duplicate a photo" special crafting recipe: exactly one Photo item + one Paper, nothing else, produces a
 * second Photo item pointing at the SAME stored photo - a photo is an opaque UUID-addressed blob in
 * PhotoSavedData, so a "copy" is just another ItemStack referencing that id, no byte duplication needed. The
 * paper is consumed; the photo is not - see getRemainingItems, which puts a fresh copy of the matched photo
 * stack back into its own grid slot instead of leaving it empty, the exact mechanism vanilla's own
 * BookCloningRecipe uses to keep a written book in place while consuming the blank books crafted around it.
 *
 * NeoForge-1.21.10 only: the Recipe/CraftingRecipe API was reworked there (CraftingInput's own shape stayed,
 * but getResultItem/canCraftInDimensions were dropped in favor of a display()/placementInfo() model) - not
 * backported yet, tracked alongside this mod's other 1.21.10 TODOs (photo capture, the item's custom
 * renderer - see FabricPictureCapture/CrazyPhonePhotoItem's own doc comments). The whole file (imports
 * included) stays inside one <1.21.10 guard so nothing here resolves against types that stopped existing
 * there (ModRecipes itself is <1.21.10-only too - see its own doc comment).
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

import java.util.List;

public class CrazyPhoneDuplicatePhotoRecipe extends CustomRecipe {
    public CrazyPhoneDuplicatePhotoRecipe(CraftingBookCategory category) {
        super(category);
    }

    // Returns the matched photo stack, or null unless the grid holds exactly one Photo + one Paper and
    // nothing else.
    private static ItemStack findPhoto(List<ItemStack> items) {
        ItemStack photo = null;
        boolean foundPaper = false;
        for (ItemStack stack : items) {
            if (stack.isEmpty())
                continue;
            if (stack.getItem() instanceof CrazyPhonePhotoItem) {
                if (photo != null)
                    return null;
                photo = stack;
            } else if (stack.is(Items.PAPER)) {
                if (foundPaper)
                    return null;
                foundPaper = true;
            } else {
                return null;
            }
        }
        return photo != null && foundPaper ? photo : null;
    }

    //? if <1.20.5 {
    public boolean matches(net.minecraft.world.inventory.CraftingContainer input, Level level) {
        return findPhoto(input.getItems()) != null;
    }

    public ItemStack assemble(net.minecraft.world.inventory.CraftingContainer input, net.minecraft.core.RegistryAccess registries) {
        ItemStack photo = findPhoto(input.getItems());
        return photo != null ? photo.copyWithCount(1) : ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.inventory.CraftingContainer input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
        List<ItemStack> items = input.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItem() instanceof CrazyPhonePhotoItem) {
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
        return photo != null ? photo.copyWithCount(1) : ItemStack.EMPTY;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(net.minecraft.world.item.crafting.CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        List<ItemStack> items = input.items();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getItem() instanceof CrazyPhonePhotoItem) {
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
        return ModRecipes.DUPLICATE_PHOTO.get();
    }
}
//?}
