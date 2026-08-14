
package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.util.ArrayList;
import java.util.List;


/**
 * Favorites/contacts/groups are all rendered and hit-tested manually by CrazyPhoneContactsScreenScreen
 * (like the group icons always were) rather than through real vanilla Slots - the grid is scrollable and
 * arranged into sections, and Slot#x/y are final, so there's no way to reposition a real Slot once the
 * section layout or scroll offset changes.
 */
public class CrazyPhoneContactsScreenMenu extends CrazyPhoneDefaultScreenMenu {
    private static final String CONTACT_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWRmZmYxYjNjNWQ4NWZlM2NkZDU2NTY4NjliYWEwZWFkZTVlNTNhY2E5ZDU2MTQyNzY0OGNjNzJmNWUyNWE5In19fQ==";

    private final List<Contact> favorites = new ArrayList<>();
    private final List<Contact> contacts = new ArrayList<>();
    private final List<GroupInfo> groups = new ArrayList<>();

    public List<Contact> getFavorites() {
        return favorites;
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public List<GroupInfo> getGroups() {
        return groups;
    }

    /** A group conversation entry as shown in the Contacts screen: its conversation id, custom
     * name/icon (empty = unset, falls back to member names / cycling heads), the current admin's number,
     * and the OTHER participants (not the viewing player) - reused to build a cycling member-head icon
     * when no custom icon is set. */
    public record GroupInfo(String conversationId, String name, ItemStack icon, String admin, List<Contact> members) {}

    public CrazyPhoneContactsScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        super(ModMenus.CRAZY_PHONE_CONTACTS_SCREEN.get(), id, inv, extraData);
        parseContactsFromBuffer(extraData, favorites);
        parseContactsFromBuffer(extraData, contacts);
        parseGroupsFromBuffer(extraData);
    }

    private void parseContactsFromBuffer(FriendlyByteBuf extraData, List<Contact> into) {
        int contactsSize = extraData.readInt();
        for (int i = 0; i < contactsSize; i++) {
            String number = extraData.readUtf();
            String name = extraData.readUtf();
            String uuid = extraData.readUtf();
            String skin = extraData.readUtf();

            Contact contact = new Contact(number, name);
            if (!uuid.isEmpty()) {
                contact.setUuid(uuid);
            }
            if (!skin.isEmpty()) {
                contact.setSkin(skin);
            }

            into.add(contact);
        }
    }

    private void parseGroupsFromBuffer(FriendlyByteBuf extraData) {
        int groupsSize = extraData.readInt();
        for (int i = 0; i < groupsSize; i++) {
            String conversationId = extraData.readUtf();
            String groupName = extraData.readUtf();
            ItemStack icon = CrazyPhoneHelper.decodeItemStack(this.world, extraData.readNbt());
            String admin = extraData.readUtf();
            int membersSize = extraData.readInt();
            List<Contact> members = new ArrayList<>();
            for (int j = 0; j < membersSize; j++) {
                String number = extraData.readUtf();
                String name = extraData.readUtf();
                String uuid = extraData.readUtf();
                String skin = extraData.readUtf();

                Contact member = new Contact(number, name);
                if (!uuid.isEmpty()) {
                    member.setUuid(uuid);
                }
                if (!skin.isEmpty()) {
                    member.setSkin(skin);
                }
                members.add(member);
            }
            groups.add(new GroupInfo(conversationId, groupName, icon, admin, members));
        }
    }

    /** A fixed identity for the "add contact" head's synthetic GameProfile - a random UUID here would
     * make every single menu-open construct an unrecognized-to-the-client profile, forcing a fresh async
     * skin-texture resolution (and showing an unresolved placeholder) every time instead of letting the
     * client reuse whatever it already resolved for this same profile earlier in the session. */
    private static final UUID ADD_CONTACT_HEAD_PROFILE_ID = UUID.nameUUIDFromBytes("crazyphone:add_contact_head".getBytes());

    /** Icon for the "add contact" tile, shown as the first entry of the Contacts section. */
    public static ItemStack createAddContactHead() {
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        MutableComponent displayName = Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.tooltip_add_contact")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));

        PhoneTagAccess.setCustomName(head, displayName);

        GameProfile profile = new GameProfile(ADD_CONTACT_HEAD_PROFILE_ID, "CustomHead");
        PropertyMap properties = fr.lordfinn.crazyphone.utils.GameProfileCompat.properties(profile);
        properties.put("textures", new Property("textures", CONTACT_TEXTURE));
        PhoneTagAccess.setSkullOwner(head, profile);

        return head;
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
    public Map<Integer, Slot> get() {
        return customSlots; // always empty - nothing in this menu uses real Slots, see class javadoc
    }
}
