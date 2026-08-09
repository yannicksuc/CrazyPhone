package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Callee-side screen shown while ringing, before the call is answered or declined - distinct from the
 * caller-side Calling screen (CrazyPhoneCallingScreenMenu), even though both represent "not yet connected". */
public class CrazyPhoneIncomingCallScreenMenu extends CrazyPhoneDefaultScreenMenu {
    private String conversationId = "";
    private UUID callId;
    private String displayTitle = "";
    /** The caller (and any other already-ringing participants in a group call) - see
     * ScreenMenuUtils#populateCallScreenBuffer, the same wire format CrazyPhoneInCallScreenMenu reads. */
    private List<CrazyPhoneInCallScreenMenu.CallParticipant> participants = List.of();

    public CrazyPhoneIncomingCallScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        super(ModMenus.CRAZY_PHONE_INCOMING_CALL_SCREEN.get(), id, inv, extraData);
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
