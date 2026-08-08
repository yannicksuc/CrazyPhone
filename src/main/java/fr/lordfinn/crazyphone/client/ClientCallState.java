package fr.lordfinn.crazyphone.client;

import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;

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
    private static volatile Consumer<CrazyPhoneCallStateSyncPacket> listener;

    private ClientCallState() {
    }

    public static void onPacket(CrazyPhoneCallStateSyncPacket packet) {
        state = packet.state();
        conversationId = packet.state() == State.ENDED ? null : packet.conversationId();
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

    public static State getState() {
        return state;
    }

    /** Non-null only while {@link #isInCall()} - the conversation whose call this is. */
    public static String getConversationId() {
        return conversationId;
    }
}
