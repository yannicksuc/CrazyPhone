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
//? if >=1.21.10 {
/*import com.mojang.serialization.Codec;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
*///?}
//? if >=26 {
/*import fr.lordfinn.crazyphone.Crazyphone;
*///?}
// Real vanilla SavedData.Factory (unlike NeoForge's own 2-arg convenience overload of the same class -
// see the get() method below) always requires a DataFixTypes, at every version Factory exists in at all -
// confirmed via javap on the Loom-remapped vanilla jar, not assumed from the NeoForge-side code above.
//? if fabric && >=1.20.5 <1.21.10 {
/*import net.minecraft.util.datafix.DataFixTypes;
*///?}

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
    /** imageId (string) -> {bytes: PNG, conversationId} - the Fabric-native picture pipeline's own payload
     * store (see fr.lordfinn.crazyphone.picture package), same shape as voiceAudio above. On NeoForge,
     * image bytes still live in Camera mod's own disk cache (see CrazyPhoneHelper#imageDataToCompoundTag) -
     * this map is simply never written to there, kept unconditional only because ConversationSavedData
     * itself is shared, loader-agnostic code. */
    public CompoundTag imageBytes = new CompoundTag();

    //? if >=1.20.5 <1.21.10 {
    /*public static ConversationSavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
    *///? } else {
    public static ConversationSavedData load(CompoundTag tag) {
    //?}
        ConversationSavedData data = new ConversationSavedData();
        data.conversations = tag.get("conversations") instanceof CompoundTag t ? t : new CompoundTag();
        data.voiceAudio = tag.get("voiceAudio") instanceof CompoundTag t ? t : new CompoundTag();
        data.imageBytes = tag.get("imageBytes") instanceof CompoundTag t ? t : new CompoundTag();
        return data;
    }

    //? if <1.20.5 {
    @Override
    public CompoundTag save(CompoundTag nbt) {
        return writeNbt(nbt);
    }
    //?}
    //? if >=1.20.5 <1.21.10 {
    /*@Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookupProvider) {
        return writeNbt(nbt);
    }
    *///?}
    //? if >=1.21.10 {
    /*// No longer a SavedData override point - see #CODEC below. Kept as this class's own hand-rolled
    // NBT shape, reused by the Codec's encode side, same rationale as PhoneRegistrySavedData.
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookupProvider) {
        return writeNbt(nbt);
    }
    *///?}

    private CompoundTag writeNbt(CompoundTag nbt) {
        nbt.put("conversations", this.conversations);
        nbt.put("voiceAudio", this.voiceAudio);
        nbt.put("imageBytes", this.imageBytes);
        return nbt;
    }

    //? if >=1.21.10 {
    /*public static final Codec<ConversationSavedData> CODEC = CompoundTag.CODEC.xmap(ConversationSavedData::load,
            data -> data.writeNbt(new CompoundTag()));

    public static final SavedDataType<ConversationSavedData> TYPE =
            new SavedDataType<>(/^$ saved_data_id {^/DATA_NAME/^$}^/, ConversationSavedData::new, CODEC, DataFixTypes.LEVEL);
    *///?}

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
        return new VoiceAudioEntry(fr.lordfinn.crazyphone.utils.NbtCompat.getString(entry, "conversationId"), bytes);
    }

    public record VoiceAudioEntry(String conversationId, byte[] bytes) {
    }

    public void storeImageBytes(UUID imageId, String conversationId, byte[] png) {
        CompoundTag entry = new CompoundTag();
        entry.putString("conversationId", conversationId);
        entry.put("bytes", new ByteArrayTag(png));
        imageBytes.put(imageId.toString(), entry);
        setDirty();
    }

    /** Null if the id doesn't exist (evicted, or never existed) - same not-client-trusted-conversationId
     * caveat as {@link #getVoiceAudio}. */
    public @Nullable ImageBytesEntry getImageBytes(UUID imageId) {
        if (!(imageBytes.get(imageId.toString()) instanceof CompoundTag entry))
            return null;
        byte[] bytes = entry.get("bytes") instanceof ByteArrayTag tag ? tag.getAsByteArray() : new byte[0];
        return new ImageBytesEntry(fr.lordfinn.crazyphone.utils.NbtCompat.getString(entry, "conversationId"), bytes);
    }

    public record ImageBytesEntry(String conversationId, byte[] bytes) {
    }

    public static ConversationSavedData get(LevelAccessor world) {
        if (world instanceof ServerLevelAccessor serverLevelAcc) {
            return serverLevelAcc.getLevel().getServer().overworld().getDataStorage()
                    //? if neoforge && <1.20.5 {
                    .computeIfAbsent(new SavedData.Factory<>(ConversationSavedData::new, ConversationSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
                    //?}
                    //? if neoforge && >=1.20.5 <1.21.10 {
                    /*.computeIfAbsent(new SavedData.Factory<>(ConversationSavedData::new, ConversationSavedData::load), DATA_NAME);
                    *///?}
                    // >=1.21.10's computeIfAbsent(SavedDataType) overload and TYPE itself are both plain
                    // vanilla (no loader-specific types involved) - shared across both loaders instead of
                    // duplicating a fabric-only copy of the same call.
                    //? if >=1.21.10 {
                    /*.computeIfAbsent(TYPE);
                    *///?}
                    // Fabric branches use real vanilla SavedData/DimensionDataStorage signatures (confirmed via
                    // javap on the Loom-remapped vanilla jar) rather than the NeoForge-only convenience
                    // overloads the two branches above rely on - see the import block's comment above.
                    //? if fabric && <1.20.5 {
                    /*.computeIfAbsent(ConversationSavedData::load, ConversationSavedData::new, DATA_NAME);
                    *///?}
                    //? if fabric && >=1.20.5 <1.21.10 {
                    /*.computeIfAbsent(new SavedData.Factory<>(ConversationSavedData::new, ConversationSavedData::load, DataFixTypes.LEVEL), DATA_NAME);
                    *///?}
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
            evictVoiceAudioIfPresent(fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(messages, 0));
            evictImageBytesIfPresent(fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(messages, 0));
            messages.remove(0);
        }
        int imageCount = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            CompoundTag message = fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(messages, i);
            if (!message.contains("image"))
                continue;
            imageCount++;
            if (imageCount > Config.maxImagesStoredPerConversation) {
                evictImageBytesIfPresent(message);
                messages.remove(i);
            }
        }
        // Voice audio is the heaviest payload this history stores - capped separately from the general
        // message count, same shape as the image cap above. An evicted message's audio blob is removed in
        // lockstep here or it leaks on disk forever, defeating the entire point of capping this history.
        int voiceCount = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            CompoundTag message = fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(messages, i);
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
        UUID voiceId = new UUID(fr.lordfinn.crazyphone.utils.NbtCompat.getLong(voiceTag, "voice_id_most"), fr.lordfinn.crazyphone.utils.NbtCompat.getLong(voiceTag, "voice_id_least"));
        voiceAudio.remove(voiceId.toString());
    }

    /** No-op for a NeoForge/Camera-mod image tag (its bytes were never in this map to begin with - see
     * imageBytes' own doc comment), removes the corresponding entry for a Fabric-native one. Both loaders'
     * image tags share the same image_id_most/image_id_least fields, so this is safe to call unconditionally. */
    private void evictImageBytesIfPresent(CompoundTag message) {
        if (!(message.get("image") instanceof CompoundTag imageTag))
            return;
        UUID imageId = new UUID(fr.lordfinn.crazyphone.utils.NbtCompat.getLong(imageTag, "image_id_most"), fr.lordfinn.crazyphone.utils.NbtCompat.getLong(imageTag, "image_id_least"));
        imageBytes.remove(imageId.toString());
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
                CompoundTag message = fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(messages, i);
                if (message.get("call") instanceof CompoundTag callTag && fr.lordfinn.crazyphone.utils.NbtCompat.getLong(callTag, "call_duration_millis") == -1) {
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
            CompoundTag message = fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(messages, i);
            if (!(message.get("call") instanceof CompoundTag callTag))
                continue;
            UUID id = new UUID(fr.lordfinn.crazyphone.utils.NbtCompat.getLong(callTag, "call_id_most"), fr.lordfinn.crazyphone.utils.NbtCompat.getLong(callTag, "call_id_least"));
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
            page.add(fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(messages, i).copy());
        }
        return page;
    }

    public int getMessageCount(String conversationId) {
        Tag existing = conversations.get(conversationId);
        return existing instanceof ListTag messages ? messages.size() : 0;
    }
}
