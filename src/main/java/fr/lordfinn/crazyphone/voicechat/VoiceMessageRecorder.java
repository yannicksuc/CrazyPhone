package fr.lordfinn.crazyphone.voicechat;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Captures the local player's mic audio into an in-progress voice message while the conversation screen's
 * recording UI is open.
 *
 * <p>Captures via LWJGL's OpenAL capture API (already part of the game's own audio stack - Minecraft itself
 * is built on LWJGL+OpenAL) rather than the JDK's {@code javax.sound.sampled}: Simple Voice Chat's own
 * client config documents a {@code java_microphone_implementation} toggle specifically because its DEFAULT
 * microphone capture is OpenAL-based, with javax.sound as an opt-in fallback for machines where OpenAL
 * capture misbehaves - i.e. OpenAL is the well-supported path on this platform, not javax.sound. Also reads
 * SVC's own {@code config/voicechat/voicechat-client.properties} for its configured {@code microphone}
 * device name (if the player picked a specific one rather than "default") so this opens the exact same
 * physical device SVC itself uses, rather than risking OpenAL's "default" resolving to something else.
 *
 * <p>This still doesn't hook SVC's transmission pipeline (e.g. {@code ClientSoundEvent}, which only fires
 * for frames SVC is actually about to send - never while muted or using push-to-talk without the key held,
 * and there is no supported way for an addon to override either). Capturing independently via OpenAL means
 * voice-message recording keeps working regardless of the player's SVC mute/push-to-talk state. Recording
 * format is 48kHz mono 16-bit PCM, matching what {@link SvcCallBridge#playAudioToPlayer} feeds into SVC's
 * own encoder for playback.
 */
public final class VoiceMessageRecorder {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    private static final int SAMPLE_RATE = 48000;
    /** 1 second of headroom between capture-loop poll cycles - generous, capture is polled continuously. */
    private static final int CAPTURE_BUFFER_SAMPLES = 48000;

    private static volatile boolean recording = false;
    private static long captureDevice = 0L;
    private static Thread captureThread;
    private static ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private static long totalSamplesRead = 0;

    /** Rolling levels for the live waveform bars, most recent last. */
    private static final int LEVEL_HISTORY = 24;
    private static final float[] recentLevels = new float[LEVEL_HISTORY];
    private static int levelWriteIndex = 0;

    private VoiceMessageRecorder() {
    }

    /** No-op (silent, empty recording) if no capture device is available at all - the UI already gates this
     * feature behind SVC being installed, so a missing mic is an edge case, not something worth a hard
     * failure. Every decision point is logged (crazyphone logger, INFO/WARN) since a silent capture failure
     * is otherwise indistinguishable from "the player just isn't talking". */
    public static void startRecording() {
        buffer = new ByteArrayOutputStream();
        totalSamplesRead = 0;
        java.util.Arrays.fill(recentLevels, 0f);
        levelWriteIndex = 0;

        captureDevice = openCaptureDevice();
        if (captureDevice == 0L) {
            LOGGER.warn("Voice message recording: could not open any OpenAL capture device");
            return;
        }

        ALC11.alcCaptureStart(captureDevice);
        recording = true;
        captureThread = new Thread(VoiceMessageRecorder::captureLoop, "crazyphone-voice-capture");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    /** Prefers the exact microphone SVC itself is configured to use (read from its own client config file -
     * the same device the player already talks through normally) over OpenAL's "default" capture device,
     * which isn't guaranteed to resolve to the same physical device on every system. */
    private static long openCaptureDevice() {
        String configuredDevice = readSvcConfiguredMicrophoneName();
        if (configuredDevice != null && !configuredDevice.isBlank()) {
            long device = ALC11.alcCaptureOpenDevice(configuredDevice, SAMPLE_RATE, AL10.AL_FORMAT_MONO16, CAPTURE_BUFFER_SAMPLES);
            if (device != 0L) {
                LOGGER.info("Voice message recording: opened SVC's configured microphone '{}'", configuredDevice);
                return device;
            }
            LOGGER.warn("Voice message recording: SVC's configured microphone '{}' could not be opened by OpenAL, falling back to the default device", configuredDevice);
        }
        long device = ALC11.alcCaptureOpenDevice((CharSequence) null, SAMPLE_RATE, AL10.AL_FORMAT_MONO16, CAPTURE_BUFFER_SAMPLES);
        if (device != 0L)
            LOGGER.info("Voice message recording: opened the default OpenAL capture device");
        else
            LOGGER.warn("Voice message recording: OpenAL reported no usable capture device at all");
        return device;
    }

    private static String readSvcConfiguredMicrophoneName() {
        Path configPath = Path.of("config", "voicechat", "voicechat-client.properties");
        if (!Files.exists(configPath))
            return null;
        try (InputStream in = Files.newInputStream(configPath)) {
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("microphone");
        } catch (IOException e) {
            LOGGER.warn("Voice message recording: could not read SVC's client config ({})", e.getMessage());
            return null;
        }
    }

    private static void captureLoop() {
        short[] chunk = new short[2048];
        long device = captureDevice;
        long lastLogMs = System.currentTimeMillis();
        while (recording && device != 0L) {
            int available = ALC10.alcGetInteger(device, ALC11.ALC_CAPTURE_SAMPLES);
            if (available <= 0) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                continue;
            }
            int toRead = Math.min(available, chunk.length);
            ALC11.alcCaptureSamples(device, chunk, toRead);
            appendSamples(chunk, toRead);
            totalSamplesRead += toRead;

            long now = System.currentTimeMillis();
            if (now - lastLogMs > 1000) {
                LOGGER.info("Voice message recording: {} samples captured so far", totalSamplesRead);
                lastLogMs = now;
            }
        }
    }

    private static void appendSamples(short[] samples, int count) {
        byte[] bytes = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            short sample = samples[i];
            bytes[2 * i] = (byte) (sample & 0xFF);
            bytes[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        synchronized (buffer) {
            buffer.write(bytes, 0, bytes.length);
        }
        pushLevel(computeLevel(samples, count));
    }

    /** Stops capturing and returns the collected samples so far (little-endian 16-bit PCM, mono, 48kHz). */
    public static byte[] stopRecording() {
        recording = false;
        closeDevice();
        joinCaptureThread();
        synchronized (buffer) {
            return buffer.toByteArray();
        }
    }

    public static void discard() {
        recording = false;
        closeDevice();
        joinCaptureThread();
        buffer = new ByteArrayOutputStream();
    }

    private static void closeDevice() {
        if (captureDevice == 0L)
            return;
        ALC11.alcCaptureStop(captureDevice);
        ALC11.alcCaptureCloseDevice(captureDevice);
        captureDevice = 0L;
    }

    private static void joinCaptureThread() {
        if (captureThread == null)
            return;
        try {
            captureThread.join(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        captureThread = null;
    }

    public static boolean isRecording() {
        return recording;
    }

    /** Derived from actual captured sample count, not wall-clock time - stays accurate even if the capture
     * loop hitches, and is what the recording-length cap (maxVoiceMessageRecordingSeconds) checks against. */
    public static float getElapsedSeconds() {
        return totalSamplesRead / (float) SAMPLE_RATE;
    }

    /** Oldest first, newest last - matches left-to-right chronological reading of the waveform bars. */
    public static float[] getRecentLevels() {
        float[] ordered = new float[LEVEL_HISTORY];
        for (int i = 0; i < LEVEL_HISTORY; i++) {
            ordered[i] = recentLevels[(levelWriteIndex + i) % LEVEL_HISTORY];
        }
        return ordered;
    }

    private static void pushLevel(float level) {
        recentLevels[levelWriteIndex] = level;
        levelWriteIndex = (levelWriteIndex + 1) % LEVEL_HISTORY;
    }

    /** 0..1 normalized RMS level of this chunk - good enough for a live waveform bar height, not a real
     * decibel measurement. */
    private static float computeLevel(short[] samples, int count) {
        if (count == 0)
            return 0f;
        double sumSquares = 0;
        for (int i = 0; i < count; i++) {
            sumSquares += (double) samples[i] * samples[i];
        }
        double rms = Math.sqrt(sumSquares / count);
        return (float) Math.min(1.0, rms / 12000.0);
    }

    /**
     * Downsamples the whole recording's amplitude envelope into a fixed number of buckets (0-255 each) -
     * cheap enough to embed directly in the message's lightweight metadata (like the duration), so a
     * sent-message widget can animate a "live" waveform in sync with playback without ever needing the
     * actual audio bytes client-side.
     */
    public static byte[] computeEnvelope(byte[] pcm, int bucketCount) {
        byte[] envelope = new byte[bucketCount];
        int totalSamples = pcm.length / 2;
        if (totalSamples == 0)
            return envelope;
        int samplesPerBucket = Math.max(1, totalSamples / bucketCount);
        for (int bucket = 0; bucket < bucketCount; bucket++) {
            int start = bucket * samplesPerBucket;
            int end = Math.min(totalSamples, start + samplesPerBucket);
            double sumSquares = 0;
            int count = 0;
            for (int i = start; i < end; i++) {
                short sample = (short) ((pcm[2 * i] & 0xFF) | (pcm[2 * i + 1] << 8));
                sumSquares += (double) sample * sample;
                count++;
            }
            float level = count == 0 ? 0f : (float) Math.min(1.0, Math.sqrt(sumSquares / count) / 12000.0);
            envelope[bucket] = (byte) Math.round(level * 255);
        }
        return envelope;
    }
}
