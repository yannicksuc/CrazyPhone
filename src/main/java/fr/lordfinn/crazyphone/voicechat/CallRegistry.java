package fr.lordfinn.crazyphone.voicechat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneIncomingCallNotificationPacket;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side, in-memory only (never {@code SavedData}, never a player attachment) - calls are session-only
 * and must not persist across a restart. Owns the Simple Voice Chat group lifecycle for every active call
 * via {@link SvcCallBridge}, so this is the single source of truth {@link
 * fr.lordfinn.crazyphone.procedures.CrazyPhoneOnUseProcedure}'s lock-bypass hook and the drop/inventory-move
 * termination sweep (added in a later phase) both query.
 */
public final class CallRegistry {

    public static final class CallSession {
        public final UUID callId;
        public final String conversationId;
        public final UUID initiator;
        /** Answered and currently connected to the call's SVC group. */
        public final Set<UUID> participants = new LinkedHashSet<>();
        /** Invited but not yet answered. */
        public final Set<UUID> ringing = new LinkedHashSet<>();
        /** Game time (server overworld) at which participants dropped to exactly 1, or -1 if not applicable
         * right now - the 5s alone-in-call auto-kick (wired up in a later phase) reads this. */
        public long soleParticipantSinceGameTime = -1;
        /** Game time the call was created - the ring-timeout sweep (CallTerminationListener) uses this to
         * expire callees who never answered, distinct from the alone-in-call kick above (which only applies
         * once a call has actually connected - see the ringing-not-alone fix in that sweep). */
        public long startedAtGameTime = -1;
        /** Real (wall-clock) epoch millis when the call actually connected (2nd participant joined) - -1 if
         * it never did. A call that was only ever ringing/calling and got cancelled or missed has nothing to
         * log; only a call someone actually answered gets a chat entry (see CrazyPhoneHelper#addCallMessage
         * / #finalizeCallMessage, keyed by this session's own callId - one call, one chat entry). */
        public long connectedAtEpochMillis = -1;

        private CallSession(UUID callId, String conversationId, UUID initiator) {
            this.callId = callId;
            this.conversationId = conversationId;
            this.initiator = initiator;
        }
    }

    private static final Map<UUID, CallSession> ACTIVE_CALLS = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_TO_CALL = new HashMap<>();

    private CallRegistry() {
    }

    public static Optional<CallSession> getSessionFor(UUID playerId) {
        UUID callId = PLAYER_TO_CALL.get(playerId);
        return callId == null ? Optional.empty() : Optional.ofNullable(ACTIVE_CALLS.get(callId));
    }

    /** Every player currently tied to a call (participant or still ringing) - read by the periodic
     * drop/inventory-move sweep, which only needs to check players who are actually in a call. */
    public static Set<UUID> getAllPlayersInCalls() {
        return new HashSet<>(PLAYER_TO_CALL.keySet());
    }

    /** Snapshot of every active call session - read by the periodic alone-in-call kick sweep. */
    public static List<CallSession> getActiveSessions() {
        return new java.util.ArrayList<>(ACTIVE_CALLS.values());
    }

    public static boolean isRinging(UUID playerId) {
        return getSessionFor(playerId).map(s -> s.ringing.contains(playerId)).orElse(false);
    }

    public static boolean isParticipant(UUID playerId) {
        return getSessionFor(playerId).map(s -> s.participants.contains(playerId)).orElse(false);
    }

    /**
     * Starts a call for {@code conversationId}: the initiator joins immediately, every callee starts
     * ringing. Callees who aren't reachable (SVC not installed/connected) are silently skipped - they just
     * never ring, exactly like calling a phone number that's turned off.
     */
    public static CallSession startCall(String conversationId, ServerPlayer initiator, List<ServerPlayer> callees) {
        // Already on a call (as caller or participant elsewhere) - do not stack a second one.
        if (getSessionFor(initiator.getUUID()).isPresent())
            return null;

        // Someone else in this conversation already has a call going - join that one instead of spinning up
        // a second, independent SVC group for the same conversation (which would split everyone's audio
        // across two disconnected calls rather than one shared one).
        CallSession existing = getSessionForConversation(conversationId);
        if (existing != null) {
            joinExistingCall(existing, initiator);
            return existing;
        }

        UUID callId = SvcCallBridge.createCallGroup("crazyphone-call-" + conversationId);
        if (callId == null)
            return null;

        CallSession session = new CallSession(callId, conversationId, initiator.getUUID());
        session.startedAtGameTime = initiator.getServer() == null ? 0 : initiator.getServer().overworld().getGameTime();
        session.participants.add(initiator.getUUID());
        ACTIVE_CALLS.put(callId, session);
        PLAYER_TO_CALL.put(initiator.getUUID(), callId);
        SvcCallBridge.joinGroup(initiator, callId);
        sendStateSync(initiator, session, CrazyPhoneCallStateSyncPacket.State.CALLING);

        for (ServerPlayer callee : callees) {
            if (callee.getUUID().equals(initiator.getUUID()) || !SvcCallBridge.isCallable(callee))
                continue;
            session.ringing.add(callee.getUUID());
            PLAYER_TO_CALL.put(callee.getUUID(), callId);
            sendStateSync(callee, session, CrazyPhoneCallStateSyncPacket.State.RINGING);
            PacketDistributor.sendToPlayer(callee,
                    new CrazyPhoneIncomingCallNotificationPacket(conversationId, initiator.getGameProfile().getName(), callId));
        }
        return session;
    }

    private static CallSession getSessionForConversation(String conversationId) {
        for (CallSession session : ACTIVE_CALLS.values()) {
            if (session.conversationId.equals(conversationId))
                return session;
        }
        return null;
    }

    /** Joining an already-active call for this conversation, triggered by the "start call" action from
     * someone who wasn't on it yet - becomes a participant immediately (same as answering, not ringing,
     * since this is an explicit join rather than an incoming ring) and everyone already on the call is
     * re-synced to ACTIVE so their participant list picks the new joiner up. */
    private static void joinExistingCall(CallSession session, ServerPlayer joiner) {
        session.participants.add(joiner.getUUID());
        session.ringing.remove(joiner.getUUID());
        PLAYER_TO_CALL.put(joiner.getUUID(), session.callId);
        session.soleParticipantSinceGameTime = -1;
        SvcCallBridge.joinGroup(joiner, session.callId);
        markConnectedIfFirstTime(session, joiner);
        for (UUID participantId : new HashSet<>(session.participants)) {
            ServerPlayer participant = findPlayer(joiner, participantId);
            if (participant != null)
                sendStateSync(participant, session, CrazyPhoneCallStateSyncPacket.State.ACTIVE);
        }
    }

    /** Moves a ringing callee into the active call - called when they use the phone while being called. */
    public static void answer(ServerPlayer player) {
        CallSession session = getSessionFor(player.getUUID()).orElse(null);
        if (session == null || !session.ringing.remove(player.getUUID()))
            return;
        session.participants.add(player.getUUID());
        session.soleParticipantSinceGameTime = -1;
        SvcCallBridge.joinGroup(player, session.callId);
        markConnectedIfFirstTime(session, player);
        for (UUID participantId : new HashSet<>(session.participants)) {
            ServerPlayer participant = findPlayer(player, participantId);
            if (participant != null)
                sendStateSync(participant, session, CrazyPhoneCallStateSyncPacket.State.ACTIVE);
        }
    }

    /** Voluntary hangup/decline, or a forced removal (drop, moved to another inventory, disconnect). */
    public static void leave(ServerPlayer player) {
        CallSession session = getSessionFor(player.getUUID()).orElse(null);
        if (session == null)
            return;
        session.participants.remove(player.getUUID());
        session.ringing.remove(player.getUUID());
        PLAYER_TO_CALL.remove(player.getUUID());
        SvcCallBridge.leaveGroup(player);
        sendStateSync(player, session, CrazyPhoneCallStateSyncPacket.State.ENDED);

        // Nobody actually connected left - whether that's the last real participant hanging up, or the
        // initiator cancelling before anyone still ringing had a chance to answer, either way there's no
        // call left to have. Checking participants alone (not also requiring ringing to be empty) matters:
        // otherwise a cancelled call with people still ringing never tears down - it sits in ACTIVE_CALLS
        // forever, and a still-ringing callee who later answers gets added as the sole "participant" of a
        // call nobody else is on.
        if (session.participants.isEmpty()) {
            endCall(session, player.getServer(), player.getUUID());
            return;
        }
        if (session.participants.size() == 1 && session.ringing.isEmpty()) {
            session.soleParticipantSinceGameTime = 0; // marked "pending" - the tick sweep stamps the real game time and plays the disconnect sound once
        }
    }

    /** Full teardown - every remaining participant/ringer is dropped, notified (ENDED) unless their id is
     * {@code alreadyNotifiedId} (the caller of this method already sent them their own ENDED sync), the
     * "call in progress" chat entry (if any) gets its final duration filled in, and the SVC group is
     * removed. */
    public static void endCall(CallSession session) {
        endCall(session, null, null);
    }

    private static void endCall(CallSession session, MinecraftServer server, UUID alreadyNotifiedId) {
        finalizeCallMessageIfConnected(session, server);
        ACTIVE_CALLS.remove(session.callId);
        Set<UUID> everyone = new HashSet<>(session.participants);
        everyone.addAll(session.ringing);
        for (UUID playerId : everyone) {
            PLAYER_TO_CALL.remove(playerId);
            if (server == null || playerId.equals(alreadyNotifiedId))
                continue;
            ServerPlayer target = server.getPlayerList().getPlayer(playerId);
            if (target != null)
                sendStateSync(target, session, CrazyPhoneCallStateSyncPacket.State.ENDED);
        }
        SvcCallBridge.removeGroup(session.callId);
    }

    /** No-op if the call never actually connected (nobody answered) - a missed/declined call has no
     * duration worth logging, and never got a "call in progress" chat entry to finalize in the first place
     * (see markConnectedIfFirstTime). */
    private static void finalizeCallMessageIfConnected(CallSession session, MinecraftServer server) {
        if (session.connectedAtEpochMillis < 0 || server == null)
            return;
        long durationMillis = System.currentTimeMillis() - session.connectedAtEpochMillis;
        CrazyPhoneHelper.finalizeCallMessage(server.overworld(), session.conversationId, session.callId, durationMillis);
    }

    /** The first time a call actually connects (a 2nd participant joins), posts a "call in progress" chat
     * entry (see CrazyPhoneHelper#addCallMessage) - the same entry is later finalized with the real duration
     * once the call ends (finalizeCallMessageIfConnected), rather than posting a second message. Guarded so
     * this only fires once per call, not on every subsequent joiner in a group call. */
    private static void markConnectedIfFirstTime(CallSession session, ServerPlayer contextPlayer) {
        if (session.connectedAtEpochMillis >= 0 || session.participants.size() < 2)
            return;
        session.connectedAtEpochMillis = System.currentTimeMillis();
        CrazyPhoneHelper.addCallMessage(contextPlayer.level(), session.conversationId, session.callId, session.connectedAtEpochMillis);
    }

    /** Called by the periodic ring-timeout sweep once a call has been ringing longer than
     * {@code callRingTimeoutSeconds} - callees who never answered are dropped (their client gets ENDED, same
     * as any other missed call) and, if that leaves no one but the initiator with nobody else on the line,
     * the whole call ends too (and the initiator is notified nobody picked up). A group call where at least
     * one other callee already answered is left alone - only the ones who didn't answer in time expire. */
    public static void expireRinging(CallSession session, MinecraftServer server) {
        for (UUID ringerId : new HashSet<>(session.ringing)) {
            session.ringing.remove(ringerId);
            PLAYER_TO_CALL.remove(ringerId);
            ServerPlayer ringer = server.getPlayerList().getPlayer(ringerId);
            if (ringer != null)
                sendStateSync(ringer, session, CrazyPhoneCallStateSyncPacket.State.ENDED);
        }
        if (session.participants.size() <= 1)
            endCall(session, server, null);
    }

    private static void sendStateSync(ServerPlayer target, CallSession session, CrazyPhoneCallStateSyncPacket.State state) {
        List<String> callNumbers = state == CrazyPhoneCallStateSyncPacket.State.ENDED
                ? List.of()
                : CrazyPhoneHelper.getGroupMembers(target.level(), session.conversationId);
        PacketDistributor.sendToPlayer(target, new CrazyPhoneCallStateSyncPacket(session.conversationId, session.callId, state, callNumbers));
        // Also written into the actual held phone's own item data, not just this targeted packet - vanilla's
        // equipment sync then carries it to nearby bystanders for free (see CrazyPhoneHelper), which the
        // packet above (sent only to this one player) never would.
        if (state == CrazyPhoneCallStateSyncPacket.State.ENDED)
            CrazyPhoneHelper.clearCallStateForAllPhones(target);
        else
            CrazyPhoneHelper.setCallStateForMatchingPhones(target, callNumbers, state.name());
    }

    private static ServerPlayer findPlayer(ServerPlayer contextPlayer, UUID playerId) {
        if (contextPlayer.getUUID().equals(playerId))
            return contextPlayer;
        return contextPlayer.getServer() == null ? null : contextPlayer.getServer().getPlayerList().getPlayer(playerId);
    }
}
