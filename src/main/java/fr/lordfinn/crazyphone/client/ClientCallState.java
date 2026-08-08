package fr.lordfinn.crazyphone.client;

import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client-side cache of THIS player's own call state (a player can only ever be in one call at a time - see
 * {@link fr.lordfinn.crazyphone.voicechat.CallRegistry}'s single player-to-call mapping), fed by {@link
 * CrazyPhoneCallStateSyncPacket}. Read by the item's "in call" texture predicate and the conversation
 * screen's call-icon state; {@link #listener} lets whichever call screen is currently open react to state
 * changes (e.g. the Calling screen auto-transitioning to the InCall screen once answered), same pattern as
 * {@code ConversationClientCache}'s single listener slot.
 */
public final class ClientCallState {
    private static volatile State state = State.ENDED;
    private static volatile String conversationId = null;
    private static volatile UUID callId = null;
    /** Phone numbers belonging to the active call's conversation - not every phone the player happens to be
     * holding is necessarily on the call, since a player can carry several registered phones at once (see
     * CrazyPhoneItemProperties's "in_call" texture predicate, the reason this is tracked at all). */
    private static volatile List<String> callNumbers = List.of();
    private static volatile Consumer<CrazyPhoneCallStateSyncPacket> listener;

    private ClientCallState() {
    }

    public static void onPacket(CrazyPhoneCallStateSyncPacket packet) {
        state = packet.state();
        conversationId = packet.state() == State.ENDED ? null : packet.conversationId();
        callId = packet.state() == State.ENDED ? null : packet.callId();
        callNumbers = packet.callNumbers();
        Consumer<CrazyPhoneCallStateSyncPacket> currentListener = listener;
        if (currentListener != null)
            currentListener.accept(packet);
    }

    public static void setListener(Consumer<CrazyPhoneCallStateSyncPacket> l) {
        listener = l;
    }

    public static void clearListener(Consumer<CrazyPhoneCallStateSyncPacket> l) {
        if (listener == l)
            listener = null;
    }

    /** True while ringing, calling, or actively in a call - drives the item's 3rd texture state. */
    public static boolean isInCall() {
        return state != State.ENDED;
    }

    public static boolean isActiveCall() {
        return state == State.ACTIVE;
    }

    /** Whether {@code number} is on a call currently in exactly {@code targetState} - the check the item's
     * per-state textures (calling/called_in/in_call) and anything else keying off a specific held phone
     * (rather than just "is this player in a call at all") use. CALLING = this player is the one waiting for
     * an answer; RINGING = this player is themselves being called and hasn't answered/declined yet; ACTIVE =
     * actually connected. */
    public static boolean numberHasState(String number, State targetState) {
        return state == targetState && callNumbers.contains(number);
    }

    public static State getState() {
        return state;
    }

    /** Non-null only while {@link #isInCall()} - the conversation whose call this is. */
    public static String getConversationId() {
        return conversationId;
    }

    /** Non-null only while {@link #isInCall()} - lets a chat-feed call-log entry (see MessageWidget) tell
     * whether it's tracking THIS player's own currently-active call, to know when to freeze its live timer. */
    public static UUID getCallId() {
        return callId;
    }
}
