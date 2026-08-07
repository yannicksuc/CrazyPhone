package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
    public final static HashMap<String, Object> guistate = new HashMap<>();
    private List<Contact> contacts = new ArrayList<>();
    private String conversationId;

    public CrazyPhoneConversationMenu(int id, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.CRAZY_PHONE_CONVERSATION.get(), id, inv, extraData);
        if (extraData.readableBytes() > 0)
            conversationId = extraData.readUtf();
        contacts = CrazyPhoneHelper.getContactsFromBuf(extraData);
        ScreenMenuUtils.addDataToCurrentPage(entity, conversationId);
        //ScreenMenuUtils.debugPrintScreenHistory(entity);
    }

    public List<Contact> getContacts() {
        return contacts;
    }

    public String getConversationId() {
        return conversationId;
    }
}
