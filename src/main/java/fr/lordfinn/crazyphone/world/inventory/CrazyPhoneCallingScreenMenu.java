package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import java.util.UUID;

/** Caller-side screen shown between starting a call and it being answered. */
public class CrazyPhoneCallingScreenMenu extends CrazyPhoneDefaultScreenMenu {
    private String conversationId = "";
    private UUID callId;
    private String displayTitle = "";

    public CrazyPhoneCallingScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        super(ModMenus.CRAZY_PHONE_CALLING_SCREEN.get(), id, inv, extraData);
        if (extraData.readableBytes() > 0) {
            conversationId = extraData.readUtf();
            callId = extraData.readUUID();
            displayTitle = extraData.readUtf();
        }
        ScreenMenuUtils.addDataToCurrentPage(this.entity, conversationId);
    }

    public String getConversationId() {
        return conversationId;
    }

    public UUID getCallId() {
        return callId;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }
}
