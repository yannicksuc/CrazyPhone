package fr.lordfinn.crazyphone.client.video;

//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
//? if >=1.20.5 {
/*import net.neoforged.neoforge.client.event.ClientTickEvent;
*///? } else {
import net.neoforged.neoforge.event.TickEvent;
//?}
//?}
// Forge 1.20.1: the same events at their original net.minecraftforge.* packages.
//? if legacyforge {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
*///?}
//? if fabric {
/*import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
*///?}

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import fr.lordfinn.crazyphone.ClientConfig;
import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneInCallScreenScreen;
import fr.lordfinn.crazyphone.network.CallVideoC2SPacket;
import fr.lordfinn.crazyphone.network.CallVideoS2CPacket;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.video.CallVideoCodec;
import fr.lordfinn.crazyphone.voicechat.CallVideoRelay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client side of the call camera video: captures this player's own camera while the server says someone is
 * watching, encodes it off the render thread, sends it; decodes everyone else's frames into small textures the
 * In Call screen draws in their tiles. See CallVideoRelay (server) for the rules of who sends to whom.
 *
 * Everything that can fail here (a GPU readback, a texture upload, a decoder fed garbage) is caught: a broken
 * frame is dropped, and a capture failure turns this client's own camera off for the rest of the session with one
 * chat message, never a crash. Every buffer is bounded: one capture in flight, one encode in flight, one pending
 * frame per sender (the newest replaces an older one not decoded yet), and at most {@link #MAX_REMOTES} textures.
 * Nothing is ever written to disk except the on/off preference.
 */
//? if neoforge || legacyforge {
@EventBusSubscriber(value = Dist.CLIENT)
//?}
public final class CallVideoClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    public enum Camera { BACK, FRONT }

    /** A remote picture older than this is no longer "live": the tile goes back to the 3D preview / 2D head. */
    private static final long STALE_MILLIS = 2_000L;
    /** A stale remote texture is freed after this long without frames. */
    private static final long RELEASE_MILLIS = 15_000L;
    private static final int MAX_REMOTES = 12;
    /** Own-preview rate while nobody is watching (the picture on your own tile, never sent anywhere). */
    private static final int PREVIEW_FPS = 4;
    private static final long CAMERA_SWITCH_COOLDOWN_MILLIS = 400L;
    private static final UUID SELF_ID = new UUID(0L, 1L);
    private static final boolean STATS = Boolean.getBoolean("crazyphone.videoStats");
    /** Dev runs keep the game in a background window; without this the capture would (rightly) never run there. */
    private static final boolean CAPTURE_UNFOCUSED = Boolean.getBoolean("crazyphone.dev.videoCaptureUnfocused");

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CrazyPhone call video");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    // Server handshake
    private static boolean serverAllowed;
    private static int serverMaxFps = 8;
    private static int serverMaxSide = 64;
    private static boolean helloSent;
    // What the server asks of this client's camera
    private static int demandFps;
    // Own camera
    private static Camera camera = Camera.BACK;
    private static long lastCameraSwitchMillis;
    private static boolean capturing;
    private static boolean selfiePoseSent;
    private static long nextCaptureNanos;
    private static long lastSentMillis = -1;
    private static boolean disabledForSession;
    private static CallVideoCodec.Encoder encoder;
    private static final AtomicBoolean encoding = new AtomicBoolean();
    private static boolean prefsLoaded;

    private static final Map<UUID, Stream> REMOTES = new HashMap<>();
    private static Stream self;

    // Stats (only logged with -Dcrazyphone.videoStats=true)
    private static long statFrames, statBytes, statCaptureNanos, statEncodeNanos, statReceived, statReceivedBytes;
    private static long statSince = System.nanoTime();

    private CallVideoClient() {
    }

    // ---- Ticking ----------------------------------------------------------------------------------------------

    //? if neoforge || legacyforge {
    @SubscribeEvent
    //? if >=1.20.5 {
    /*public static void onClientTick(ClientTickEvent.Post event) {
        tick();
    }
    *///? } else {
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tick();
    }
    //?}
    //?}
    //? if fabric {
    /*public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }
    *///?}

    private static void tick() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                // Disconnected (or never joined): everything from that server is meaningless now.
                if (helloSent || self != null || !REMOTES.isEmpty())
                    resetAll();
                return;
            }
            if (!prefsLoaded)
                loadPrefs(mc);
            if (!ClientCallState.isInCall()) {
                if (self != null || !REMOTES.isEmpty() || capturing)
                    endCall();
                return;
            }
            if (capturing && !shouldCapture(mc))
                stopCapturing(true);
            long now = System.currentTimeMillis();
            for (Iterator<Map.Entry<UUID, Stream>> it = REMOTES.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Stream> entry = it.next();
                Stream stream = entry.getValue();
                // Left the call, turned video off, or silent for a long time: free it.
                if (!ClientCallState.isVideoEnabled(entry.getKey()) || now - stream.lastFrameMillis > RELEASE_MILLIS) {
                    stream.close();
                    it.remove();
                }
            }
            if (self != null && !capturing && now - self.lastFrameMillis > RELEASE_MILLIS) {
                self.close();
                self = null;
            }
            if (STATS && System.nanoTime() - statSince > 10_000_000_000L)
                logStats();
        } catch (Throwable t) {
            LOGGER.error("Call video tick failed", t);
        }
    }

    /** Called by CallVideoCaptureMixin once per rendered frame, right after the world is drawn and before the hand,
     * the HUD or any screen: the main render target then holds exactly what the back camera sees, nothing else. */
    public static void onLevelRendered() {
        if (CallVideoFrontCamera.isRendering())
            return;
        try {
            CallVideoCapture.poll();
            Minecraft mc = Minecraft.getInstance();
            if (!shouldCapture(mc)) {
                if (capturing)
                    stopCapturing(true);
                return;
            }
            capturing = true;
            boolean front = camera == Camera.FRONT && CallVideoFrontCamera.isAvailable();
            updateSelfiePose(front);
            // The selfie view is drawn and captured before the next frame's world pass (see beforeLevelRender).
            if (front || !captureDue())
                return;
            long start = System.nanoTime();
            CallVideoCapture.capture(mc./*$ mc_main_render_target {*/getMainRenderTarget/*$}*/(), frameSide(), (rgb, s) -> onCaptured(rgb, s, false));
            statCaptureNanos += System.nanoTime() - start;
        } catch (Throwable t) {
            disableForSession(t);
        }
    }

    /** Called by CallVideoCaptureMixin at the start of each frame's world pass, with a way to run one more such
     * pass: when the selfie camera is due, its offscreen view is drawn now, before the real one. */
    public static void beforeLevelRender(Runnable renderLevel) {
        if (!capturing || camera != Camera.FRONT || CallVideoFrontCamera.isRendering())
            return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (!shouldCapture(mc) || !CallVideoFrontCamera.isAvailable() || !captureDue())
                return;
            long start = System.nanoTime();
            CallVideoFrontCamera.renderAndCapture(mc, frameSide(), renderLevel, (rgb, s) -> onCaptured(rgb, s, true));
            statCaptureNanos += System.nanoTime() - start;
        } catch (Throwable t) {
            disableForSession(t);
        }
    }

    /** Whether the next frame should be captured now (capped rate, nothing already in flight); schedules the one
     * after when it says yes. */
    private static boolean captureDue() {
        long now = System.nanoTime();
        if (now < nextCaptureNanos || CallVideoCapture.isBusy() || encoding.get())
            return false;
        int fps = demandFps > 0 ? Math.min(demandFps, serverMaxFps) : PREVIEW_FPS;
        nextCaptureNanos = now + 1_000_000_000L / Math.max(1, fps);
        return true;
    }

    private static int frameSide() {
        return Math.max(16, Math.min(CallVideoCodec.MAX_SIDE, serverMaxSide));
    }

    private static boolean shouldCapture(Minecraft mc) {
        return !disabledForSession && helloSent && serverAllowed && ClientConfig.callCameraVideo
                && mc.player != null && mc.level != null
                && ClientCallState.isActiveCall() && ClientCallState.isVideoFeatureEnabled() && ClientCallState.isSelfVideoEnabled()
                && mc./*$ mc_get_screen {*/screen/*$}*/ instanceof CrazyPhoneInCallScreenScreen
                && !mc.isPaused() && (mc.isWindowActive() || CAPTURE_UNFOCUSED)
                && !CrazyPhoneCaptureMode.isActive();
    }

    // ---- Outgoing ---------------------------------------------------------------------------------------------

    private static void onCaptured(int[] rgb, int side, boolean front) {
        if (!capturing || !encoding.compareAndSet(false, true))
            return;
        if (encoder == null)
            encoder = new CallVideoCodec.Encoder();
        CallVideoCodec.Encoder enc = encoder;
        try {
            WORKER.execute(() -> {
                try {
                    long start = System.nanoTime();
                    byte[] data = enc.encode(rgb, side, side, front, start);
                    statEncodeNanos += System.nanoTime() - start;
                    if (data == null)
                        return;
                    Minecraft.getInstance().execute(() -> sendOwnFrame(data));
                } catch (Throwable t) {
                    LOGGER.warn("Call video encode failed", t);
                } finally {
                    encoding.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            encoding.set(false);
        }
    }

    private static void sendOwnFrame(byte[] data) {
        if (!capturing)
            return;
        // Your own tile shows exactly what the others get, decoded from the very same bytes.
        if (self == null)
            self = new Stream(SELF_ID);
        self.offer(data);
        if (demandFps > 0 && helloSent) {
            NetworkAccess.sendToServer(new CallVideoC2SPacket(CallVideoC2SPacket.FRAME, 0, data));
            lastSentMillis = System.currentTimeMillis();
            statFrames++;
            statBytes += data.length;
        }
    }

    private static void stopCapturing(boolean tellServer) {
        boolean wasSending = lastSentMillis >= 0;
        capturing = false;
        CallVideoCapture.abort();
        CallVideoFrontCamera.release();
        updateSelfiePose(false);
        lastSentMillis = -1;
        if (encoder != null) {
            CallVideoCodec.Encoder enc = encoder;
            encoder = null;
            submitQuietly(enc::close);
        }
        if (tellServer && wasSending && helloSent && Minecraft.getInstance().getConnection() != null)
            NetworkAccess.sendToServer(new CallVideoC2SPacket(CallVideoC2SPacket.STOPPED, 0, null));
    }

    /** The arm held out in front, like a selfie, while the front camera films (others nearby see it too). */
    private static void updateSelfiePose(boolean front) {
        if (front == selfiePoseSent || CrazyPhoneCaptureMode.isActive())
            return;
        selfiePoseSent = front;
        if (Minecraft.getInstance().getConnection() != null)
            NetworkAccess.sendToServer(new fr.lordfinn.crazyphone.network.CrazyPhoneSelfiePoseSyncPacket(front,
                    fr.lordfinn.crazyphone.client.CrazyPhoneSelfieStickPose.stickX, fr.lordfinn.crazyphone.client.CrazyPhoneSelfieStickPose.stickY));
    }

    public static boolean isSelfiePoseActive() {
        return selfiePoseSent;
    }

    private static void disableForSession(Throwable cause) {
        LOGGER.error("Call camera video failed, turning it off for this session", cause);
        disabledForSession = true;
        try {
            stopCapturing(true);
            CallVideoCapture.release();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null)
                //? if >=1.21.10 <26 {
                /*mc.player.displayClientMessage(Component.translatable("message.crazyphone.call_video.capture_failed"), false);
                *///?} else {
                mc.player.sendSystemMessage(Component.translatable("message.crazyphone.call_video.capture_failed"));
                //?}
        } catch (Throwable ignored) {
        }
    }

    // ---- Incoming ---------------------------------------------------------------------------------------------

    /** Every server -> client video message, on the render thread. Never throws. */
    public static void onServerPacket(int kind, UUID subject, int value, int value2, byte[] data) {
        try {
            switch (kind) {
                case CallVideoS2CPacket.CONFIG -> {
                    serverAllowed = (value & CallVideoS2CPacket.CONFIG_ALLOWED) != 0;
                    serverMaxFps = Math.max(1, Math.min(30, value2 >>> 16));
                    serverMaxSide = Math.max(16, Math.min(CallVideoCodec.MAX_SIDE, value2 & 0xFFFF));
                    if (!helloSent) {
                        helloSent = true;
                        NetworkAccess.sendToServer(new CallVideoC2SPacket(CallVideoC2SPacket.HELLO, CallVideoRelay.PROTOCOL, null));
                        sendPrefs();
                    }
                    if (!serverAllowed)
                        endCall();
                }
                case CallVideoS2CPacket.DEMAND -> {
                    demandFps = Math.max(0, Math.min(serverMaxFps, value));
                    if ((value2 & CallVideoS2CPacket.DEMAND_KEYFRAME) != 0 && encoder != null) {
                        CallVideoCodec.Encoder enc = encoder;
                        submitQuietly(enc::requestKeyframe);
                    }
                }
                case CallVideoS2CPacket.FRAME -> {
                    // Only from someone who is in this call with video on, and only if we want camera video at all.
                    if (!ClientConfig.callCameraVideo || subject == null || !ClientCallState.isActiveCall()
                            || !ClientCallState.isVideoEnabled(subject) || data == null)
                        return;
                    Stream stream = REMOTES.get(subject);
                    if (stream == null) {
                        if (REMOTES.size() >= MAX_REMOTES)
                            return;
                        stream = new Stream(subject);
                        REMOTES.put(subject, stream);
                    }
                    stream.offer(data);
                    statReceived++;
                    statReceivedBytes += data.length;
                }
                case CallVideoS2CPacket.STOPPED -> {
                    Stream stream = REMOTES.remove(subject);
                    if (stream != null)
                        stream.close();
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Ignored a call video packet", t);
        }
    }

    // ---- Screen API -------------------------------------------------------------------------------------------

    /** The live picture of a participant (or of yourself, with your own UUID), or null when there is none right
     * now: the tile then shows the 3D preview / 2D head exactly as before. */
    public static /*$ res_loc {*/ResourceLocation/*$}*/ liveTexture(UUID id, boolean isSelf) {
        if (!ClientConfig.callCameraVideo || !serverAllowed || !helloSent)
            return null;
        Stream stream = isSelf ? (capturing ? self : null) : REMOTES.get(id);
        if (stream == null || stream.textureId == null || System.currentTimeMillis() - stream.lastFrameMillis > STALE_MILLIS)
            return null;
        return stream.textureId;
    }

    /** Whether this server allows camera video (the per-client switch only shows then). */
    public static boolean isServerAllowed() {
        return serverAllowed && helloSent;
    }

    /** Whether this server and client do camera video at all (the camera icons only show then). */
    public static boolean isAvailable() {
        return serverAllowed && helloSent && !disabledForSession;
    }

    /** Actually sending frames to someone right now (the red dot on your own tile). */
    public static boolean isSending() {
        return capturing && demandFps > 0 && lastSentMillis >= 0 && System.currentTimeMillis() - lastSentMillis < STALE_MILLIS;
    }

    /** The camera actually filming: the selfie camera falls back to the back one whenever it is not available. */
    public static Camera camera() {
        return camera == Camera.FRONT && CallVideoFrontCamera.isAvailable() ? Camera.FRONT : Camera.BACK;
    }

    public static boolean isFrontCameraAvailable() {
        return CallVideoFrontCamera.isAvailable();
    }

    public static void toggleCamera() {
        long now = System.currentTimeMillis();
        if (now - lastCameraSwitchMillis < CAMERA_SWITCH_COOLDOWN_MILLIS)
            return;
        lastCameraSwitchMillis = now;
        camera = camera == Camera.BACK && CallVideoFrontCamera.isAvailable() ? Camera.FRONT : Camera.BACK;
        // The next frame shows a different place entirely: start a fresh palette right away.
        if (encoder != null) {
            CallVideoCodec.Encoder enc = encoder;
            submitQuietly(enc::requestKeyframe);
        }
    }

    public static void toggleEnabled() {
        ClientConfig.callCameraVideo = !ClientConfig.callCameraVideo;
        savePrefs();
        sendPrefs();
        if (!ClientConfig.callCameraVideo) {
            stopCapturing(true);
            for (Stream stream : REMOTES.values())
                stream.close();
            REMOTES.clear();
        }
    }

    private static void sendPrefs() {
        if (helloSent && Minecraft.getInstance().getConnection() != null)
            NetworkAccess.sendToServer(new CallVideoC2SPacket(CallVideoC2SPacket.PREFS, ClientConfig.callCameraVideo ? 1 : 0, null));
    }

    // ---- Lifecycle --------------------------------------------------------------------------------------------

    /** The call ended (or video was refused): free every picture and stop the camera. Keeps the handshake. */
    private static void endCall() {
        if (capturing)
            stopCapturing(false);
        CallVideoCapture.release();
        demandFps = 0;
        camera = Camera.BACK;
        for (Stream stream : REMOTES.values())
            stream.close();
        REMOTES.clear();
        if (self != null) {
            self.close();
            self = null;
        }
    }

    private static void resetAll() {
        endCall();
        helloSent = false;
        serverAllowed = false;
        disabledForSession = false;
        selfiePoseSent = false;
    }

    private static void submitQuietly(Runnable task) {
        try {
            WORKER.execute(task);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private static Path prefsFile(Minecraft mc) {
        return mc.gameDirectory.toPath().resolve("config").resolve("crazyphone-client.properties");
    }

    private static void loadPrefs(Minecraft mc) {
        prefsLoaded = true;
        Path file = prefsFile(mc);
        if (!Files.isRegularFile(file))
            return;
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            ClientConfig.callCameraVideo = !"false".equalsIgnoreCase(props.getProperty("callCameraVideo", "true").trim());
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not read {}", file, e);
        }
    }

    private static void savePrefs() {
        Path file = prefsFile(Minecraft.getInstance());
        Properties props = new Properties();
        try {
            if (Files.isRegularFile(file))
                try (InputStream in = Files.newInputStream(file)) {
                    props.load(in);
                }
            props.setProperty("callCameraVideo", Boolean.toString(ClientConfig.callCameraVideo));
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "CrazyPhone client settings");
            }
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warn("Could not write {}", file, e);
        }
    }

    private static void logStats() {
        double seconds = (System.nanoTime() - statSince) / 1e9;
        LOGGER.info("[call video] sent {} frames ({} B, {} B/s, {} B/frame), received {} frames ({} B/s), avg capture {} us, avg encode {} us, demand {} fps, camera {}",
                statFrames, statBytes, Math.round(statBytes / seconds), statFrames == 0 ? 0 : statBytes / statFrames,
                statReceived, Math.round(statReceivedBytes / seconds),
                statFrames == 0 ? 0 : statCaptureNanos / 1000 / Math.max(1, statFrames), statFrames == 0 ? 0 : statEncodeNanos / 1000 / Math.max(1, statFrames),
                demandFps, camera);
        statFrames = statBytes = statCaptureNanos = statEncodeNanos = statReceived = statReceivedBytes = 0;
        statSince = System.nanoTime();
    }

    // ---- One decoded picture stream (a remote participant, or yourself) ---------------------------------------

    private static final class Stream {
        private final UUID id;
        private final CallVideoCodec.Decoder decoder = new CallVideoCodec.Decoder();
        private byte[] pending;
        private boolean decoding;
        private boolean closed;
        private DynamicTexture texture;
        private /*$ res_loc {*/ResourceLocation/*$}*/ textureId;
        private int width, height;
        private long lastFrameMillis;

        Stream(UUID id) {
            this.id = id;
        }

        /** Render thread. The newest frame replaces any older one still waiting: decoding never falls behind. */
        void offer(byte[] data) {
            if (closed)
                return;
            synchronized (this) {
                pending = data;
            }
            if (decoding)
                return;
            decoding = true;
            try {
                WORKER.execute(this::decodeLoop);
            } catch (RejectedExecutionException e) {
                decoding = false;
            }
        }

        private void decodeLoop() {
            byte[] data;
            synchronized (this) {
                data = pending;
                pending = null;
            }
            CallVideoCodec.Decoder.Frame frame = null;
            try {
                if (data != null)
                    frame = decoder.decode(data);
            } catch (Throwable t) {
                LOGGER.debug("Call video decode failed", t);
            }
            CallVideoCodec.Decoder.Frame result = frame;
            Minecraft.getInstance().execute(() -> {
                decoding = false;
                if (result != null && !closed)
                    upload(result);
                byte[] next;
                synchronized (this) {
                    next = pending;
                }
                if (next != null && !closed)
                    offer(next);
            });
        }

        private void upload(CallVideoCodec.Decoder.Frame frame) {
            try {
                if (texture == null || frame.width() != width || frame.height() != height) {
                    releaseTexture();
                    NativeImage image = new NativeImage(frame.width(), frame.height(), false);
                    width = frame.width();
                    height = frame.height();
                    String name = "crazyphone_call_video_" + id.toString().replace("-", "");
                    //? if <1.21.10 {
                    texture = new DynamicTexture(image);
                    texture.setFilter(false, false);
                    textureId = Minecraft.getInstance().getTextureManager().register(name, texture);
                    //?} else {
                    /*textureId = fr.lordfinn.crazyphone.Crazyphone.resource(name);
                    texture = new DynamicTexture(textureId::toString, image);
                    Minecraft.getInstance().getTextureManager().register(textureId, texture);
                    *///?}
                }
                NativeImage pixels = texture.getPixels();
                if (pixels == null)
                    return;
                int[] rgb = frame.rgb();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int c = rgb[y * width + x];
                        int abgr = 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
                        //? if <1.21.10 {
                        pixels.setPixelRGBA(x, y, abgr);
                        //?} else {
                        /*pixels.setPixelABGR(x, y, abgr);
                        *///?}
                    }
                }
                texture.upload();
                lastFrameMillis = System.currentTimeMillis();
            } catch (Throwable t) {
                LOGGER.warn("Call video texture upload failed", t);
                releaseTexture();
            }
        }

        private void releaseTexture() {
            if (textureId != null) {
                try {
                    Minecraft.getInstance().getTextureManager().release(textureId);
                } catch (Throwable ignored) {
                }
            }
            texture = null;
            textureId = null;
        }

        /** Render thread. */
        void close() {
            closed = true;
            pending = null;
            releaseTexture();
            submitQuietly(decoder::close);
        }
    }
}
