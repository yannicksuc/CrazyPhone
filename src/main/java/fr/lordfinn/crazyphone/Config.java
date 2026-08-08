package fr.lordfinn.crazyphone;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = Crazyphone.MODID)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Caps below exist to keep synced/persisted phone data bounded regardless of how long a server has run.
    // Without them, conversation history and per-phone data grow forever and every player join / message
    // resyncs the whole blob, which is what used to crash the server on connect as worlds aged.

    private static final ModConfigSpec.IntValue MAX_STORED_MESSAGES_PER_CONVERSATION = BUILDER
            .comment("Maximum number of messages kept on disk per conversation. Older messages beyond this are discarded when a new one arrives.")
            .defineInRange("maxStoredMessagesPerConversation", 300, 10, 10000);

    private static final ModConfigSpec.IntValue MAX_MESSAGES_SENT_PER_REQUEST = BUILDER
            .comment("Maximum number of messages sent to a client in one page when it opens/scrolls a conversation.")
            .defineInRange("maxMessagesSentPerRequest", 100, 10, 1000);

    private static final ModConfigSpec.IntValue MAX_IMAGES_STORED_PER_CONVERSATION = BUILDER
            .comment("Maximum number of image messages kept on disk per conversation (images are the heaviest payload, capped separately from text messages).")
            .defineInRange("maxImagesStoredPerConversation", 50, 5, 2000);

    private static final ModConfigSpec.IntValue MAX_ALBUM_SLOTS_PER_PHONE = BUILDER
            .comment("Number of album/photo storage slots available in a phone's internal inventory.")
            .defineInRange("maxAlbumSlotsPerPhone", 27, 1, 97);

    private static final ModConfigSpec.BooleanValue MAYOR_ELECTION_FEATURE_ENABLED = BUILDER
            .comment("Whether the mayor election/voting feature (accessible from the phone) is enabled.")
            .define("mayorElectionFeatureEnabled", true);

    // Optional Simple Voice Chat integration (calls + voice messages) - see fr.lordfinn.crazyphone.voicechat.

    private static final ModConfigSpec.BooleanValue VOICECHAT_INTEGRATION_ENABLED = BUILDER
            .comment("Master switch for the Simple Voice Chat integration (calls + voice messages). Has no effect if Simple Voice Chat itself isn't installed.")
            .define("voicechatIntegrationEnabled", true);

    private static final ModConfigSpec.IntValue CALL_RING_TIMEOUT_SECONDS = BUILDER
            .comment("How long a call rings before an unanswered callee is treated as a missed call. Distinct from aloneInCallKickSeconds, which only applies once a call is actually connected.")
            .defineInRange("callRingTimeoutSeconds", 30, 5, 120);

    private static final ModConfigSpec.IntValue ALONE_IN_CALL_KICK_SECONDS = BUILDER
            .comment("How long a call stays open with only one participant left before that participant is automatically removed from it.")
            .defineInRange("aloneInCallKickSeconds", 5, 1, 60);

    private static final ModConfigSpec.IntValue MAX_VOICE_MESSAGES_STORED_PER_CONVERSATION = BUILDER
            .comment("Maximum number of voice messages (with their audio) kept on disk per conversation - voice audio is the heaviest payload, capped separately from text/image messages.")
            .defineInRange("maxVoiceMessagesStoredPerConversation", 30, 5, 500);

    private static final ModConfigSpec.IntValue MAX_VOICE_MESSAGE_RECORDING_SECONDS = BUILDER
            .comment("Maximum length of a single voice message recording, in seconds - recording auto-stops once this is reached.")
            .defineInRange("maxVoiceMessageRecordingSeconds", 60, 5, 600);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int maxStoredMessagesPerConversation;
    public static int maxMessagesSentPerRequest;
    public static int maxImagesStoredPerConversation;
    public static int maxAlbumSlotsPerPhone;
    public static boolean mayorElectionFeatureEnabled;
    public static boolean voicechatIntegrationEnabled;
    public static int callRingTimeoutSeconds;
    public static int aloneInCallKickSeconds;
    public static int maxVoiceMessagesStoredPerConversation;
    public static int maxVoiceMessageRecordingSeconds;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        maxStoredMessagesPerConversation = MAX_STORED_MESSAGES_PER_CONVERSATION.get();
        maxMessagesSentPerRequest = MAX_MESSAGES_SENT_PER_REQUEST.get();
        maxImagesStoredPerConversation = MAX_IMAGES_STORED_PER_CONVERSATION.get();
        maxAlbumSlotsPerPhone = MAX_ALBUM_SLOTS_PER_PHONE.get();
        mayorElectionFeatureEnabled = MAYOR_ELECTION_FEATURE_ENABLED.get();
        voicechatIntegrationEnabled = VOICECHAT_INTEGRATION_ENABLED.get();
        callRingTimeoutSeconds = CALL_RING_TIMEOUT_SECONDS.get();
        aloneInCallKickSeconds = ALONE_IN_CALL_KICK_SECONDS.get();
        maxVoiceMessagesStoredPerConversation = MAX_VOICE_MESSAGES_STORED_PER_CONVERSATION.get();
        maxVoiceMessageRecordingSeconds = MAX_VOICE_MESSAGE_RECORDING_SECONDS.get();
    }
}
