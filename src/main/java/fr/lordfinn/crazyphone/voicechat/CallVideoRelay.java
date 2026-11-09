package fr.lordfinn.crazyphone.voicechat;

//? if neoforge {
//? if >=1.20.5 {
/*import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//?}
// Forge 1.20.1: the same events at their original net.minecraftforge.* packages.
//? if legacyforge {
/*import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
*///?}
//? if fabric {
/*import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
*///?}

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.network.CallVideoC2SPacket;
import fr.lordfinn.crazyphone.network.CallVideoS2CPacket;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.video.CallVideoCodec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side of the call camera video: every client captures and encodes its own camera (see
 * fr.lordfinn.crazyphone.client.video), sends the frames here, and this relays them ONLY to the other members of
 * that same call, wherever they are (no chunk or distance involved at all - this is plain packet relaying, the same
 * way calls themselves go through Simple Voice Chat groups rather than proximity).
 *
 * Nothing is stored anywhere: a frame is checked and forwarded within the same server tick it arrived in, then
 * dropped. All state here is in memory, per online player, and is forgotten when they leave their call.
 *
 * Who sends: a participant whose video is on (the existing per-player toggle, off by default - see
 * CallRegistry#isVideoEnabled), who has the In Call screen open (so they can see their own "live" indicator),
 * whose client said it understands camera video and has it enabled, AND only while at least one other member of
 * the call is looking at the In Call screen with camera video enabled on their side. That decision is made here
 * and pushed to the sender as a DEMAND (target fps, 0 = stop), so a client never captures or sends anything when
 * no one is watching.
 *
 * Limits, all enforced here whatever the client claims: the feature switches and caps of {@link Config} (camera
 * video allowed, max fps, max resolution, per-viewer bandwidth), frame size and header checks before anything is
 * relayed, a per-sender frame rate bucket, a per-viewer byte budget (a viewer over budget skips frames, then
 * waits for the next keyframe rather than receiving a broken delta chain), and a total fps budget per call that
 * lowers every sender's rate as more people turn their camera on. Malformed data is dropped; only a client that
 * keeps sending garbage at a rate no real client could reach gets disconnected.
 *
 * Version safety: the server only sends CONFIG to a client able to receive it, and only sends anything else to a
 * client that answered HELLO, so an older client (or one without this feature) never receives a packet it does
 * not know and simply keeps the 3D preview.
 */
//? if neoforge || legacyforge {
@EventBusSubscriber
//?}
public final class CallVideoRelay {
    public static final int PROTOCOL = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    private static final int TICK_INTERVAL = 5;
    /** Senders in one call share this many times the per-sender max fps: up to 3 cameras get the full rate,
     * more than that and everyone's rate goes down so a big group call does not multiply the bandwidth. */
    private static final int CALL_FPS_BUDGET_FACTOR = 3;
    private static final int MIN_FPS = 2;
    private static final long KEYFRAME_REQUEST_INTERVAL_NANOS = 700_000_000L;
    private static final long ABUSE_WINDOW_NANOS = 10_000_000_000L;
    private static final int ABUSE_LIMIT = 200;
    private static final boolean STATS = Boolean.getBoolean("crazyphone.videoStats");

    private static final class Peer {
        int protocol;
        boolean enabled = true;
        String configSent = null;
        // As a sender
        int demandFps;
        boolean keyframeWanted;
        long lastKeyframeRequestNanos;
        double frameTokens;
        long lastFrameNanos;
        Set<UUID> watchers = new HashSet<>();
        // As a viewer
        double byteTokens;
        long lastByteRefillNanos;
        final Set<UUID> awaitingKeyframe = new HashSet<>();
        // Abuse tracking
        int invalid;
        long invalidWindowStart;
        // Stats
        long bytesIn, bytesOut, framesIn, framesOut, framesDropped;
    }

    private static final Map<UUID, Peer> PEERS = new HashMap<>();

    private CallVideoRelay() {
    }

    //? if neoforge || legacyforge {
    //? if >=1.20.5 {
    /*@SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tick(event.getServer());
    }
    *///? } else {
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick(event.getServer());
    }
    //?}
    //?}
    //? if fabric {
    /*// Called from CrazyphoneFabric#onInitialize.
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CallVideoRelay::tick);
    }
    *///?}

    public static boolean isAllowed() {
        return Config.callVideoEnabled && Config.callCameraVideoEnabled;
    }

    private static int maxFps() {
        return Math.max(1, Math.min(30, Config.callCameraVideoMaxFps));
    }

    private static int maxSide() {
        return Math.max(16, Math.min(CallVideoCodec.MAX_SIDE, Config.callCameraVideoMaxResolution));
    }

    private static int maxFrameBytes() {
        int side = maxSide();
        return CallVideoCodec.HEADER_BYTES + 1 + CallVideoCodec.MAX_PALETTE * 3 + side * side + 256;
    }

    // ---- Tick: demand, config, cleanup -----------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % TICK_INTERVAL != 0)
            return;
        try {
            tickInternal(server);
        } catch (Exception e) {
            // Never let video bookkeeping take a server tick down with it.
            LOGGER.error("Call video relay tick failed", e);
        }
    }

    private static void tickInternal(MinecraftServer server) {
        Set<UUID> inCalls = new HashSet<>();
        for (CallRegistry.CallSession session : CallRegistry.getActiveSessions()) {
            List<ServerPlayer> members = new ArrayList<>();
            for (UUID id : session.participants) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null)
                    members.add(player);
            }
            for (ServerPlayer member : members) {
                inCalls.add(member.getUUID());
                sendConfigIfNeeded(member);
            }
            updateDemand(server, session, members);
        }

        // Anyone known here who is no longer in a call (hung up, call ended, logged out): stop them, tell their
        // former viewers, forget them. A client that is still online keeps its handshake (protocol, setting).
        for (Map.Entry<UUID, Peer> entry : new ArrayList<>(PEERS.entrySet())) {
            UUID id = entry.getKey();
            Peer peer = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                stopSending(server, id, peer);
                PEERS.remove(id);
                continue;
            }
            if (!inCalls.contains(id)) {
                if (peer.demandFps > 0) {
                    peer.demandFps = 0;
                    sendDemand(player, peer);
                }
                stopSending(server, id, peer);
                peer.awaitingKeyframe.clear();
            }
        }

        if (STATS && server.getTickCount() % 200 == 0)
            logStats();
    }

    private static void sendConfigIfNeeded(ServerPlayer player) {
        boolean allowed = isAllowed();
        String signature = allowed + ":" + maxFps() + ":" + maxSide();
        Peer peer = PEERS.get(player.getUUID());
        if (peer != null && signature.equals(peer.configSent))
            return;
        if (!canReceive(player))
            return;
        if (peer == null) {
            peer = new Peer();
            PEERS.put(player.getUUID(), peer);
        }
        peer.configSent = signature;
        send(player, new CallVideoS2CPacket(CallVideoS2CPacket.CONFIG, CallVideoS2CPacket.NOBODY,
                allowed ? CallVideoS2CPacket.CONFIG_ALLOWED : 0, (maxFps() << 16) | maxSide(), null));
    }

    /** A member who can take part at all: understands camera video, has it enabled on their side. */
    private static boolean ready(ServerPlayer player) {
        Peer peer = PEERS.get(player.getUUID());
        return peer != null && peer.protocol > 0 && peer.enabled;
    }

    private static void updateDemand(MinecraftServer server, CallRegistry.CallSession session, List<ServerPlayer> members) {
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer member : members) {
            if (ready(member) && CallRegistry.isViewingInCallScreen(member))
                viewers.add(member);
        }
        List<ServerPlayer> senders = new ArrayList<>();
        Map<UUID, Set<UUID>> watchersBySender = new HashMap<>();
        for (ServerPlayer member : members) {
            Set<UUID> watchers = new HashSet<>();
            // A sender no longer needs their own In Call screen open: with the phone put away they keep sending
            // what they see while walking around. Only viewers need the screen open (nobody else is sent anything).
            boolean canSend = isAllowed() && ready(member) && CallRegistry.isVideoEnabled(session, member.getUUID());
            if (canSend) {
                for (ServerPlayer viewer : viewers)
                    if (!viewer.getUUID().equals(member.getUUID()))
                        watchers.add(viewer.getUUID());
            }
            watchersBySender.put(member.getUUID(), watchers);
            if (!watchers.isEmpty())
                senders.add(member);
        }

        int fps = senders.isEmpty() ? 0
                : Math.max(MIN_FPS, Math.min(maxFps(), maxFps() * CALL_FPS_BUDGET_FACTOR / senders.size()));
        long now = System.nanoTime();
        for (ServerPlayer member : members) {
            Peer peer = PEERS.get(member.getUUID());
            if (peer == null || peer.protocol <= 0)
                continue;
            Set<UUID> watchers = watchersBySender.getOrDefault(member.getUUID(), Set.of());
            int wanted = watchers.isEmpty() ? 0 : fps;
            // Someone new started watching: they need a keyframe before any delta frame means anything to them.
            for (UUID watcher : watchers) {
                if (!peer.watchers.contains(watcher)) {
                    Peer viewerPeer = PEERS.get(watcher);
                    if (viewerPeer != null)
                        viewerPeer.awaitingKeyframe.add(member.getUUID());
                    peer.keyframeWanted = true;
                }
            }
            // Someone stopped watching: tell them to drop the picture now.
            for (UUID former : peer.watchers) {
                if (!watchers.contains(former)) {
                    ServerPlayer formerPlayer = server.getPlayerList().getPlayer(former);
                    if (formerPlayer != null && ready(formerPlayer))
                        send(formerPlayer, new CallVideoS2CPacket(CallVideoS2CPacket.STOPPED, member.getUUID(), 0, 0, null));
                }
            }
            peer.watchers = watchers;
            boolean keyframe = peer.keyframeWanted && wanted > 0 && now - peer.lastKeyframeRequestNanos > KEYFRAME_REQUEST_INTERVAL_NANOS;
            if (wanted != peer.demandFps || keyframe) {
                peer.demandFps = wanted;
                if (keyframe) {
                    peer.lastKeyframeRequestNanos = now;
                    peer.keyframeWanted = false;
                }
                sendDemand(member, peer, keyframe);
            }
        }
    }

    private static void sendDemand(ServerPlayer player, Peer peer) {
        sendDemand(player, peer, false);
    }

    private static void sendDemand(ServerPlayer player, Peer peer, boolean keyframe) {
        send(player, new CallVideoS2CPacket(CallVideoS2CPacket.DEMAND, CallVideoS2CPacket.NOBODY, peer.demandFps,
                keyframe ? CallVideoS2CPacket.DEMAND_KEYFRAME : 0, null));
    }

    private static void stopSending(MinecraftServer server, UUID senderId, Peer peer) {
        for (UUID watcher : peer.watchers) {
            ServerPlayer player = server.getPlayerList().getPlayer(watcher);
            if (player != null && ready(player))
                send(player, new CallVideoS2CPacket(CallVideoS2CPacket.STOPPED, senderId, 0, 0, null));
        }
        peer.watchers = new HashSet<>();
    }

    // ---- Incoming packets -------------------------------------------------------------------------------------

    /** Every client -> server video message, on the server thread. Never throws. */
    public static void onClientPacket(ServerPlayer player, int kind, int value, byte[] data) {
        try {
            switch (kind) {
                case CallVideoC2SPacket.HELLO -> {
                    Peer peer = PEERS.computeIfAbsent(player.getUUID(), id -> new Peer());
                    peer.protocol = Math.max(1, Math.min(PROTOCOL, value));
                }
                case CallVideoC2SPacket.PREFS -> {
                    Peer peer = PEERS.get(player.getUUID());
                    if (peer != null)
                        peer.enabled = (value & 1) != 0;
                }
                case CallVideoC2SPacket.STOPPED -> {
                    Peer peer = PEERS.get(player.getUUID());
                    MinecraftServer server = player.level().getServer();
                    if (peer != null && server != null) {
                        stopSending(server, player.getUUID(), peer);
                        // Watchers are recomputed on the next tick; until the client sends again they would only
                        // get STOPPED twice, which is harmless.
                    }
                }
                case CallVideoC2SPacket.FRAME -> onFrame(player, data);
                default -> flagInvalid(player);
            }
        } catch (Exception e) {
            LOGGER.warn("Ignored a bad call video packet from {}", fr.lordfinn.crazyphone.utils.GameProfileCompat.name(player.getGameProfile()), e);
        }
    }

    private static void onFrame(ServerPlayer sender, byte[] data) {
        Peer peer = PEERS.get(sender.getUUID());
        if (peer == null || peer.protocol <= 0)
            return;
        if (data == null || data.length > maxFrameBytes()) {
            flagInvalid(sender);
            return;
        }
        CallVideoCodec.Header header = CallVideoCodec.readHeader(data);
        if (header == null || header.width() > maxSide() || header.height() > maxSide()) {
            flagInvalid(sender);
            return;
        }
        // Late frames after the demand dropped to 0 (hung up, viewer closed the screen, video toggled off) are
        // normal in-flight traffic, not abuse: just dropped.
        CallRegistry.CallSession session = CallRegistry.getSessionFor(sender.getUUID()).orElse(null);
        if (!isAllowed() || session == null || !session.participants.contains(sender.getUUID())
                || !CallRegistry.isVideoEnabled(session, sender.getUUID()) || peer.demandFps <= 0 || peer.watchers.isEmpty())
            return;

        // Frame rate bucket: a little burst room above the max fps (frames do not arrive perfectly evenly), never
        // more. Excess frames are dropped here, never queued.
        long now = System.nanoTime();
        double fps = maxFps() * 1.5;
        if (peer.lastFrameNanos == 0)
            peer.frameTokens = 3;
        else
            peer.frameTokens = Math.min(3, peer.frameTokens + (now - peer.lastFrameNanos) / 1.0e9 * fps);
        peer.lastFrameNanos = now;
        if (peer.frameTokens < 1) {
            peer.framesDropped++;
            // Far above any real client's rate: count it.
            if (peer.frameTokens < -maxFps() * 4)
                flagInvalid(sender);
            peer.frameTokens -= 0.25;
            return;
        }
        peer.frameTokens -= 1;
        peer.bytesIn += data.length;
        peer.framesIn++;

        MinecraftServer server = sender.level().getServer();
        if (server == null)
            return;
        CallVideoS2CPacket packet = new CallVideoS2CPacket(CallVideoS2CPacket.FRAME, sender.getUUID(), 0, 0, data);
        double budgetPerSecond = Math.max(8, Config.callCameraVideoMaxKilobytesPerSecond) * 1024.0;
        for (UUID watcherId : peer.watchers) {
            ServerPlayer watcher = server.getPlayerList().getPlayer(watcherId);
            Peer viewer = PEERS.get(watcherId);
            // Re-checked per frame: only current members of THIS call, still looking at the call screen.
            if (watcher == null || viewer == null || !session.participants.contains(watcherId) || !ready(watcher)
                    || !CallRegistry.isViewingInCallScreen(watcher))
                continue;
            if (viewer.awaitingKeyframe.contains(sender.getUUID())) {
                if (!header.keyframe()) {
                    peer.keyframeWanted = true;
                    continue;
                }
            }
            if (viewer.lastByteRefillNanos == 0)
                viewer.byteTokens = budgetPerSecond;
            else
                viewer.byteTokens = Math.min(budgetPerSecond, viewer.byteTokens + (now - viewer.lastByteRefillNanos) / 1.0e9 * budgetPerSecond);
            viewer.lastByteRefillNanos = now;
            if (viewer.byteTokens < data.length) {
                // Over this viewer's bandwidth budget: skip, and resync on the next keyframe instead of sending a
                // delta they cannot apply anyway.
                viewer.awaitingKeyframe.add(sender.getUUID());
                peer.keyframeWanted = true;
                peer.framesDropped++;
                continue;
            }
            viewer.byteTokens -= data.length;
            viewer.awaitingKeyframe.remove(sender.getUUID());
            send(watcher, packet);
            peer.bytesOut += data.length;
            peer.framesOut++;
        }
    }

    /** Malformed or impossible data. Dropped every time; a client that keeps it up far beyond anything a real
     * client sends is disconnected. */
    private static void flagInvalid(ServerPlayer player) {
        Peer peer = PEERS.computeIfAbsent(player.getUUID(), id -> new Peer());
        long now = System.nanoTime();
        if (now - peer.invalidWindowStart > ABUSE_WINDOW_NANOS) {
            peer.invalidWindowStart = now;
            peer.invalid = 0;
        }
        if (++peer.invalid == ABUSE_LIMIT) {
            LOGGER.warn("Disconnecting {}: too much invalid call video data", fr.lordfinn.crazyphone.utils.GameProfileCompat.name(player.getGameProfile()));
            player.connection.disconnect(Component.translatable("message.crazyphone.call_video.kicked"));
        }
    }

    private static void logStats() {
        for (Map.Entry<UUID, Peer> entry : PEERS.entrySet()) {
            Peer p = entry.getValue();
            if (p.framesIn == 0 && p.framesDropped == 0)
                continue;
            LOGGER.info("[call video] {}: in {} frames / {} B ({} B/frame), out {} frames / {} B, dropped {}, demand {} fps",
                    entry.getKey(), p.framesIn, p.bytesIn, p.framesIn == 0 ? 0 : p.bytesIn / p.framesIn, p.framesOut, p.bytesOut,
                    p.framesDropped, p.demandFps);
            p.framesIn = p.bytesIn = p.framesOut = p.bytesOut = p.framesDropped = 0;
        }
    }

    // ---- Sending ----------------------------------------------------------------------------------------------

    private static void send(ServerPlayer player, CallVideoS2CPacket packet) {
        try {
            NetworkAccess.sendToPlayer(player, packet);
        } catch (Exception e) {
            LOGGER.debug("Could not send call video packet", e);
        }
    }

    /** Whether this client can be sent the video packets at all. Fabric knows which channels the other side
     * registered; NeoForge refuses mismatched mod versions at login already; old Forge only logs (and drops) a
     * message it does not know, and anything past CONFIG only goes to clients that answered HELLO anyway. */
    private static boolean canReceive(ServerPlayer player) {
        //? if fabric {
        /*return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, CallVideoS2CPacket.TYPE);
        *///?} else {
        return true;
        //?}
    }
}
