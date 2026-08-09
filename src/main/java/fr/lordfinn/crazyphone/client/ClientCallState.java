package fr.lordfinn.crazyphone.client;

import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    /** Every conversation id that currently has SOME active call, whether or not the local player is on it
     * - fed by {@link fr.lordfinn.crazyphone.network.ConversationCallActivitySyncPacket}, sent to every
     * conversation member (not just participants/ringers). Lets the contacts-list badge and the conversation
     * screen's call icon show "there's a call here you could rejoin" for a group call the player left, or
     * was never on. A ConcurrentHashMap-backed set since it's written from the network thread's enqueued
     * work and read from the render thread. */
    private static final Set<String> conversationsWithActiveCalls = ConcurrentHashMap.newKeySet();
    /** Latest live pose state per participant, fed by
     * {@link fr.lordfinn.crazyphone.network.CallParticipantHeadRotationSyncPacket} several times a second -
     * lets the InCall screen's bust portraits mirror what the real player is actually doing (sneaking,
     * swimming, running...) in real time. Never actively cleared: a stale entry for a UUID nothing renders
     * anymore is harmless, and the set of UUIDs in any one call is always small. */
    private static final java.util.Map<UUID, LiveState> liveStates = new ConcurrentHashMap<>();

    /** @param headYawDelta head-vs-body yaw deviation in degrees, to reapply on top of the bust's fixed
     *                      camera-facing body (see CrazyPhoneInCallScreenScreen.renderBust)
     * @param poseOrdinal   {@link net.minecraft.world.entity.Pose#ordinal()}
     * @param walkAnimationSpeed per-tick input for {@code LivingEntity.walkAnimation.update(speed, 0.4F)},
     *                           mirroring vanilla's own {@code min(distance * 4, 1)} formula */
    public record LiveState(float headYawDelta, float pitch, int poseOrdinal, boolean crouching,
                             boolean sprinting, boolean swimming, float walkAnimationSpeed) {
    }

    private ClientCallState() {
    }

    public static void setLiveState(UUID playerId, float headYawDelta, float pitch, int poseOrdinal,
                                     boolean crouching, boolean sprinting, boolean swimming, float walkAnimationSpeed) {
        liveStates.put(playerId, new LiveState(headYawDelta, pitch, poseOrdinal, crouching, sprinting, swimming, walkAnimationSpeed));
    }

    /** This participant's latest live pose state, or {@code null} if nothing's been received yet (e.g. the
     * first render frame or 2 right after they join). */
    public static LiveState getLiveState(UUID playerId) {
        return liveStates.get(playerId);
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

    public static void onConversationActivityChanged(String conversationId, boolean active) {
        if (active)
            conversationsWithActiveCalls.add(conversationId);
        else
            conversationsWithActiveCalls.remove(conversationId);
    }

    /** True when {@code conversationId} has an active call happening right now that ISN'T the local
     * player's own currently-active call (that case already has its own green "reopen" state - see
     * {@link #isActiveCall()}/{@link #getConversationId()}) - the "someone else is still on this call, or I
     * left it, but I could rejoin" state the contacts-list badge and conversation call icon render yellow. */
    public static boolean hasJoinableCallElsewhere(String conversationId) {
        if (!conversationsWithActiveCalls.contains(conversationId))
            return false;
        return !(isActiveCall() && conversationId.equals(getConversationId()));
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
