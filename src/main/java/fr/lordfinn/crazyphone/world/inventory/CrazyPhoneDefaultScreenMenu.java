
package fr.lordfinn.crazyphone.world.inventory;

//? if neoforge {
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
//?}

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.function.Supplier;
import java.util.Map;
import java.util.HashMap;

abstract public class CrazyPhoneDefaultScreenMenu extends AbstractContainerMenu implements Supplier<Map<Integer, Slot>> {
	/** Every screen's slot grid starts here (in phone-background-relative pixels) to leave room for the
	 * page-title header drawn by CrazyPhoneDefaultScreenScreen#renderHeader - must match that class's
	 * HEADER_HEIGHT constant. Menus don't have access to the screen class, hence the duplicate constant. */
	public static final int HEADER_CONTENT_START_Y = 27;
	/** Item grids start their first column here so they end up symmetrically padded (matches the phone
	 * background's width) and their first item lines up with the header's icon (CrazyPhoneDefaultScreenScreen#renderHeader). */
	public static final int HEADER_CONTENT_START_X = 8;
	/** Standard slot cell pitch (16px item + 1px gap each side) shared by every item grid in the phone UI. */
	public static final int SLOT_PITCH = 18;
	/** Standard column count shared by every item grid in the phone UI. */
	public static final int GRID_COLUMNS = 6;
	public final static HashMap<String, Object> guistate = new HashMap<>();
	public final Level world;
	public final Player entity;
	public int x, y, z;
	protected ContainerLevelAccess access = ContainerLevelAccess.NULL;
	//? if neoforge {
	public IItemHandler internal;
	//?}
	//? if fabric {
	/*// TODO(#163): real Fabric ItemApiLookup-backed item capability - deferred until the two menus that
	// actually use this (CrazyPhonePicturesScreenMenu/CrazyPhonePictureFoldersScreenMenu, both still
	// Camera-mod-coupled - see task #165) are ported. Every other menu subclass never touches this field.
	public Object internal;
	*///?}
	protected final Map<Integer, Slot> customSlots = new HashMap<>();
	protected boolean bound = false;
	protected Supplier<Boolean> boundItemMatcher = null;
	protected Entity boundEntity = null;
	protected BlockEntity boundBlockEntity = null;
	/** Captured once at construction (not re-derived later) so removed() flips the tag back off on the exact
	 * same item instance, even if the player has since switched what's in their main hand - see
	 * CrazyPhoneHelper#setPhoneScreenOpen for why this is written into the item's own data rather than any
	 * client-only field (bystanders need to see this too, not just the phone's own owner). */
	private ItemStack ownerPhoneStack = ItemStack.EMPTY;

	public CrazyPhoneDefaultScreenMenu(MenuType<?> type, int id, Inventory inv, FriendlyByteBuf extraData) {
		super(type, id);
		this.entity = inv.player;
		this.world = inv.player.level();
		//? if neoforge {
		this.internal = new ItemStackHandler(0);
		//?}
		//? if fabric {
		/*this.internal = null;
		*///?}
		setCurrentPageHistory();
		if (this.entity instanceof ServerPlayer serverPlayer) {
			ItemStack held = CrazyPhoneHelper.getMainHandItemOrEmpty(this.entity);
			if (held.getItem() == ModItems.CRAZY_PHONE.get()) {
				ownerPhoneStack = held;
				CrazyPhoneHelper.setPhoneScreenOpen(ownerPhoneStack, true);
				// This menu is a fully custom phone UI that never wraps the player's own Inventory slots
				// (see CrazyPhoneHelper#sendSelectedAlbumSlotsFromHeldPhone for the same issue previously
				// found on album slots), so once it's open, vanilla's per-tick slot-change broadcast stops
				// looking at the hotbar - the mutation above would otherwise never reach the client.
				// broadcastChanges (not broadcastFullState) - a full-content resync packet replaces every
				// slot's ItemStack instance at once, which makes the client replay the item re-equip/bob
				// animation on the held phone even though nothing about the held slot itself moved.
				serverPlayer.inventoryMenu.broadcastChanges();
			}
		}
		BlockPos pos = null;
		if (extraData != null) {
			pos = extraData.readBlockPos();
			this.x = pos.getX();
			this.y = pos.getY();
			this.z = pos.getZ();
			access = ContainerLevelAccess.create(world, pos);
		}
		if (pos != null) {
			if (extraData.readableBytes() >= 1) {
				byte hand = extraData.readByte();
				ItemStack itemstack = hand == 0 ? this.entity.getMainHandItem() : this.entity.getOffhandItem();
				this.boundItemMatcher = () -> itemstack == (hand == 0 ? this.entity.getMainHandItem() : this.entity.getOffhandItem());
				//? if neoforge && >=1.21.10 {
				/*Object cap = itemstack.getCapability(Capabilities.Item.ITEM, null);
				if (cap instanceof IItemHandler h) {
					this.internal = h;
					this.bound = true;
				}
				*///?}
				//? if neoforge && <1.21.10 {
				IItemHandler cap = itemstack.getCapability(Capabilities.ItemHandler.ITEM);
				if (cap != null) {
					this.internal = cap;
					this.bound = true;
				}
				//?}
				//? if fabric {
				/*// TODO(#163): real Fabric ItemApiLookup binding - see the internal field's own comment above.
				*///?}
			}
		}
	}
	protected void setCurrentPageHistory() {
		setCurrentPageHistoryData( null);
	}
	protected void setCurrentPageHistoryData(String screenData) {
		ResourceLocation registryName = BuiltInRegistries.MENU.getKey(this.getType());
		String menuName = registryName != null ? registryName.toString() : "unknown";
		setCurrentPageHistory(menuName, screenData);
	}
	private void setCurrentPageHistory(String screenId, String screenData) {
		ScreenMenuUtils.pushScreen(this.entity, screenId, screenData);
	}

	protected void addPlayerInventorySlots(Inventory inv, int left, int top) {
		for (int row = 0; row < 3; ++row)
			for (int col = 0; col < 9; ++col)
				this.addSlot(new Slot(inv, col + (row + 1) * 9, left + col * 18, top + row * 18));

		for (int col = 0; col < 9; ++col)
			this.addSlot(new Slot(inv, col, left + col * 18, top + 58));
	}

	@Override
	public boolean stillValid(Player player) {
		if (this.bound) {
			if (this.boundItemMatcher != null)
				return this.boundItemMatcher.get();
			else if (this.boundBlockEntity != null)
				return AbstractContainerMenu.stillValid(this.access, player, this.boundBlockEntity.getBlockState().getBlock());
			else if (this.boundEntity != null)
				return this.boundEntity.isAlive();
		}
		return true;
	}

	@Override
	public ItemStack quickMoveStack(Player playerIn, int index) {
		ItemStack itemstack = ItemStack.EMPTY;
		Slot slot = (Slot) this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack itemstack1 = slot.getItem();
			itemstack = itemstack1.copy();
			if (index < 0) {
				if (!this.moveItemStackTo(itemstack1, 0, this.slots.size(), true))
					return ItemStack.EMPTY;
				slot.onQuickCraft(itemstack1, itemstack);
			} else if (!this.moveItemStackTo(itemstack1, 0, 0, false)) {
				if (index < 0 + 27) {
					if (!this.moveItemStackTo(itemstack1, 0 + 27, this.slots.size(), true))
						return ItemStack.EMPTY;
				} else {
					if (!this.moveItemStackTo(itemstack1, 0, 0 + 27, false))
						return ItemStack.EMPTY;
				}
				return ItemStack.EMPTY;
			}
			if (itemstack1.getCount() == 0)
				slot.set(ItemStack.EMPTY);
			else
				slot.setChanged();
			if (itemstack1.getCount() == itemstack.getCount())
				return ItemStack.EMPTY;
			slot.onTake(playerIn, itemstack1);
		}
		return itemstack;
	}

	@Override
	protected boolean moveItemStackTo(ItemStack p_38904_, int p_38905_, int p_38906_, boolean p_38907_) {
		boolean flag = false;
		int i = p_38905_;
		if (p_38907_) {
			i = p_38906_ - 1;
		}
		if (p_38904_.isStackable()) {
			while (!p_38904_.isEmpty() && (p_38907_ ? i >= p_38905_ : i < p_38906_)) {
				Slot slot = this.slots.get(i);
				ItemStack itemstack = slot.getItem();
				//? if >=1.20.5 {
				/*if (slot.mayPlace(itemstack) && !itemstack.isEmpty() && ItemStack.isSameItemSameComponents(p_38904_, itemstack)) {
				*///? } else {
				if (slot.mayPlace(itemstack) && !itemstack.isEmpty() && ItemStack.isSameItemSameTags(p_38904_, itemstack)) {
				//?}
					int j = itemstack.getCount() + p_38904_.getCount();
					int k = slot.getMaxStackSize(itemstack);
					if (j <= k) {
						p_38904_.setCount(0);
						itemstack.setCount(j);
						slot.set(itemstack);
						flag = true;
					} else if (itemstack.getCount() < k) {
						p_38904_.shrink(k - itemstack.getCount());
						itemstack.setCount(k);
						slot.set(itemstack);
						flag = true;
					}
				}
				if (p_38907_) {
					i--;
				} else {
					i++;
				}
			}
		}
		if (!p_38904_.isEmpty()) {
			if (p_38907_) {
				i = p_38906_ - 1;
			} else {
				i = p_38905_;
			}
			while (p_38907_ ? i >= p_38905_ : i < p_38906_) {
				Slot slot1 = this.slots.get(i);
				ItemStack itemstack1 = slot1.getItem();
				if (itemstack1.isEmpty() && slot1.mayPlace(p_38904_)) {
					int l = slot1.getMaxStackSize(p_38904_);
					slot1.setByPlayer(p_38904_.split(Math.min(p_38904_.getCount(), l)));
					slot1.setChanged();
					flag = true;
					break;
				}
				if (p_38907_) {
					i--;
				} else {
					i++;
				}
			}
		}
		return flag;
	}

	@Override
	public void removed(Player playerIn) {
		super.removed(playerIn);
		if (!ownerPhoneStack.isEmpty()) {
			CrazyPhoneHelper.setPhoneScreenOpen(ownerPhoneStack, false);
			if (playerIn instanceof ServerPlayer serverPlayer)
				serverPlayer.inventoryMenu.broadcastChanges();
		}
		//? if neoforge {
		if (!bound && playerIn instanceof ServerPlayer serverPlayer) {
			if (!serverPlayer.isAlive() || serverPlayer.hasDisconnected()) {
				for (int j = 0; j < internal.getSlots(); ++j) {
					playerIn.drop(internal.getStackInSlot(j), false);
					if (internal instanceof IItemHandlerModifiable ihm)
						ihm.setStackInSlot(j, ItemStack.EMPTY);
				}
			} else {
				for (int i = 0; i < internal.getSlots(); ++i) {
					playerIn.getInventory().placeItemBackInInventory(internal.getStackInSlot(i));
					if (internal instanceof IItemHandlerModifiable ihm)
						ihm.setStackInSlot(i, ItemStack.EMPTY);
				}
			}
		}
		//?}
	}

	public Map<Integer, Slot> get() {
		return customSlots;
	}
}
