package fr.lordfinn.crazyphone.world.inventory;

import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import java.util.HashMap;
import java.util.Map;

public class CrazyPhonePictureFoldersScreenMenu extends CrazyPhoneDefaultScreenMenu {
	public static final HashMap<String, Object> guistate = new HashMap<>();
	private final Map<Integer, Slot> customSlots = new HashMap<>();
	private final int slotWidth = 18;
	private final int slotHeight = 18;


	public CrazyPhonePictureFoldersScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
		super(ModMenus.CRAZY_PHONE_PICTURE_FOLDERS_SCREEN.get(), id, inv, extraData);

		// Create 6x8 grid
		int startX = HEADER_CONTENT_START_X;
		int startY = HEADER_CONTENT_START_Y;

		int slotIndex = 0;
		for (int row = 0; row < 8; row++) {
			for (int col = 0; col < 6; col++) {
				int x = startX + col * slotWidth;
				int y = startY + row * slotHeight;
				final int index = slotIndex;

				Slot slot = new SlotItemHandler(this.internal, index, x, y) {
					@Override
					public boolean mayPickup(Player player) {
						return false;
					}

					@Override
					public boolean mayPlace(ItemStack stack) {
						return false;
					}

					@Override
					public void onTake(Player player, ItemStack stack) {
						super.onTake(player, stack);
						handleSlotClick(index);
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
	}

	public void handleSlotClick(int slotIndex) {
		if ((entity instanceof LivingEntity _livEnt ? _livEnt.getMainHandItem() : ItemStack.EMPTY).getCapability(Capabilities.ItemHandler.ITEM, null) instanceof IItemHandlerModifiable itemHandler) {
			if (entity instanceof ServerPlayer severplayer) {
				ScreenMenuUtils.openPhoneAlbumMenu(severplayer, InteractionHand.MAIN_HAND, slotIndex);
			}
		}
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
	}

	@Override
	public Map<Integer, Slot> get() {
		return customSlots;
	}
}
