package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The group's own inventory-style "settings" screen: rename, change icon (by right-clicking an item in
 * the player inventory shown alongside - see CrazyPhoneGroupSettingsScreenScreen), and exclude members.
 * Player inventory slots are real vanilla slots (added via addPlayerInventorySlots) so the inventory stays
 * normally usable while this screen is open; the group itself has no container slots of its own - the
 * icon is chosen by reference (an item id), never actually moved out of the player's inventory.
 */
public class CrazyPhoneGroupSettingsScreenMenu extends CrazyPhoneDefaultScreenMenu {
    /** Player inventory sits to the right of the 122px-wide phone frame itself (see
     * CrazyPhoneGroupSettingsScreenScreen, which widens imageWidth accordingly), vertically centered on
     * the phone's own 195px height: 3 main rows (54px) + 4px gap + hotbar (18px) = 76px tall. */
    public static final int PLAYER_INV_X = 130;
    public static final int PLAYER_INV_Y = 60;

    public final static HashMap<String, Object> guistate = new HashMap<>();
    private String conversationId = "";
    private String groupName = "";
    private ItemStack groupIcon = ItemStack.EMPTY;
    private String groupAdmin = "";
    private final List<Contact> members = new ArrayList<>();
    /** The viewer's own contacts who aren't already in the group - shown as "invite to group" rows. */
    private final List<Contact> invitableContacts = new ArrayList<>();

    public CrazyPhoneGroupSettingsScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        super(ModMenus.CRAZY_PHONE_GROUP_SETTINGS_SCREEN.get(), id, inv, extraData);
        if (extraData.readableBytes() > 0) {
            conversationId = extraData.readUtf();
            groupName = extraData.readUtf();
            groupIcon = CrazyPhoneHelper.decodeItemStack(this.world, extraData.readNbt());
            groupAdmin = extraData.readUtf();
            readContacts(extraData, members);
            readContacts(extraData, invitableContacts);
        }
        addPlayerInventorySlots(inv, PLAYER_INV_X, PLAYER_INV_Y);
        ScreenMenuUtils.addDataToCurrentPage(this.entity, conversationId);
    }

    private static void readContacts(FriendlyByteBuf extraData, List<Contact> into) {
        int count = extraData.readInt();
        for (int i = 0; i < count; i++) {
            String number = extraData.readUtf();
            String name = extraData.readUtf();
            String uuid = extraData.readUtf();
            String skin = extraData.readUtf();
            Contact contact = new Contact(number, name);
            if (!uuid.isEmpty())
                contact.setUuid(uuid);
            if (!skin.isEmpty())
                contact.setSkin(skin);
            into.add(contact);
        }
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getGroupName() {
        return groupName;
    }

    public ItemStack getGroupIcon() {
        return groupIcon;
    }

    public String getGroupAdmin() {
        return groupAdmin;
    }

    public List<Contact> getMembers() {
        return members;
    }

    public List<Contact> getInvitableContacts() {
        return invitableContacts;
    }
}
