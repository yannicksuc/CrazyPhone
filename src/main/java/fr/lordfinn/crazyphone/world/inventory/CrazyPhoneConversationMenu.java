package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Unlike the old MCreator version, this menu's open-buffer no longer carries the conversation's full
 * message history (see fr.lordfinn.crazyphone.utils.ScreenMenuUtils#populateBufferWithConversationData -
 * that unbounded payload was the root cause of the old mod's login crash). The buffer now only contains:
 * block pos, hand byte (read by the superclass), conversationId, then participant count + per-participant
 * name/number/uuid/skin - exactly what writeParticipantsToBuffer writes. Message history is instead fetched
 * page-by-page by the screen via fr.lordfinn.crazyphone.network.ConversationRequestPacket and cached in
 * fr.lordfinn.crazyphone.client.ConversationClientCache.
 */
public class CrazyPhoneConversationMenu extends CrazyPhoneDefaultScreenMenu {
    /** A gear/cog skin, used for the group-settings header icon (see CrazyPhoneConversationScreen). */
    private static final String COG_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWU1ZWRmZTkwMTU2Y2U5YjViNmI4MDc5M2RjMmNiZmU4NTBkZGVjODU2Yzk5ZWViZGUzMTc3NWNjZTk1NjA0MSJ9fX0=";
    public final static HashMap<String, Object> guistate = new HashMap<>();
    private List<Contact> contacts = new ArrayList<>();
    private String conversationId;
    private boolean isGroup;
    private String groupName = "";
    private ItemStack groupIcon = ItemStack.EMPTY;
    private String groupAdmin = "";

    //? if >=1.20.5 {
    /*public CrazyPhoneConversationMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
    *///? } else {
    public CrazyPhoneConversationMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
    //?}
        super(ModMenus.CRAZY_PHONE_CONVERSATION.get(), id, inv, extraData);
        if (extraData.readableBytes() > 0)
            conversationId = extraData.readUtf();
        contacts = CrazyPhoneHelper.getContactsFromBuf(extraData);
        if (extraData.readableBytes() > 0) {
            isGroup = extraData.readBoolean();
            groupName = extraData.readUtf();
            groupIcon = CrazyPhoneHelper.decodeItemStack(this.world, extraData.readNbt());
            groupAdmin = extraData.readUtf();
        }
        ScreenMenuUtils.addDataToCurrentPage(entity, conversationId);
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public String getConversationId() {
        return conversationId;
    }

    public boolean isGroup() {
        return isGroup;
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

    /** Icon for the header's group-settings button - a plain generic head (gear skin), not tied to any
     * particular contact, same pattern as {@link CrazyPhoneContactsScreenMenu#createAddContactHead()}. */
    public static ItemStack createGroupSettingsIcon() {
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
        MutableComponent displayName = Component.translatable("gui.crazyphone.crazy_phone_conversation.tooltip_group_settings")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));
        PhoneTagAccess.setCustomName(head, displayName);

        GameProfile profile = new GameProfile(UUID.randomUUID(), "CustomHead");
        PropertyMap properties = profile.getProperties();
        properties.put("textures", new Property("textures", COG_TEXTURE));
        PhoneTagAccess.setSkullOwner(head, profile);

        return head;
    }
}
