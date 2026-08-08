package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import java.util.UUID;

/** Active-call screen - shown once a call is answered, and again any time this player reopens the phone
 * while in that call (including bypassing the lock screen - see CrazyPhoneOnUseProcedure). */
public class CrazyPhoneInCallScreenMenu extends CrazyPhoneDefaultScreenMenu {
    private String conversationId = "";
    private UUID callId;
    private String displayTitle = "";

    public CrazyPhoneInCallScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        super(ModMenus.CRAZY_PHONE_IN_CALL_SCREEN.get(), id, inv, extraData);
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
