package fr.lordfinn.crazyphone.voicechat;

import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import fr.lordfinn.crazyphone.network.CallParticipantHeadRotationSyncPacket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts each active call's connected participants' live pose state - head-vs-body yaw deviation, pitch,
 * {@link net.minecraft.world.entity.Pose}, sneak/sprint/swim flags, and a walk-animation speed input - to
 * their fellow participants, several times a second - lets the InCall screen's bust portraits mirror the real
 * player's live look (sneaking, swimming, running...) in real time (see
 * CrazyPhoneInCallScreenScreen.renderBust) while keeping the bust's body fixed facing the camera. Deliberately
 * a separate, higher-frequency tick listener from CallTerminationListener's 20-tick cleanup sweep - this needs
 * to update several times a second to look smooth, cleanup does not.
 */
@EventBusSubscriber
public class CallHeadRotationSync {
    private static final int SYNC_INTERVAL_TICKS = 2;
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    /** Previous tick's position per synced player, used to derive a per-tick horizontal movement distance
     * for the walk-animation speed input (matches LivingEntity#updateWalkAnimation's own
     * {@code min(distance * 4, 1)} formula) without depending on uncertain internals like walkDist's exact
     * accumulation semantics for server-controlled players. Never actively cleared - a stale entry for a
     * UUID no longer in any call is harmless and the set of ever-synced players stays small. */
    private static final Map<UUID, Vec3> lastPositions = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0)
            return;
        for (CallRegistry.CallSession session : CallRegistry.getActiveSessions()) {
            try {
                syncSession(server, session);
            } catch (Exception e) {
                // One session's rotation broadcast failing (a participant disconnecting mid-loop, etc.)
                // must never skip every other active call's sync for this tick.
                LOGGER.error("Failed to sync head rotation for call {}", session.callId, e);
            }
        }
    }

    private static void syncSession(MinecraftServer server, CallRegistry.CallSession session) {
        if (session.participants.size() < 2)
            return;

        List<UUID> ids = new ArrayList<>();
        List<Float> headYawDeltas = new ArrayList<>();
        List<Float> pitches = new ArrayList<>();
        List<Integer> poseOrdinals = new ArrayList<>();
        List<Boolean> crouching = new ArrayList<>();
        List<Boolean> sprinting = new ArrayList<>();
        List<Boolean> swimming = new ArrayList<>();
        List<Float> walkAnimationSpeeds = new ArrayList<>();
        for (UUID id : session.participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null)
                continue;
            ids.add(id);
            headYawDeltas.add(Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot));
            pitches.add(player.getXRot());
            poseOrdinals.add(player.getPose().ordinal());
            crouching.add(player.isCrouching());
            sprinting.add(player.isSprinting());
            swimming.add(player.isSwimming());
            walkAnimationSpeeds.add(computeWalkAnimationSpeed(id, player));
        }
        if (ids.size() < 2)
            return;

        for (int targetIndex = 0; targetIndex < ids.size(); targetIndex++) {
            ServerPlayer target = server.getPlayerList().getPlayer(ids.get(targetIndex));
            if (target == null)
                continue;
            List<UUID> otherIds = new ArrayList<>();
            List<Float> otherYawDeltas = new ArrayList<>();
            List<Float> otherPitches = new ArrayList<>();
            List<Integer> otherPoses = new ArrayList<>();
            List<Boolean> otherCrouching = new ArrayList<>();
            List<Boolean> otherSprinting = new ArrayList<>();
            List<Boolean> otherSwimming = new ArrayList<>();
            List<Float> otherWalkSpeeds = new ArrayList<>();
            for (int i = 0; i < ids.size(); i++) {
                if (i == targetIndex)
                    continue;
                otherIds.add(ids.get(i));
                otherYawDeltas.add(headYawDeltas.get(i));
                otherPitches.add(pitches.get(i));
                otherPoses.add(poseOrdinals.get(i));
                otherCrouching.add(crouching.get(i));
                otherSprinting.add(sprinting.get(i));
                otherSwimming.add(swimming.get(i));
                otherWalkSpeeds.add(walkAnimationSpeeds.get(i));
            }
            PacketDistributor.sendToPlayer(target,
                    new CallParticipantHeadRotationSyncPacket(session.conversationId, otherIds, otherYawDeltas, otherPitches,
                            otherPoses, otherCrouching, otherSprinting, otherSwimming, otherWalkSpeeds));
        }
    }

    /** Mirrors LivingEntity#updateWalkAnimation's own {@code min(distance * 4, 1)} formula, using the
     * horizontal distance moved since the last sync (normalized back to a per-tick figure since this runs
     * every SYNC_INTERVAL_TICKS ticks, not every tick) as the distance input. */
    private static float computeWalkAnimationSpeed(UUID id, ServerPlayer player) {
        Vec3 current = player.position();
        Vec3 last = lastPositions.getOrDefault(id, current);
        lastPositions.put(id, current);
        double perTickDistance = current.subtract(last).horizontalDistance() / SYNC_INTERVAL_TICKS;
        return Mth.clamp((float) perTickDistance * 4.0F, 0f, 1f);
    }
}
