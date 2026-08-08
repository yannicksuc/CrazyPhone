package fr.lordfinn.crazyphone.data;

import fr.lordfinn.crazyphone.Config;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the actual crash-fix logic: message history must stay bounded on disk (not just "not broadcast"),
 * images are capped separately from text messages, and pagination serves the most recent messages first
 * without ever requiring the whole conversation to be loaded at once.
 */
class ConversationSavedDataTest {

    private static final String CONVO = "111.222";

    @BeforeEach
    void resetConfig() {
        Config.maxStoredMessagesPerConversation = 300;
        Config.maxMessagesSentPerRequest = 100;
        Config.maxImagesStoredPerConversation = 50;
        Config.maxVoiceMessagesStoredPerConversation = 50;
    }

    private static CompoundTag textMessage(String sender, String value, int timecode) {
        CompoundTag tag = new CompoundTag();
        tag.putString("sender", sender);
        tag.putString("value", value);
        tag.putInt("timecode", timecode);
        return tag;
    }

    private static CompoundTag imageMessage(int timecode) {
        CompoundTag tag = textMessage("111", "", timecode);
        tag.put("image", new CompoundTag());
        return tag;
    }

    private static CompoundTag voiceMessage(int timecode, UUID voiceId) {
        CompoundTag tag = textMessage("111", "", timecode);
        CompoundTag voice = new CompoundTag();
        voice.putLong("voice_id_most", voiceId.getMostSignificantBits());
        voice.putLong("voice_id_least", voiceId.getLeastSignificantBits());
        tag.put("voice", voice);
        return tag;
    }

    private static CompoundTag callMessage(UUID callId) {
        CompoundTag tag = textMessage("", "", 0);
        CompoundTag call = new CompoundTag();
        call.putLong("call_id_most", callId.getMostSignificantBits());
        call.putLong("call_id_least", callId.getLeastSignificantBits());
        call.putInt("duration", -1);
        tag.put("call", call);
        return tag;
    }

    @Test
    void appendMessage_storesMessageRetrievableViaGetPage() {
        ConversationSavedData data = new ConversationSavedData();
        data.appendMessage(CONVO, textMessage("111", "hello", 1));

        List<CompoundTag> page = data.getPage(CONVO, 0, 10);
        assertEquals(1, page.size());
        assertEquals("hello", page.get(0).getString("value"));
        assertEquals(1, data.getMessageCount(CONVO));
    }

    @Test
    void appendMessage_trimsOldestMessagesBeyondConfiguredCap() {
        Config.maxStoredMessagesPerConversation = 5;
        ConversationSavedData data = new ConversationSavedData();

        for (int i = 0; i < 8; i++) {
            data.appendMessage(CONVO, textMessage("111", "msg" + i, i));
        }

        // Storage itself must be bounded, not just what's sent over the network - this is the actual fix
        // for the old mod's unbounded-growth crash.
        assertEquals(5, data.getMessageCount(CONVO));

        List<CompoundTag> all = data.getPage(CONVO, 0, 100);
        assertEquals(5, all.size());
        // Oldest 3 (msg0-msg2) must have been dropped; msg3..msg7 remain, oldest-first.
        assertEquals("msg3", all.get(0).getString("value"));
        assertEquals("msg7", all.get(4).getString("value"));
    }

    @Test
    void appendMessage_capsImagesSeparatelyFromTextMessages() {
        Config.maxStoredMessagesPerConversation = 100;
        Config.maxImagesStoredPerConversation = 2;
        ConversationSavedData data = new ConversationSavedData();

        data.appendMessage(CONVO, imageMessage(0));
        data.appendMessage(CONVO, textMessage("111", "text between images", 1));
        data.appendMessage(CONVO, imageMessage(2));
        data.appendMessage(CONVO, imageMessage(3));

        List<CompoundTag> all = data.getPage(CONVO, 0, 100);
        long imageCount = all.stream().filter(t -> t.contains("image")).count();

        // Only the 2 most recent images survive; the text message and message count are otherwise untouched.
        assertEquals(2, imageCount);
        assertEquals(3, all.size(), "the oldest image should be dropped, not the text message");
        assertTrue(all.stream().anyMatch(t -> "text between images".equals(t.getString("value"))));
    }

    @Test
    void getPage_supportsPaginationForLoadingOlderMessages() {
        ConversationSavedData data = new ConversationSavedData();
        for (int i = 0; i < 10; i++) {
            data.appendMessage(CONVO, textMessage("111", "msg" + i, i));
        }

        // First page: most recent 4 messages (msg6..msg9)
        List<CompoundTag> firstPage = data.getPage(CONVO, 0, 4);
        assertEquals(4, firstPage.size());
        assertEquals("msg6", firstPage.get(0).getString("value"));
        assertEquals("msg9", firstPage.get(3).getString("value"));

        // Second page: skip the 4 most recent, get the next 4 back (msg2..msg5)
        List<CompoundTag> secondPage = data.getPage(CONVO, 4, 4);
        assertEquals(4, secondPage.size());
        assertEquals("msg2", secondPage.get(0).getString("value"));
        assertEquals("msg5", secondPage.get(3).getString("value"));
    }

    @Test
    void getPage_forUnknownConversation_returnsEmptyNotNull() {
        ConversationSavedData data = new ConversationSavedData();
        assertTrue(data.getPage("nonexistent", 0, 10).isEmpty());
        assertEquals(0, data.getMessageCount("nonexistent"));
    }

    @Test
    void getPage_pastEndOfHistory_returnsEmpty() {
        ConversationSavedData data = new ConversationSavedData();
        data.appendMessage(CONVO, textMessage("111", "only message", 0));

        List<CompoundTag> page = data.getPage(CONVO, 50, 10);
        assertTrue(page.isEmpty());
    }

    @Test
    void separateConversationsDoNotShareHistory() {
        ConversationSavedData data = new ConversationSavedData();
        data.appendMessage("111.222", textMessage("111", "to 222", 0));
        data.appendMessage("111.333", textMessage("111", "to 333", 0));

        assertEquals(1, data.getMessageCount("111.222"));
        assertEquals(1, data.getMessageCount("111.333"));
        assertEquals("to 222", data.getPage("111.222", 0, 10).get(0).getString("value"));
        assertEquals("to 333", data.getPage("111.333", 0, 10).get(0).getString("value"));
    }

    @Test
    void save_and_load_roundTripsAllConversations() {
        ConversationSavedData data = new ConversationSavedData();
        data.appendMessage(CONVO, textMessage("111", "hello", 0));
        data.appendMessage(CONVO, textMessage("222", "hi back", 1));

        CompoundTag saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        ConversationSavedData loaded = ConversationSavedData.load(saved, RegistryAccess.EMPTY);

        assertEquals(2, loaded.getMessageCount(CONVO));
        List<CompoundTag> page = loaded.getPage(CONVO, 0, 10);
        assertEquals("hello", page.get(0).getString("value"));
        assertEquals("hi back", page.get(1).getString("value"));
    }

    @Test
    void storeVoiceAudio_and_getVoiceAudio_roundTrips() {
        ConversationSavedData data = new ConversationSavedData();
        UUID voiceId = UUID.randomUUID();
        byte[] pcm = {1, 2, 3, 4};

        data.storeVoiceAudio(voiceId, CONVO, pcm);
        ConversationSavedData.VoiceAudioEntry entry = data.getVoiceAudio(voiceId);

        assertNotNull(entry);
        assertEquals(CONVO, entry.conversationId());
        assertArrayEquals(pcm, entry.bytes());
    }

    @Test
    void getVoiceAudio_unknownId_returnsNull() {
        ConversationSavedData data = new ConversationSavedData();
        assertNull(data.getVoiceAudio(UUID.randomUUID()));
    }

    @Test
    void appendMessage_evictingVoiceMessageBeyondVoiceCap_alsoRemovesItsAudioBlob() {
        Config.maxVoiceMessagesStoredPerConversation = 1;
        ConversationSavedData data = new ConversationSavedData();

        UUID oldVoiceId = UUID.randomUUID();
        UUID newVoiceId = UUID.randomUUID();
        data.storeVoiceAudio(oldVoiceId, CONVO, new byte[]{1});
        data.storeVoiceAudio(newVoiceId, CONVO, new byte[]{2});

        data.appendMessage(CONVO, voiceMessage(0, oldVoiceId));
        data.appendMessage(CONVO, voiceMessage(1, newVoiceId));

        // Only the newest voice message's audio should survive - the older one dropped from both the
        // message list AND voiceAudio, or its bytes would leak on disk forever (the exact bug this cap
        // exists to prevent).
        assertNull(data.getVoiceAudio(oldVoiceId), "evicted voice message's audio blob must be removed too");
        assertNotNull(data.getVoiceAudio(newVoiceId));
        assertEquals(1, data.getMessageCount(CONVO));
    }

    @Test
    void appendMessage_evictingViaGeneralCountCap_stillCleansUpVoiceAudio() {
        // Distinct from the voice-specific cap test above: this exercises the FIRST trim loop (general
        // message count), which must also call evictVoiceAudioIfPresent - a voice message evicted purely
        // because the conversation got too long, not because there were too many voice messages, must not
        // leak its audio blob either.
        Config.maxStoredMessagesPerConversation = 1;
        ConversationSavedData data = new ConversationSavedData();

        UUID voiceId = UUID.randomUUID();
        data.storeVoiceAudio(voiceId, CONVO, new byte[]{9});
        data.appendMessage(CONVO, voiceMessage(0, voiceId));
        data.appendMessage(CONVO, textMessage("111", "pushes the voice message out", 1));

        assertEquals(1, data.getMessageCount(CONVO));
        assertNull(data.getVoiceAudio(voiceId), "audio must be cleaned up even when eviction came from the general count cap");
    }

    @Test
    void updateCallMessage_mutatesTheMatchingCallByIdOnly() {
        ConversationSavedData data = new ConversationSavedData();
        UUID targetCallId = UUID.randomUUID();
        UUID otherCallId = UUID.randomUUID();

        data.appendMessage(CONVO, callMessage(otherCallId));
        data.appendMessage(CONVO, callMessage(targetCallId));

        data.updateCallMessage(CONVO, targetCallId, callTag -> callTag.putInt("duration", 12345));

        List<CompoundTag> page = data.getPage(CONVO, 0, 10);
        CompoundTag untouched = page.get(0).getCompound("call");
        CompoundTag mutated = page.get(1).getCompound("call");
        assertEquals(-1, untouched.getInt("duration"), "the OTHER call message must not be touched");
        assertEquals(12345, mutated.getInt("duration"));
    }

    @Test
    void updateCallMessage_unknownCallId_isNoOpNotException() {
        ConversationSavedData data = new ConversationSavedData();
        data.appendMessage(CONVO, callMessage(UUID.randomUUID()));
        assertDoesNotThrow(() -> data.updateCallMessage(CONVO, UUID.randomUUID(), tag -> tag.putInt("duration", 1)));
    }

    @Test
    void updateCallMessage_unknownConversation_isNoOpNotException() {
        ConversationSavedData data = new ConversationSavedData();
        assertDoesNotThrow(() -> data.updateCallMessage("nonexistent", UUID.randomUUID(), tag -> tag.putInt("duration", 1)));
    }

    /**
     * Architectural regression guard: the original crash was caused by a "sync this to every player"
     * method existing on the class holding message history at all - {@code MapVariables#syncData} was
     * called after every message append and blasted the whole conversation blob to everyone. As long as
     * {@link ConversationSavedData} has no such method, that specific bug shape cannot come back, even if
     * someone later adds a new mutation method to this class without reading these comments.
     */
    @Test
    void hasNoMethodThatCouldBroadcastConversationDataToAllPlayers() {
        for (java.lang.reflect.Method method : ConversationSavedData.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            boolean looksLikeBroadcast = name.contains("sync") || name.contains("broadcast") || name.contains("sendtoall");
            assertFalse(looksLikeBroadcast,
                    "ConversationSavedData must never gain a broadcast-style method (found: " + method + ") - " +
                    "conversation data must only ever be sent to specific requesting players via ConversationRequestPacket/ConversationResponsePacket");
        }
    }
}
