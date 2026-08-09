package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Caller-side screen shown between starting a call and it being answered. */
public class CrazyPhoneCallingScreenMenu extends CrazyPhoneDefaultScreenMenu {
    private String conversationId = "";
    private UUID callId;
    private String displayTitle = "";
    /** The callee(s) still being rung - see ScreenMenuUtils#openCallScreenForPlayer, the same wire format
     * CrazyPhoneInCallScreenMenu reads. */
    private List<CrazyPhoneInCallScreenMenu.CallParticipant> participants = List.of();

    public CrazyPhoneCallingScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        super(ModMenus.CRAZY_PHONE_CALLING_SCREEN.get(), id, inv, extraData);
        if (extraData.readableBytes() > 0) {
            conversationId = extraData.readUtf();
            callId = extraData.readUUID();
            displayTitle = extraData.readUtf();
            int count = extraData.readVarInt();
            List<CrazyPhoneInCallScreenMenu.CallParticipant> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
                list.add(new CrazyPhoneInCallScreenMenu.CallParticipant(extraData.readUUID(), extraData.readUtf(),
                        CrazyPhoneHelper.decodeItemStack(this.world, extraData.readNbt()),
                        CrazyPhoneHelper.decodeItemStack(this.world, extraData.readNbt()),
                        CrazyPhoneHelper.decodeItemStack(this.world, extraData.readNbt()),
                        CrazyPhoneHelper.decodeItemStack(this.world, extraData.readNbt())));
            participants = list;
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

    public List<CrazyPhoneInCallScreenMenu.CallParticipant> getParticipants() {
        return participants;
    }
}
