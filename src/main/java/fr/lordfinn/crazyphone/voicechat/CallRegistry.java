package fr.lordfinn.crazyphone.voicechat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneIncomingCallNotificationPacket;

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

        UUID callId = SvcCallBridge.createCallGroup("crazyphone-call-" + conversationId);
        if (callId == null)
            return null;

        CallSession session = new CallSession(callId, conversationId, initiator.getUUID());
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

    /** Moves a ringing callee into the active call - called when they use the phone while being called. */
    public static void answer(ServerPlayer player) {
        CallSession session = getSessionFor(player.getUUID()).orElse(null);
        if (session == null || !session.ringing.remove(player.getUUID()))
            return;
        session.participants.add(player.getUUID());
        session.soleParticipantSinceGameTime = -1;
        SvcCallBridge.joinGroup(player, session.callId);
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

        if (session.participants.isEmpty() && session.ringing.isEmpty()) {
            endCall(session);
            return;
        }
        if (session.participants.size() == 1) {
            session.soleParticipantSinceGameTime = 0; // marked "pending" - the tick sweep (later phase) stamps the real game time and plays the disconnect sound once
        }
    }

    /** Full teardown - every remaining participant/ringer is dropped and the SVC group is removed. */
    public static void endCall(CallSession session) {
        ACTIVE_CALLS.remove(session.callId);
        for (UUID playerId : new HashSet<>(session.participants)) {
            PLAYER_TO_CALL.remove(playerId);
        }
        for (UUID playerId : new HashSet<>(session.ringing)) {
            PLAYER_TO_CALL.remove(playerId);
        }
        SvcCallBridge.removeGroup(session.callId);
    }

    private static void sendStateSync(ServerPlayer target, CallSession session, CrazyPhoneCallStateSyncPacket.State state) {
        PacketDistributor.sendToPlayer(target, new CrazyPhoneCallStateSyncPacket(session.conversationId, session.callId, state));
    }

    private static ServerPlayer findPlayer(ServerPlayer contextPlayer, UUID playerId) {
        if (contextPlayer.getUUID().equals(playerId))
            return contextPlayer;
        return contextPlayer.getServer() == null ? null : contextPlayer.getServer().getPlayerList().getPlayer(playerId);
    }
}
