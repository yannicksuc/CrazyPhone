package fr.lordfinn.crazyphone.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import fr.lordfinn.crazyphone.Config;

import java.util.ArrayList;
import java.util.List;

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

    public static ConversationSavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        ConversationSavedData data = new ConversationSavedData();
        data.conversations = tag.get("conversations") instanceof CompoundTag t ? t : new CompoundTag();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookupProvider) {
        nbt.put("conversations", this.conversations);
        return nbt;
    }

    public static ConversationSavedData get(LevelAccessor world) {
        if (world instanceof ServerLevelAccessor serverLevelAcc) {
            return serverLevelAcc.getLevel().getServer().overworld().getDataStorage()
                    .computeIfAbsent(new SavedData.Factory<>(ConversationSavedData::new, ConversationSavedData::load), DATA_NAME);
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
