package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;

import fr.lordfinn.crazyphone.init.ModMenus;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Active-call screen - shown once a call is answered, and again any time this player reopens the phone
 * while in that call (including bypassing the lock screen - see CrazyPhoneOnUseProcedure). */
public class CrazyPhoneInCallScreenMenu extends CrazyPhoneDefaultScreenMenu {
    /** One other participant's identity, as seen by the viewer - never includes the viewer themselves (see
     * ScreenMenuUtils#populateCallScreenBuffer). {@code name} is empty if the server couldn't resolve them
     * (shouldn't normally happen for someone actually in CallSession#participants). */
    public record CallParticipant(UUID id, String name) {
    }

    private String conversationId = "";
    private UUID callId;
    private String displayTitle = "";
    private List<CallParticipant> participants = List.of();

    public CrazyPhoneInCallScreenMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        super(ModMenus.CRAZY_PHONE_IN_CALL_SCREEN.get(), id, inv, extraData);
        if (extraData.readableBytes() > 0) {
            conversationId = extraData.readUtf();
            callId = extraData.readUUID();
            displayTitle = extraData.readUtf();
            int count = extraData.readVarInt();
            List<CallParticipant> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
                list.add(new CallParticipant(extraData.readUUID(), extraData.readUtf()));
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

    public List<CallParticipant> getParticipants() {
        return participants;
    }
}
