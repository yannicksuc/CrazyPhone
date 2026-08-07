
package fr.lordfinn.crazyphone.world.inventory;

import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import de.maxhenkel.camera.inventory.AlbumInventory;

import java.util.Map;
import java.util.HashMap;

import org.slf4j.LoggerFactory;

public class CrazyPhonePicturesScreenMenu extends CrazyPhoneDefaultScreenMenu {
	public static final HashMap<String, Object> guistate = new HashMap<>();
	private final Map<Integer, Slot> customSlots = new HashMap<>();
	private final int slotWidth = SLOT_PITCH;
	private final int slotHeight = SLOT_PITCH;
	public int albumId = 0;
	/** The album ItemStack itself (icon + custom name), exposed so the screen can show it in its page header. */
	public ItemStack albumStack = ItemStack.EMPTY;

	public CrazyPhonePicturesScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(ModMenus.CRAZY_PHONE_PICTURES_SCREEN.get(), id, inv, extraData);

		if (this.internal != null && extraData != null && extraData.readableBytes() > 0) {
			albumId = extraData.readInt();
			if (albumId >= 0 && albumId < this.internal.getSlots()) {
				ItemStack album = this.internal.getStackInSlot(albumId);
				this.albumStack = album;
				AlbumInventory pictures = new AlbumInventory(entity.registryAccess(), album);
				this.internal = new AlbumInventoryItemHandler(pictures);

				// 6x7 grid - one row shorter than the folders list to leave room for the action buttons
				// (Del/Prendre/Envoyer) below the grid, on top of the header's own space at the top.
				int startX = HEADER_CONTENT_START_X;
				int startY = HEADER_CONTENT_START_Y;

				int slotIndex = 0;
				for (int row = 0; row < 7; row++) {
					for (int col = 0; col < GRID_COLUMNS; col++) {
						int x = startX + col * slotWidth;
						int y = startY + row * slotHeight;
						final int index = slotIndex;

						Slot slot = new SlotItemHandler(internal, index, x, y) {
							@Override
							public boolean mayPickup(Player player) {
								return false;
							}

							@Override
							public boolean mayPlace(ItemStack stack) {
								return false;
							}

							@Override
							public void set(ItemStack stack) {
								// Prevent external stack setting
							}
						};
						this.customSlots.put(index, this.addSlot(slot));
						slotIndex++;
					}
				}
			} else {
				LoggerFactory.getLogger("crazyphone").warn("Invalid slotId: " + albumId + ", handler has " + this.internal.getSlots() + " slots");
			}

		}
		ScreenMenuUtils.addDataToCurrentPage(entity, Integer.toString(albumId));
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player playerIn, int index) {
		return ItemStack.EMPTY; // Disallow quick move
	}

	@Override
	public void removed(Player playerIn) {
		super.removed(playerIn);
		// Do nothing on close
	}

	@Override
	public Map<Integer, Slot> get() {
		return customSlots;
	}

	public class AlbumInventoryItemHandler implements IItemHandlerModifiable {
		public final AlbumInventory albumInventory;

		public AlbumInventoryItemHandler(AlbumInventory albumInventory) {
			this.albumInventory = albumInventory;
		}

		@Override
		public int getSlots() {
			return albumInventory.getContainerSize();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return albumInventory.getItem(slot);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return stack; // Read-only
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return ItemStack.EMPTY; // Read-only
		}

		@Override
		public int getSlotLimit(int slot) {
			return 54; // Safe default
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return false;
		}

		@Override
		public void setStackInSlot(int slot, ItemStack stack) {
			// Read-only — do nothing
		}
	}
}
