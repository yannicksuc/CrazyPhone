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
import java.util.concurrent.ConcurrentHashMap;

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
    /** Every call's Group object, kept from the moment groupBuilder().build() hands it back - NOT re-looked-up
     * via serverApi.getGroup(id) later. Call groups are created non-persistent (they're session-only, same as
     * CallRegistry itself), and getGroup(id) on this SVC version returns a non-null Group wrapper around a
     * null internal reference for a non-persistent group, which then NPEs inside connection.setGroup(group) -
     * the builder's own returned reference is the only reliably usable handle to it. */
    private static final Map<UUID, Group> ACTIVE_GROUPS = new HashMap<>();

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

    /** Client-side "currently audible" per OTHER player, stamped by {@link #markReceivedSound} from the
     * ClientReceiveSoundEvents the plugin registers (see CrazyPhoneVoicechatPlugin#registerEvents). SVC's own
     * {@code VoicechatClientApi#isTalking(UUID)} can't be used for this: in 2.6.22 it is a stub for any
     * non-null id - it calls into SVC's internal TalkCache and then discards the result, returning false
     * unconditionally (confirmed against the real jar's bytecode: {@code invokevirtual TalkCache.isTalking;
     * pop; iconst_0; ireturn}) - which is exactly why the yellow border never showed for anyone. Same
     * quarter-second hold after the last packet SVC's own HUD icon uses. */
    private static final Map<UUID, Long> LAST_SOUND_MILLIS = new ConcurrentHashMap<>();
    private static final long TALKING_HOLD_MILLIS = 250;

    public static void markReceivedSound(UUID senderId) {
        if (senderId != null)
            LAST_SOUND_MILLIS.put(senderId, System.currentTimeMillis());
    }

    /** Live talking indicator for ANOTHER player (the InCall screen's yellow "speaking" border) - for the local
     * player use {@link #isLocalTalking()}, their own voice never comes back as a received sound. */
    public static boolean isTalking(UUID playerId) {
        Long last = LAST_SOUND_MILLIS.get(playerId);
        return last != null && System.currentTimeMillis() - last < TALKING_HOLD_MILLIS;
    }

    /** Whether the LOCAL player's own microphone is currently picking up speech - the no-arg
     * {@code VoicechatClientApi#isTalking()} (mic thread), the one variant that actually works in 2.6.22.
     * Null-safe so it can be polled every frame without the caller worrying about client-API init timing. */
    public static boolean isLocalTalking() {
        return clientApi != null && clientApi.isTalking();
    }

    /** Whether the LOCAL player has muted their own microphone in Simple Voice Chat's own settings -
     * {@code VoicechatClientApi#isMuted()} ("if the local voice chat is muted", no player-id parameter since
     * it can only ever mean the local client), not to be confused with {@code isDisabled()} which reflects
     * voice chat being turned off entirely rather than just muted. Null-safe like {@link #isTalking(UUID)},
     * for the InCall screen's own "you're muted" warning icon. */
    public static boolean isMicMuted() {
        return clientApi != null && clientApi.isMuted();
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
        ACTIVE_GROUPS.put(group.getId(), group);
        return group.getId();
    }

    /** Server-authoritative join - the player never has to touch SVC's own group UI. */
    public static void joinGroup(ServerPlayer player, UUID groupId) {
        if (serverApi == null || groupId == null)
            return;
        VoicechatConnection connection = serverApi.getConnectionOf(player.getUUID());
        Group group = ACTIVE_GROUPS.get(groupId);
        if (connection != null && group != null)
            connection.setGroup(group);
    }

    /** Takes a bare UUID, not a ServerPlayer, so it also works for a player who's already fully logged out
     * by the time CallRegistry gets around to cleaning up their call membership (see the periodic sweep in
     * CallTerminationListener, which only ever has a UUID for someone PlayerList#getPlayer can't find
     * anymore) - getConnectionOf only ever needed the UUID internally anyway. */
    public static void leaveGroup(UUID playerId) {
        if (serverApi == null)
            return;
        VoicechatConnection connection = serverApi.getConnectionOf(playerId);
        if (connection != null)
            connection.setGroup(null);
    }

    public static void removeGroup(UUID groupId) {
        if (serverApi == null || groupId == null)
            return;
        ACTIVE_GROUPS.remove(groupId);
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
