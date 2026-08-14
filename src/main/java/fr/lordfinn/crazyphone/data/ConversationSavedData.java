package fr.lordfinn.crazyphone.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
//? if <1.20.5 {
import net.minecraft.util.datafix.DataFixTypes;
//?}

import fr.lordfinn.crazyphone.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * Per-conversation message history. Unlike {@link PhoneRegistrySavedData}, this is NEVER broadcast
 * wholesale to every player or on login: a conversation is only sent to the two participants, and only
 * when they actually open it (see network.ConversationRequestPacket / ConversationResponsePacket).
 *
 * Storage itself is also capped (not just what's sent over the network) so a server's disk/memory
 * footprint for a conversation cannot grow forever either - this was the root cause of the old mod's
 * "gets heavier every load, eventually crashes on player connect" bug: the equivalent old data
 * (CrazythingsModVariables.MapVariables#crazyPhoneMessages) was unbounded and broadcast in full to
 * everyone on every login and after every single message sent.
 */
public class ConversationSavedData extends SavedData {
    public static final String DATA_NAME = "crazyphone_conversations";

    public CompoundTag conversations = new CompoundTag();
    /** voiceMessageId (string) -> {bytes: raw 16-bit PCM, conversationId} - kept separate from the message
     * tag itself, same "lightweight metadata in the message, heavy payload fetched on demand" shape as
     * image messages. Never sent wholesale; only ever read one entry at a time, on an explicit play click. */
    public CompoundTag voiceAudio = new CompoundTag();

    //? if >=1.20.5 {
    /*public static ConversationSavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
    *///? } else {
    public static ConversationSavedData load(CompoundTag tag) {
    //?}
        ConversationSavedData data = new ConversationSavedData();
        data.conversations = tag.get("conversations") instanceof CompoundTag t ? t : new CompoundTag();
        data.voiceAudio = tag.get("voiceAudio") instanceof CompoundTag t ? t : new CompoundTag();
        return data;
    }

    //? if >=1.20.5 {
    /*@Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookupProvider) {
    *///? } else {
    @Override
    public CompoundTag save(CompoundTag nbt) {
    //?}
        nbt.put("conversations", this.conversations);
        nbt.put("voiceAudio", this.voiceAudio);
        return nbt;
    }

    public void storeVoiceAudio(UUID voiceId, String conversationId, byte[] pcm) {
        CompoundTag entry = new CompoundTag();
        entry.putString("conversationId", conversationId);
        entry.put("bytes", new ByteArrayTag(pcm));
        voiceAudio.put(voiceId.toString(), entry);
        setDirty();
    }

    /** Null if the id doesn't exist (evicted, or never existed) - the caller must not trust a
     * client-supplied conversationId, only the one stored here at upload time. */
    public @Nullable VoiceAudioEntry getVoiceAudio(UUID voiceId) {
        if (!(voiceAudio.get(voiceId.toString()) instanceof CompoundTag entry))
            return null;
        byte[] bytes = entry.get("bytes") instanceof ByteArrayTag tag ? tag.getAsByteArray() : new byte[0];
        return new VoiceAudioEntry(entry.getString("conversationId"), bytes);
    }

    public record VoiceAudioEntry(String conversationId, byte[] bytes) {
    }

    public static ConversationSavedData get(LevelAccessor world) {
        if (world instanceof ServerLevelAccessor serverLevelAcc) {
            return serverLevelAcc.getLevel().getServer().overworld().getDataStorage()
                    //? if >=1.20.5 {
                    /*.computeIfAbsent(new SavedData.Factory<>(ConversationSavedData::new, ConversationSavedData::load), DATA_NAME);
                    *///? } else {
                    .computeIfAbsent(new SavedData.Factory<>(ConversationSavedData::new, ConversationSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
                    //?}
        }
        throw new IllegalStateException("ConversationSavedData is server-only; conversations are fetched on demand via network packets, never held client-side in full");
    }

    private ListTag getOrCreate(String conversationId) {
        Tag existing = conversations.get(conversationId);
        if (existing instanceof ListTag list)
            return list;
        ListTag created = new ListTag();
        conversations.put(conversationId, created);
        return created;
    }

    /** Appends a message and trims stored history down to the configured caps. Marks dirty for disk persistence. */
    public CompoundTag appendMessage(String conversationId, CompoundTag messageTag) {
        ListTag messages = getOrCreate(conversationId);
        messages.add(messageTag);
        trim(messages);
        setDirty();
        return messageTag;
    }

    private void trim(ListTag messages) {
        while (messages.size() > Config.maxStoredMessagesPerConversation) {
            evictVoiceAudioIfPresent(messages.getCompound(0));
            messages.remove(0);
        }
        int imageCount = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            CompoundTag message = messages.getCompound(i);
            if (!message.contains("image"))
                continue;
            imageCount++;
            if (imageCount > Config.maxImagesStoredPerConversation) {
                messages.remove(i);
            }
        }
        // Voice audio is the heaviest payload this history stores - capped separately from the general
        // message count, same shape as the image cap above. An evicted message's audio blob is removed in
        // lockstep here or it leaks on disk forever, defeating the entire point of capping this history.
        int voiceCount = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            CompoundTag message = messages.getCompound(i);
            if (!message.contains("voice"))
                continue;
            voiceCount++;
            if (voiceCount > Config.maxVoiceMessagesStoredPerConversation) {
                evictVoiceAudioIfPresent(message);
                messages.remove(i);
            }
        }
    }

    private void evictVoiceAudioIfPresent(CompoundTag message) {
        if (!(message.get("voice") instanceof CompoundTag voiceTag))
            return;
        UUID voiceId = new UUID(voiceTag.getLong("voice_id_most"), voiceTag.getLong("voice_id_least"));
        voiceAudio.remove(voiceId.toString());
    }

    /** Sentinel {@code call_duration_millis} value meaning "connected, then the server went away before it
     * could be gracefully ended (crash, forced kill, normal shutdown) - the real duration is unknown and
     * unrecoverable", distinct from -1 ("still genuinely ongoing"). See {@link #finalizeOrphanedCalls()}. */
    public static final long ORPHANED_CALL_DURATION_MILLIS = -2;

    /** Called once when the server finishes starting (see fr.lordfinn.crazyphone.data.OrphanedCallCleanup):
     * CallRegistry is in-memory only and always starts empty on a fresh boot, so ANY call message still
     * showing call_duration_millis == -1 at this point can only mean the previous server process ended while
     * that call was still active, and nothing will ever finalize it through the normal hangup/leave path.
     * Without this, that entry would tick an ever-growing fake "in progress" duration forever, surviving
     * every future relaunch since it's on disk. */
    public void finalizeOrphanedCalls() {
        boolean changed = false;
        for (String conversationId : fr.lordfinn.crazyphone.utils.NbtCompat.keySet(conversations)) {
            if (!(conversations.get(conversationId) instanceof ListTag messages))
                continue;
            for (int i = 0; i < messages.size(); i++) {
                CompoundTag message = messages.getCompound(i);
                if (message.get("call") instanceof CompoundTag callTag && callTag.getLong("call_duration_millis") == -1) {
                    callTag.putLong("call_duration_millis", ORPHANED_CALL_DURATION_MILLIS);
                    changed = true;
                }
            }
        }
        if (changed)
            setDirty();
    }

    /** Finds the most recent message in this conversation whose "call" sub-tag has the given call id and
     * lets the caller mutate it in place - used to fill in a call's final duration once it ends, without
     * appending a second "call ended" message. No-op if not found (e.g. already evicted by the history cap). */
    public void updateCallMessage(String conversationId, UUID callId, java.util.function.Consumer<CompoundTag> mutator) {
        Tag existing = conversations.get(conversationId);
        if (!(existing instanceof ListTag messages))
            return;
        for (int i = messages.size() - 1; i >= 0; i--) {
            CompoundTag message = messages.getCompound(i);
            if (!(message.get("call") instanceof CompoundTag callTag))
                continue;
            UUID id = new UUID(callTag.getLong("call_id_most"), callTag.getLong("call_id_least"));
            if (id.equals(callId)) {
                mutator.accept(callTag);
                setDirty();
                return;
            }
        }
    }

    /** Returns up to {@code limit} of the most recent messages, oldest-first, starting {@code skipFromEnd} messages back from the newest (for "load more" pagination). */
    public List<CompoundTag> getPage(String conversationId, int skipFromEnd, int limit) {
        Tag existing = conversations.get(conversationId);
        List<CompoundTag> page = new ArrayList<>();
        if (!(existing instanceof ListTag messages) || messages.isEmpty())
            return page;

        int endExclusive = Math.max(0, messages.size() - skipFromEnd);
        int startInclusive = Math.max(0, endExclusive - limit);
        for (int i = startInclusive; i < endExclusive; i++) {
            page.add(messages.getCompound(i).copy());
        }
        return page;
    }

    public int getMessageCount(String conversationId) {
        Tag existing = conversations.get(conversationId);
        return existing instanceof ListTag messages ? messages.size() : 0;
    }
}
