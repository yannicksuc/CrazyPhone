package fr.lordfinn.crazyphone.utils;

import java.util.List;
import java.util.UUID;

import de.maxhenkel.camera.gui.AlbumScreen;
import de.maxhenkel.camera.inventory.AlbumInventory;
import de.maxhenkel.camera.items.AlbumItem;
import de.maxhenkel.camera.items.CameraItem;
import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneAlbumScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneImageScreen;
import fr.lordfinn.crazyphone.item.CrazyPhoneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

public class CameraModHelper {
    public static boolean isSupportedCamera(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.getItem() instanceof CameraItem ||
               stack.getItem() instanceof CrazyPhoneItem;
    }

    /** Single choke point for constructing the camera mod's own AlbumInventory - it briefly took a
     *  RegistryAccess as of 1.20.5 (needed for component-aware ItemStack codecs) but the camera mod's own
     *  1.21.10 build dropped that argument again (single-arg constructor, same as pre-1.20.5), so this is
     *  a genuine three-way split, not a straight "old vs new" one. */
    public static AlbumInventory newAlbumInventory(LevelAccessor world, ItemStack albumStack) {
        //? if <1.20.5 {
        return new AlbumInventory(albumStack);
        //?}
        //? if >=1.20.5 <1.21.10 {
        /*return new AlbumInventory(world.registryAccess(), albumStack);
        *///?}
        //? if >=1.21.10 {
        /*return new AlbumInventory(albumStack);
        *///?}
    }


    public static boolean isActive(ItemStack stack) {
        return CameraModAccess.cameraItem().isActive(stack);
    }

    public static void openAlbum(Player player, ItemStack album, int startIndex) {
		if (player.level().isClientSide()) {
			List<UUID> images = new java.util.ArrayList<>();
			List<ItemStack> imageStacks = new java.util.ArrayList<>();
			AlbumInventory inventory = newAlbumInventory(player.level(), album);
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				de.maxhenkel.camera.ImageData imageData = de.maxhenkel.camera.ImageData.fromStack(stack);
				if (imageData == null) continue;
				images.add(imageData.getId());
				imageStacks.add(stack);
			}
			if (!images.isEmpty()) {
				openClientGui(images, imageStacks, startIndex);
			}
		}
	}

	@OnlyIn(Dist.CLIENT)
	private static void openClientGui(List<UUID> images, List<ItemStack> imageStacks, int startIndex) {
		Minecraft mc = Minecraft.getInstance();
		AlbumScreen screen = new CrazyPhoneAlbumScreen(images, imageStacks);
		mc.setScreen(screen);

		mc.execute(() -> {
			if (mc.screen instanceof AlbumScreen s) {
				try {
					var method = AlbumScreen.class.getDeclaredMethod("setIndex", int.class);
					method.setAccessible(true);
					method.invoke(s, startIndex);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	@OnlyIn(Dist.CLIENT)
    public static void openImage(ItemStack stack) {
      Minecraft.getInstance().setScreen(new CrazyPhoneImageScreen(stack));
    }

    public static boolean tryInsertImageIntoCrazyPhone(ServerPlayer player, ItemStack imageStack) {
    ItemStack held = player.getMainHandItem();

    if (!(held.getItem() instanceof CrazyPhoneItem)) {
        return false;
    }

    //? if >=1.21.10 {
    /*if (!(held.getCapability(Capabilities.Item.ITEM, null) instanceof IItemHandlerModifiable handler)) {
        return false;
    }
    *///? } else {
    if (!(held.getCapability(Capabilities.ItemHandler.ITEM, null) instanceof IItemHandlerModifiable handler)) {
        return false;
    }
    //?}

    for (int slot = 0; slot < handler.getSlots(); slot++) {
        ItemStack slotStack = handler.getStackInSlot(slot);

        // Cas 1 : slot vide ou mauvais item, on crée un album
        if (slotStack.isEmpty() || !(slotStack.getItem() instanceof AlbumItem)) {
            ItemStack newAlbum = new ItemStack(CameraModAccess.albumItem());
            if (tryAddImageToAlbum(player, newAlbum, imageStack)) {
                handler.setStackInSlot(slot, newAlbum);
                return true;
            }
            continue;
        }

        // Cas 2 : album déjà présent
        if (slotStack.getItem() instanceof AlbumItem) {
            if (tryAddImageToAlbum(player, slotStack, imageStack)) {
                handler.setStackInSlot(slot, slotStack);
                return true;
            }
        }
    }

    return false;
    }

    private static boolean tryAddImageToAlbum(ServerPlayer player, ItemStack albumStack, ItemStack imageStack) {
        AlbumInventory inventory = newAlbumInventory(player.level(), albumStack);

        // maxAlbumSlotsPerPhone's config range goes up to 97, but AlbumInventory's own backing storage is a
        // fixed-size (54-slot) vanilla container (the same NonNullList a shulker box uses) - without this
        // clamp, a server configured above 54 would throw IndexOutOfBoundsException here the moment someone
        // tried to fill a slot past the container's real capacity.
        int limit = Math.min(Config.maxAlbumSlotsPerPhone, inventory.getContainerSize());
        for (int i = 0; i < limit; i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, imageStack.copy());
                return true;
            }
        }

        return false; // Album full
    }
}
