package fr.lordfinn.crazyphone.voicechat;

import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every direct call into the Simple Voice Chat API goes through here. This class references SVC types at
 * the top level, so it must never be loaded unless {@link VoicechatIntegration#isAvailable()} has already
 * returned true - see that class's javadoc for why that's safe. Populated once by
 * {@link CrazyPhoneVoicechatPlugin} when SVC starts up; everything else in the mod reads/calls through here
 * instead of touching the plugin/event-registration objects directly.
 */
public final class SvcCallBridge {
    private static VoicechatServerApi serverApi;
    private static VoicechatClientApi clientApi;
    /** The in-progress voice-message playback for each player who has one running, if any - lets a
     * follow-up "pause" click actually stop it (there's no other handle to it once startPlaying() returns,
     * and only one voice message plays at a time per player by construction of the conversation UI). */
    private static final Map<UUID, AudioPlayer> ACTIVE_VOICE_MESSAGE_PLAYBACK = new HashMap<>();

    private SvcCallBridge() {
    }

    static void setServerApi(VoicechatServerApi api) {
        serverApi = api;
    }

    static void setClientApi(VoicechatClientApi api) {
        clientApi = api;
    }

    public static VoicechatClientApi getClientApi() {
        return clientApi;
    }

    /** Creates a fresh, transient, hidden, isolated (no proximity leak either way) group for one call. */
    public static UUID createCallGroup(String name) {
        if (serverApi == null)
            return null;
        Group group = serverApi.groupBuilder()
                .setName(name)
                .setPersistent(false)
                .setHidden(true)
                .setType(Group.Type.ISOLATED)
                .build();
        return group.getId();
    }

    /** Server-authoritative join - the player never has to touch SVC's own group UI. */
    public static void joinGroup(ServerPlayer player, UUID groupId) {
        if (serverApi == null || groupId == null)
            return;
        VoicechatConnection connection = serverApi.getConnectionOf(player.getUUID());
        Group group = serverApi.getGroup(groupId);
        if (connection != null && group != null)
            connection.setGroup(group);
    }

    public static void leaveGroup(ServerPlayer player) {
        if (serverApi == null)
            return;
        VoicechatConnection connection = serverApi.getConnectionOf(player.getUUID());
        if (connection != null)
            connection.setGroup(null);
    }

    public static void removeGroup(UUID groupId) {
        if (serverApi == null || groupId == null)
            return;
        serverApi.removeGroup(groupId);
    }

    /** Whether this player can currently be reached by a voice call at all. */
    public static boolean isCallable(ServerPlayer player) {
        if (serverApi == null)
            return false;
        VoicechatConnection connection = serverApi.getConnectionOf(player.getUUID());
        return connection != null && connection.isInstalled() && connection.isConnected();
    }

    /**
     * Plays a fully-buffered voice message to exactly one player, targeted via a fresh {@link
     * StaticAudioChannel} scoped only to their connection - this is what makes the lazy-fetch requirement
     * work (the audio is never broadcast; it's addressed to the one player who clicked Play). The encoder
     * is created and closed per playback rather than cached, matching SVC's own "close it when you're
     * finished" contract on {@code VoicechatApi#createEncoder()}.
     */
    public static boolean playAudioToPlayer(ServerPlayer player, short[] pcm) {
        if (serverApi == null || pcm.length == 0)
            return false;
        VoicechatConnection connection = serverApi.getConnectionOf(player.getUUID());
        if (connection == null || !connection.isInstalled() || !connection.isConnected())
            return false;

        OpusEncoder encoder = serverApi.createEncoder();
        StaticAudioChannel channel = serverApi.createStaticAudioChannel(java.util.UUID.randomUUID());
        channel.addTarget(connection);
        AudioPlayer audioPlayer = serverApi.createAudioPlayer(channel, encoder, pcm);
        UUID playerId = player.getUUID();
        ACTIVE_VOICE_MESSAGE_PLAYBACK.put(playerId, audioPlayer);
        audioPlayer.setOnStopped(() -> {
            encoder.close();
            ACTIVE_VOICE_MESSAGE_PLAYBACK.remove(playerId, audioPlayer);
        });
        audioPlayer.startPlaying();
        return true;
    }

    /** Stops whatever voice message is currently playing for this player, if any - the click handler for
     * the pause icon on an in-progress playback. No-op if nothing is playing (already finished, or this is
     * a stray click). */
    public static void stopVoiceMessagePlayback(ServerPlayer player) {
        AudioPlayer audioPlayer = ACTIVE_VOICE_MESSAGE_PLAYBACK.remove(player.getUUID());
        if (audioPlayer != null && !audioPlayer.isStopped())
            audioPlayer.stopPlaying();
    }
}
