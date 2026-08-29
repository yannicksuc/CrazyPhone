package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import fr.lordfinn.crazyphone.init.ModMenus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A flat, un-paginated-on-the-server view of every photo a phone number owns (see
 * {@link fr.lordfinn.crazyphone.data.PhotoSavedData#getPhotoIdsForOwner}) - no real container slots (this
 * mirrors {@link CrazyPhoneMayorsCandidatesListMenu}'s "data-carrying, not slot-carrying" shape), just the
 * resolved id list read once from the open buffer. {@code conversationId} is empty unless this was opened
 * from inside a conversation to attach an existing photo, in which case the screen shows a Send button
 * targeting it instead of Delete/Take.
 */
public class CrazyPhoneMyPhotosScreenMenu extends CrazyPhoneDefaultScreenMenu {
    public final List<UUID> photoIds = new ArrayList<>();
    public final String conversationId;

    //? if >=1.20.5 {
    /*public CrazyPhoneMyPhotosScreenMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
    *///? } else {
    public CrazyPhoneMyPhotosScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
    //?}
        super(ModMenus.CRAZY_PHONE_MY_PHOTOS_SCREEN.get(), id, inv, extraData);
        conversationId = extraData.readUtf();
        int count = extraData.readVarInt();
        for (int i = 0; i < count; i++)
            photoIds.add(extraData.readUUID());
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        return ItemStack.EMPTY;
    }
}
