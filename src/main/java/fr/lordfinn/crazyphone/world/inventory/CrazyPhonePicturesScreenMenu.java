
package fr.lordfinn.crazyphone.world.inventory;

import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.FriendlyByteBuf;
import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import de.maxhenkel.camera.inventory.AlbumInventory;

import java.util.Map;
import java.util.HashMap;

import org.slf4j.LoggerFactory;

public class CrazyPhonePicturesScreenMenu extends CrazyPhoneDefaultScreenMenu {
	public static final HashMap<String, Object> guistate = new HashMap<>();
	/** Instagram-feed style: 3 wide, big enough for a real cropped photo thumbnail instead of a 16x16 item
	 * icon - deliberately NOT the shared GRID_COLUMNS/SLOT_PITCH every other grid in this mod uses, since
	 * those are sized for item icons. 34px thumbnail + 1px gap each side = 36px pitch, and 3*36=108 lines up
	 * with the exact same total content width every other 6-column/18px-pitch grid already uses. */
	public static final int ALBUM_COLUMNS = 3;
	public static final int THUMB_SIZE = 34;
	public static final int THUMB_PITCH = 36;
	/** One screen's worth of rows - matches the fixed grid built below. Album slots beyond this are reached
	 * by scrolling (see AlbumInventoryItemHandler#scrollBy), not by a second network fetch: the album's
	 * whole picture list is the same small, fixed-size vanilla container a shulker box or bundle already
	 * uses (AlbumInventory.SIZE, 54 slots - already synced whole by the same MenuProvider mechanism vanilla
	 * uses for those), so there's nothing to page over the network here, only a visible-window to scroll. */
	private static final int VISIBLE_ROWS = 3;
	private final Map<Integer, Slot> customSlots = new HashMap<>();
	public int albumId = 0;
	/** The album ItemStack itself (icon + custom name), exposed so the screen can show it in its page header. */
	public ItemStack albumStack = ItemStack.EMPTY;
	private AlbumInventoryItemHandler albumHandler;

	public CrazyPhonePicturesScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(ModMenus.CRAZY_PHONE_PICTURES_SCREEN.get(), id, inv, extraData);

		if (extraData != null && extraData.readableBytes() > 0) {
			albumId = extraData.readInt();
			// The album ItemStack is transmitted whole (see ScreenMenuUtils#openPhoneAlbumMenu) rather than
			// re-read from this client's own copy of the held phone item's capability - that copy can still
			// be a tick or two stale right after a photo is taken (its sync packet racing the menu-open
			// packet), which used to make a freshly taken photo invisible until the album was reopened.
			CompoundTag albumTag = extraData.readNbt();
			ItemStack album = CrazyPhoneHelper.decodeItemStack(this.world, albumTag != null ? albumTag : new CompoundTag());
			this.albumStack = album;
			if (!album.isEmpty()) {
				//? if >=1.20.5 {
				/*AlbumInventory pictures = new AlbumInventory(entity.level().registryAccess(), album);
				*///? } else {
				AlbumInventory pictures = new AlbumInventory(album);
				//?}
				this.albumHandler = new AlbumInventoryItemHandler(pictures);
				this.internal = albumHandler;

				// 3x3 visible grid of big square thumbnails, leaving room below for the action buttons
				// (Del/Prendre/Envoyer) on top of the header's own space at the top. The album itself holds
				// up to AlbumInventory.SIZE (54) pictures - reaching the ones past what fits on screen is
				// done by scrolling (see scrollAlbumBy), which just shifts which underlying slots these same
				// fixed Slot objects show, not by rebuilding them. Slots here are position bookkeeping only
				// (mayPickup/mayPlace/set are all no-ops below) - the screen draws its own cropped thumbnail
				// per slot instead of the vanilla 16x16 item-icon render, see CrazyPhonePicturesScreenScreen.
				int startX = HEADER_CONTENT_START_X;
				int startY = HEADER_CONTENT_START_Y + 2;

				int slotIndex = 0;
				for (int row = 0; row < VISIBLE_ROWS; row++) {
					for (int col = 0; col < ALBUM_COLUMNS; col++) {
						int x = startX + col * THUMB_PITCH;
						int y = startY + row * THUMB_PITCH;
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
				LoggerFactory.getLogger("crazyphone").warn("Invalid albumId: " + albumId + " - no album data transmitted");
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

	/** Shifts the visible window by {@code deltaRows} (positive = scroll down toward later pictures),
	 * clamped so the last row of the album's real storage never scrolls fully off past the bottom of the
	 * grid. No-op (returns false) if there's no album loaded or scrolling wouldn't move anything, so the
	 * screen can skip a wasted re-render. */
	public boolean scrollAlbumBy(int deltaRows) {
		return albumHandler != null && albumHandler.scrollBy(deltaRows);
	}

	/** The real, unshifted album slot a visible grid position currently maps to - needed wherever code
	 * (e.g. the zoom-viewer's "ignore empty slots" index math) must reason about the album's actual
	 * storage rather than the currently-scrolled visible window. */
	public int absoluteAlbumIndex(int visibleIndex) {
		return albumHandler == null ? visibleIndex : albumHandler.rowOffset * ALBUM_COLUMNS + visibleIndex;
	}

	/** Unlike {@link #internal}'s own getStackInSlot (which is scroll-shifted, for grid rendering), this
	 * always reads the album's true, absolute slot - the zoom-viewer index math needs the real content from
	 * slot 0 onward regardless of where the grid is currently scrolled to. */
	public ItemStack getAbsoluteAlbumStack(int absoluteIndex) {
		return albumHandler == null ? ItemStack.EMPTY : albumHandler.albumInventory.getItem(absoluteIndex);
	}

	/** Pure row-offset clamping math, pulled out of the (otherwise unconstructable-in-isolation) inner
	 * AlbumInventoryItemHandler so it has a direct unit-test surface. {@code containerSize}/{@code columns}
	 * need not divide evenly - the ceiling division below is what makes a partially-filled last row still
	 * count as a full scrollable row instead of vanishing. */
	static int clampRowOffset(int currentOffset, int deltaRows, int containerSize, int columns, int visibleRows) {
		int totalRows = (containerSize + columns - 1) / columns;
		int maxOffset = Math.max(0, totalRows - visibleRows);
		return Math.max(0, Math.min(maxOffset, currentOffset + deltaRows));
	}

	public class AlbumInventoryItemHandler implements IItemHandlerModifiable {
		public final AlbumInventory albumInventory;
		/** In rows, not slots - 0 means the grid shows the album's own first row. */
		private int rowOffset = 0;

		public AlbumInventoryItemHandler(AlbumInventory albumInventory) {
			this.albumInventory = albumInventory;
		}

		private boolean scrollBy(int deltaRows) {
			int newOffset = clampRowOffset(rowOffset, deltaRows, albumInventory.getContainerSize(), ALBUM_COLUMNS, VISIBLE_ROWS);
			if (newOffset == rowOffset)
				return false;
			rowOffset = newOffset;
			return true;
		}

		@Override
		public int getSlots() {
			return albumInventory.getContainerSize();
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			int realIndex = rowOffset * ALBUM_COLUMNS + slot;
			return realIndex < albumInventory.getContainerSize() ? albumInventory.getItem(realIndex) : ItemStack.EMPTY;
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
