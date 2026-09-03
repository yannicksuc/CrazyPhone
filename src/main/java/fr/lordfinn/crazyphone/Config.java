package fr.lordfinn.crazyphone;

//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
//?}

//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(modid = Crazyphone.MODID, bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber(modid = Crazyphone.MODID)
*///?}
//?}
//? if neoforge {
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

    private static final ModConfigSpec.IntValue MAX_PHOTOS_STORED_PER_OWNER = BUILDER
            .comment("Maximum number of photos (both resolutions) kept on disk per owning phone number - the oldest is discarded once a new one exceeds this, independent of conversation history trimming.")
            .defineInRange("maxPhotosStoredPerOwner", 300, 10, 5000);

    private static final ModConfigSpec.IntValue PHOTO_THUMBNAIL_PIXEL_HEIGHT = BUILDER
            .comment("Target height in pixels for a photo's low-quality preview (thumbnails, chat bubbles) - lower looks more like pixel art, higher looks closer to the full photo. 0 disables the separate preview entirely (the full photo is reused as-is, so nothing extra is stored). If the photo's own height is already shorter than this, no resize happens either - a photo is never upscaled for its preview.")
            .defineInRange("photoThumbnailPixelHeight", 16, 0, 256);

    private static final ModConfigSpec.IntValue PHOTO_FULL_MAX_DIMENSION = BUILDER
            .comment("Maximum size in pixels, on the longer side, for a photo's full-quality version (fetched on demand when a photo is opened full-size) - independent of the player's actual render resolution. Higher looks sharper but costs more storage/network per photo.")
            .defineInRange("photoFullMaxDimension", 1024, 64, 4096);

    private static final ModConfigSpec.IntValue PHOTO_FULL_MAX_UPLOAD_BYTES = BUILDER
            .comment("Server-side ceiling, in bytes, on a photo's full-quality upload (defense in depth against a modified client - the real client already stays under this by construction, via photoFullMaxDimension). Raise this if you raise photoFullMaxDimension high enough that legitimate uploads start getting rejected.")
            .defineInRange("photoFullMaxUploadBytes", 4_000_000, 100_000, 50_000_000);

    private static final ModConfigSpec.BooleanValue MAYOR_ELECTION_FEATURE_ENABLED = BUILDER
            .comment("Whether the mayor election/voting feature (accessible from the phone) is enabled.")
            .define("mayorElectionFeatureEnabled", true);

    // Per-feature global switches - see fr.lordfinn.crazyphone.FeatureFlag. Each also has a matching
    // permission node (crazyphone.feature.<name>) for per-player/per-group restriction on top of this
    // global on/off; both can be changed live with /crazyphone feature, not just at startup.

    private static final ModConfigSpec.BooleanValue CALLS_FEATURE_ENABLED = BUILDER
            .comment("Whether voice calls are enabled. Has no effect if Simple Voice Chat isn't installed or voicechatIntegrationEnabled is false.")
            .define("callsFeatureEnabled", true);

    private static final ModConfigSpec.BooleanValue VOICE_MESSAGES_FEATURE_ENABLED = BUILDER
            .comment("Whether recording and sending voice messages is enabled. Has no effect if Simple Voice Chat isn't installed or voicechatIntegrationEnabled is false.")
            .define("voiceMessagesFeatureEnabled", true);

    private static final ModConfigSpec.BooleanValue IMAGES_FEATURE_ENABLED = BUILDER
            .comment("Whether sending images from the phone's album into a conversation is enabled.")
            .define("imagesFeatureEnabled", true);

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

    private static final ModConfigSpec.IntValue PHONE_DROP_GRACE_SECONDS = BUILDER
            .comment("How long a player can be without their phone (dropped, or moved to another inventory) during a call before it actually ends - picking it back up within this window keeps the call going uninterrupted.")
            .defineInRange("phoneDropGraceSeconds", 5, 0, 60);

    private static final ModConfigSpec.IntValue MAX_VOICE_MESSAGES_STORED_PER_CONVERSATION = BUILDER
            .comment("Maximum number of voice messages (with their audio) kept on disk per conversation - voice audio is the heaviest payload, capped separately from text/image messages.")
            .defineInRange("maxVoiceMessagesStoredPerConversation", 30, 5, 500);

    private static final ModConfigSpec.IntValue MAX_VOICE_MESSAGE_RECORDING_SECONDS = BUILDER
            .comment("Maximum length of a single voice message recording, in seconds - recording auto-stops once this is reached.")
            .defineInRange("maxVoiceMessageRecordingSeconds", 60, 5, 600);

    // The Soulbound enchantment (Ancient City loot only) keeps enchanted tools/the phone out of death drops
    // and back in the owner's inventory on respawn - see fr.lordfinn.crazyphone.enchantment.SoulboundHandler.
    // This toggle disables that behavior server-wide (the enchantment can still be found/applied, it just
    // stops doing anything) without needing to touch the loot tables or item tags.

    private static final ModConfigSpec.BooleanValue SOULBOUND_ENCHANTMENT_ENABLED = BUILDER
            .comment("Whether the Soulbound enchantment actually keeps enchanted items on death. Does not affect whether it can still be found or applied.")
            .define("soulboundEnchantmentEnabled", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int maxStoredMessagesPerConversation;
    public static int maxMessagesSentPerRequest;
    public static int maxImagesStoredPerConversation;
    public static int maxPhotosStoredPerOwner;
    public static int photoThumbnailPixelHeight;
    public static int photoFullMaxDimension;
    public static int photoFullMaxUploadBytes;
    public static boolean mayorElectionFeatureEnabled;
    public static boolean callsFeatureEnabled;
    public static boolean voiceMessagesFeatureEnabled;
    public static boolean imagesFeatureEnabled;
    public static boolean voicechatIntegrationEnabled;
    public static int callRingTimeoutSeconds;
    public static int aloneInCallKickSeconds;
    public static int phoneDropGraceSeconds;
    public static int maxVoiceMessagesStoredPerConversation;
    public static int maxVoiceMessageRecordingSeconds;
    public static boolean soulboundEnchantmentEnabled;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        maxStoredMessagesPerConversation = MAX_STORED_MESSAGES_PER_CONVERSATION.get();
        maxMessagesSentPerRequest = MAX_MESSAGES_SENT_PER_REQUEST.get();
        maxImagesStoredPerConversation = MAX_IMAGES_STORED_PER_CONVERSATION.get();
        maxPhotosStoredPerOwner = MAX_PHOTOS_STORED_PER_OWNER.get();
        photoThumbnailPixelHeight = PHOTO_THUMBNAIL_PIXEL_HEIGHT.get();
        photoFullMaxDimension = PHOTO_FULL_MAX_DIMENSION.get();
        photoFullMaxUploadBytes = PHOTO_FULL_MAX_UPLOAD_BYTES.get();
        mayorElectionFeatureEnabled = MAYOR_ELECTION_FEATURE_ENABLED.get();
        callsFeatureEnabled = CALLS_FEATURE_ENABLED.get();
        voiceMessagesFeatureEnabled = VOICE_MESSAGES_FEATURE_ENABLED.get();
        imagesFeatureEnabled = IMAGES_FEATURE_ENABLED.get();
        voicechatIntegrationEnabled = VOICECHAT_INTEGRATION_ENABLED.get();
        callRingTimeoutSeconds = CALL_RING_TIMEOUT_SECONDS.get();
        aloneInCallKickSeconds = ALONE_IN_CALL_KICK_SECONDS.get();
        phoneDropGraceSeconds = PHONE_DROP_GRACE_SECONDS.get();
        maxVoiceMessagesStoredPerConversation = MAX_VOICE_MESSAGES_STORED_PER_CONVERSATION.get();
        maxVoiceMessageRecordingSeconds = MAX_VOICE_MESSAGE_RECORDING_SECONDS.get();
        soulboundEnchantmentEnabled = SOULBOUND_ENCHANTMENT_ENABLED.get();
    }

    /** Setters for the toggleable features below, used by /crazyphone feature to change them at runtime
     * (not just at startup). ConfigValue#set() ALONE only updates its own internal cache and the in-memory
     * backing config - per its own javadoc, it does so "without firing events or writing the config to
     * disk". Config.onLoad (which populates the mirror fields FeatureFlag actually reads) only re-fires, and
     * the TOML file only gets rewritten, once SPEC.save() is called afterward - a real bug found by
     * mayorVote_whileFeatureGloballyDisabled_isBlocked (a GameTest), where set() alone left the mirror field
     * stale and the vote went through anyway. */
    public static void setMayorElectionFeatureEnabled(boolean enabled) {
        MAYOR_ELECTION_FEATURE_ENABLED.set(enabled);
        SPEC.save();
    }

    public static void setCallsFeatureEnabled(boolean enabled) {
        CALLS_FEATURE_ENABLED.set(enabled);
        SPEC.save();
    }

    public static void setVoiceMessagesFeatureEnabled(boolean enabled) {
        VOICE_MESSAGES_FEATURE_ENABLED.set(enabled);
        SPEC.save();
    }

    public static void setImagesFeatureEnabled(boolean enabled) {
        IMAGES_FEATURE_ENABLED.set(enabled);
        SPEC.save();
    }

}
//?}
//? if fabric {
/*// TODO(#164 follow-up): no real Fabric config file yet - NeoForge's ModConfigSpec (TOML, per-value
// comments/ranges, live reload) has no Fabric equivalent in this codebase yet. Values below are the
// same hardcoded defaults as the NeoForge TOML's defaults, just not server-operator-configurable on
// Fabric yet. Setters only flip the in-memory value (no persistence to disk), matching FeatureFlag's
// Fabric placeholder for the same reason.
public class Config {
    public static int maxStoredMessagesPerConversation = 300;
    public static int maxMessagesSentPerRequest = 100;
    public static int maxImagesStoredPerConversation = 50;
    public static int maxPhotosStoredPerOwner = 300;
    public static int photoThumbnailPixelHeight = 16;
    public static int photoFullMaxDimension = 1024;
    public static int photoFullMaxUploadBytes = 4_000_000;
    public static boolean mayorElectionFeatureEnabled = true;
    public static boolean callsFeatureEnabled = true;
    public static boolean voiceMessagesFeatureEnabled = true;
    public static boolean imagesFeatureEnabled = true;
    public static boolean voicechatIntegrationEnabled = true;
    public static int callRingTimeoutSeconds = 30;
    public static int aloneInCallKickSeconds = 5;
    public static int phoneDropGraceSeconds = 5;
    public static int maxVoiceMessagesStoredPerConversation = 30;
    public static int maxVoiceMessageRecordingSeconds = 60;
    public static boolean soulboundEnchantmentEnabled = true;

    public static void setMayorElectionFeatureEnabled(boolean enabled) {
        mayorElectionFeatureEnabled = enabled;
    }

    public static void setCallsFeatureEnabled(boolean enabled) {
        callsFeatureEnabled = enabled;
    }

    public static void setVoiceMessagesFeatureEnabled(boolean enabled) {
        voiceMessagesFeatureEnabled = enabled;
    }

    public static void setImagesFeatureEnabled(boolean enabled) {
        imagesFeatureEnabled = enabled;
    }
}
*///?}
