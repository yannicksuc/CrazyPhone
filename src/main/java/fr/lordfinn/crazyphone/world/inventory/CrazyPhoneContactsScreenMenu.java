
package fr.lordfinn.crazyphone.world.inventory;

import net.neoforged.neoforge.items.SlotItemHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class CrazyPhoneContactsScreenMenu extends CrazyPhoneDefaultScreenMenu {
    private static final String CONTACT_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWRmZmYxYjNjNWQ4NWZlM2NkZDU2NTY4NjliYWEwZWFkZTVlNTNhY2E5ZDU2MTQyNzY0OGNjNzJmNWUyNWE5In19fQ==";

    private final Map<Integer, Slot> customSlots = new HashMap<>();
    private final int slotWidth = 18;
    private final int slotHeight = 18;
    private final List<Contact> contacts = new ArrayList<>();

    public List<Contact> getContacts() {
        return contacts;
    }

    public CrazyPhoneContactsScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        super(ModMenus.CRAZY_PHONE_CONTACTS_SCREEN.get(), id, inv, extraData);
        parseContactsFromBuffer(extraData);
        updateInventorySlots();
    }

    private void parseContactsFromBuffer(FriendlyByteBuf extraData) {
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

            contacts.add(contact);
        }
    }

    private void updateInventorySlots() {
        clearSlots();
        addContactHeads();
    }

    private void clearSlots() {
        for (int i = 0; i < 48; i++) {
            this.internal.extractItem(i + 48, 64, false); // Clear the slot
        }
        customSlots.clear();
    }

    private void addContactHeads() {
        int startX = HEADER_CONTENT_START_X;
        int startY = HEADER_CONTENT_START_Y;
        int slotIndex = 0;

        for (int i = 0; i < contacts.size(); i++) {
            int x = startX + (i % 6) * slotWidth;
            int y = startY + (i / 6) * slotHeight;

			Slot slot = createSlot(slotIndex + 48, x, y, contacts.get(i));
            this.customSlots.put(slotIndex, this.addSlot(slot));

			ItemStack item = CrazyPhoneHelper.createContactHead(contacts.get(i));
			this.internal.insertItem(slotIndex + 48, item, false);
            slotIndex++;
        }
    }

    private Slot createSlot(int index, int x, int y, Contact contact) {
        return new SlotItemHandler(this.internal, index, x, y) {
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
                //handleSlotClick(getSlotIndex());
            }

            @Override
            public void set(ItemStack stack) {
                // Prevent external stack setting
            }
        };
    }

    /** Icon for the "add contact" button, now relocated to the top-right of the header banner (see
     * CrazyPhoneContactsScreenScreen) instead of being a slot among the contact heads. */
    public static ItemStack createAddContactHead() {
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        MutableComponent displayName = Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.tooltip_add_contact")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));

        head.set(DataComponents.CUSTOM_NAME, displayName);

        GameProfile profile = new GameProfile(UUID.randomUUID(), "CustomHead");
        PropertyMap properties = profile.getProperties();
        properties.put("textures", new Property("textures", CONTACT_TEXTURE));
        ResolvableProfile resolvableProfile = new ResolvableProfile(profile);
        head.set(DataComponents.PROFILE, resolvableProfile);

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
    public void removed(Player playerIn) {
        super.removed(playerIn);
    }

    @Override
    public Map<Integer, Slot> get() {
        return customSlots;
    }

    public void addContact(Contact contact) {
        contacts.add(contact);
        updateInventorySlots();
    }

    public void removeContact(int index) {
        if (index >= 0 && index < contacts.size()) {
            contacts.remove(index);
            updateInventorySlots();
        }
    }
}

