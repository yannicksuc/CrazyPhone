package fr.lordfinn.crazyphone.voicechat;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneIncomingCallNotificationPacket;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");

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
        // Loading SvcCallBridge's class at all - triggered by calling ANY of its static methods, even ones
        // whose particular execution path would have no-opped - requires the JVM to verify its ENTIRE
        // bytecode, including methods unrelated to this call (playAudioToPlayer's AudioChannel/AudioPlayer
        // usage, for instance). Those types come from the compileOnly voicechat-api, never bundled at
        // runtime, so if the real SVC mod isn't installed they're simply unresolvable - the very first
        // SvcCallBridge call this method used to make (createCallGroup, below) crashed with
        // NoClassDefFoundError instead of the graceful no-call this method's own javadoc promises. This is
        // the ONLY place a session is ever created, so keeping it here means every other CallRegistry method
        // stays safe too: none of them can reach their own SvcCallBridge calls if no session exists to find.
        if (!VoicechatIntegration.isAvailable())
            return null;
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
        notifySafe(initiator, session, CrazyPhoneCallStateSyncPacket.State.CALLING);
        // A brand-new session, not a join of an already-active one (see the existing != null branch above) -
        // this is the transition that actually flips conversation-level "is there a call happening here",
        // so every online conversation member (not just these participants/ringers) needs to hear about it.
        CrazyPhoneHelper.broadcastConversationCallActivity(initiator.level(), conversationId, true);

        for (ServerPlayer callee : callees) {
            if (callee.getUUID().equals(initiator.getUUID()) || !SvcCallBridge.isCallable(callee))
                continue;
            session.ringing.add(callee.getUUID());
            PLAYER_TO_CALL.put(callee.getUUID(), callId);
            notifySafe(callee, session, CrazyPhoneCallStateSyncPacket.State.RINGING);
            //? if >=1.20.5 {
            PacketDistributor.sendToPlayer(callee,
                    new CrazyPhoneIncomingCallNotificationPacket(conversationId, initiator.getGameProfile().getName(), callId));
            //? } else {
            /*PacketDistributor.PLAYER.with(callee).send(
                    new CrazyPhoneIncomingCallNotificationPacket(conversationId, initiator.getGameProfile().getName(), callId));
            *///?}
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
                notifySafe(participant, session, CrazyPhoneCallStateSyncPacket.State.ACTIVE);
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
                notifySafe(participant, session, CrazyPhoneCallStateSyncPacket.State.ACTIVE);
        }
    }

    /** Voluntary hangup/decline, or a forced removal (drop, moved to another inventory, disconnect). The
     * cleanup runs FIRST and the leaving player's own ENDED notification is strictly best-effort afterward -
     * getting this order backwards once already caused a real bug: sendStateSync throwing (their connection
     * is already gone, or - in tests - never completed a handshake) aborted the method before the session
     * was ever actually cleaned up, leaving them stuck in CallRegistry's maps forever and the periodic sweep
     * retrying (and re-throwing) on every subsequent tick indefinitely. */
    public static void leave(ServerPlayer player) {
        CallSession session = getSessionFor(player.getUUID()).orElse(null);
        if (session == null)
            return;
        leaveInternal(player.getUUID(), player.getServer(), session);
        notifySafe(player, session, CrazyPhoneCallStateSyncPacket.State.ENDED);
    }

    /** Same cleanup as {@link #leave(ServerPlayer)}, for a player who's no longer reachable as a live
     * ServerPlayer at all - fully logged out, not just mid-disconnect. Needed because the periodic sweep
     * (CallTerminationListener) can only look someone up via {@code PlayerList#getPlayer(UUID)}, which
     * returns null the instant a player is actually gone - {@code hasDisconnected()} only covers the brief
     * mid-disconnect window, not "already logged off", so this is the path that actually fires in the
     * common case. Skips whatever needs a live connection for THIS player (no ENDED packet to send them -
     * they're not connected to receive it; no inventory to touch - reconcilePhoneStateOnJoin cleans up their
     * held phones' stale NBT the next time they log back in) but still updates the session, tells SVC to
     * drop their connection's group membership, and - critically - still notifies any REMAINING participants
     * and tears the session down if this was the last one. Without this, a session with zero reachable
     * participants left never empties: the conversation's "call in progress" chat entry never finalizes its
     * duration, and the phantom session lingers, blocking a clean rejoin. */
    public static void leave(UUID playerId, @Nullable MinecraftServer server) {
        CallSession session = getSessionFor(playerId).orElse(null);
        if (session == null)
            return;
        leaveInternal(playerId, server, session);
    }

    private static void leaveInternal(UUID playerId, @Nullable MinecraftServer server, CallSession session) {
        session.participants.remove(playerId);
        session.ringing.remove(playerId);
        PLAYER_TO_CALL.remove(playerId);
        SvcCallBridge.leaveGroup(playerId);

        // Nobody actually connected left - whether that's the last real participant hanging up, or the
        // initiator cancelling before anyone still ringing had a chance to answer, either way there's no
        // call left to have. Checking participants alone (not also requiring ringing to be empty) matters:
        // otherwise a cancelled call with people still ringing never tears down - it sits in ACTIVE_CALLS
        // forever, and a still-ringing callee who later answers gets added as the sole "participant" of a
        // call nobody else is on.
        if (session.participants.isEmpty()) {
            endCall(session, server, playerId);
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

    private static void endCall(CallSession session, @Nullable MinecraftServer server, @Nullable UUID alreadyNotifiedId) {
        finalizeCallMessageIfConnected(session, server);
        ACTIVE_CALLS.remove(session.callId);
        Set<UUID> everyone = new HashSet<>(session.participants);
        everyone.addAll(session.ringing);
        for (UUID playerId : everyone) {
            // PLAYER_TO_CALL.remove happens unconditionally, BEFORE the notify attempt below - one
            // participant's packet send failing must never leave THEM (or anyone later in this loop) stuck
            // registered in a session that's otherwise already torn down (see notifyEnded's own javadoc for
            // why this ordering matters).
            PLAYER_TO_CALL.remove(playerId);
            if (server == null || playerId.equals(alreadyNotifiedId))
                continue;
            ServerPlayer target = server.getPlayerList().getPlayer(playerId);
            if (target != null)
                notifySafe(target, session, CrazyPhoneCallStateSyncPacket.State.ENDED);
        }
        SvcCallBridge.removeGroup(session.callId);
        // The other conversation-liveness transition (see startCall's matching broadcast) - lets a
        // contacts-list badge or the conversation screen's call icon clear once the call actually ends,
        // instead of staying stuck showing "rejoinable" for a call that no longer exists.
        if (server != null)
            CrazyPhoneHelper.broadcastConversationCallActivity(server.overworld(), session.conversationId, false);
    }

    /** A call that connected gets its "call in progress" chat entry finalized with the real duration; one
     * that never connected at all (nobody answered, everyone declined, or the caller cancelled first) never
     * got that entry in the first place (see markConnectedIfFirstTime) - it posts a "missed call" system
     * message instead, so it isn't just silently invisible in the conversation feed. */
    private static void finalizeCallMessageIfConnected(CallSession session, MinecraftServer server) {
        if (server == null)
            return;
        if (session.connectedAtEpochMillis < 0) {
            CrazyPhoneHelper.addMissedCallMessage(server.overworld(), session.conversationId, session.initiator);
            return;
        }
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
                notifySafe(ringer, session, CrazyPhoneCallStateSyncPacket.State.ENDED);
        }
        if (session.participants.size() <= 1)
            endCall(session, server, null);
    }

    /** Best-effort state notification - wrapped so one player's packet-send failure (a connection that's
     * already gone, or in tests never completed a handshake) can never abort whatever loop called this
     * partway through. Every call site that mutates CallRegistry's own maps (leave/endCall/expireRinging)
     * already does so BEFORE calling this, specifically so a failure here is cosmetic (that one player just
     * doesn't get the toast) rather than leaving them - or, worse, anyone processed after them in the same
     * loop - permanently stuck registered in a call that's already ended for real. The CALLING/RINGING/ACTIVE
     * call sites (starting or joining a call) don't have that same "stuck forever" failure mode since nothing
     * is torn down there, but a failed notification still shouldn't be able to stop the REST of a multi-callee
     * ring-out or a group's other participants from hearing about a new joiner. */
    private static void notifySafe(ServerPlayer target, CallSession session, CrazyPhoneCallStateSyncPacket.State state) {
        try {
            sendStateSync(target, session, state);
        } catch (Exception e) {
            LOGGER.error("Failed to send call state {} to {}", state, target.getGameProfile().getName(), e);
        }
    }

    private static void sendStateSync(ServerPlayer target, CallSession session, CrazyPhoneCallStateSyncPacket.State state) {
        List<String> callNumbers = state == CrazyPhoneCallStateSyncPacket.State.ENDED
                ? List.of()
                : CrazyPhoneHelper.getGroupMembers(target.level(), session.conversationId);
        List<UUID> participantIds = state == CrazyPhoneCallStateSyncPacket.State.ENDED
                ? List.of()
                : session.participants.stream().filter(id -> !id.equals(target.getUUID())).toList();
        List<String> participantNames = participantIds.stream().map(id -> resolvePlayerName(target, id)).toList();
        //? if >=1.20.5 {
        PacketDistributor.sendToPlayer(target, new CrazyPhoneCallStateSyncPacket(session.conversationId, session.callId, state, callNumbers, participantIds, participantNames));
        //? } else {
        /*PacketDistributor.PLAYER.with(target).send(new CrazyPhoneCallStateSyncPacket(session.conversationId, session.callId, state, callNumbers, participantIds, participantNames));
        *///?}
        // Also written into the actual held phone's own item data, not just this targeted packet - vanilla's
        // equipment sync then carries it to nearby bystanders for free (see CrazyPhoneHelper), which the
        // packet above (sent only to this one player) never would.
        if (state == CrazyPhoneCallStateSyncPacket.State.ENDED)
            CrazyPhoneHelper.clearCallStateForAllPhones(target);
        else
            CrazyPhoneHelper.setCallStateForMatchingPhones(target, callNumbers, state.name());
    }

    private static String resolvePlayerName(ServerPlayer contextPlayer, UUID playerId) {
        ServerPlayer player = findPlayer(contextPlayer, playerId);
        return player != null ? player.getGameProfile().getName() : "";
    }

    private static ServerPlayer findPlayer(ServerPlayer contextPlayer, UUID playerId) {
        if (contextPlayer.getUUID().equals(playerId))
            return contextPlayer;
        return contextPlayer.getServer() == null ? null : contextPlayer.getServer().getPlayerList().getPlayer(playerId);
    }
}
