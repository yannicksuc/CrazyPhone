package fr.lordfinn.crazyphone.client;

import net.minecraft.nbt.CompoundTag;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Decouples the network layer from the conversation screen: the screen registers itself as the listener
 * while open, and the packet handler just calls whoever is currently listening (or does nothing if no
 * conversation screen is open). Avoids the old mod's approach of pushing full conversation state to every
 * client regardless of what they're looking at.
 */
public final class ConversationClientCache {
    private static BiConsumer<String, ConversationPage> listener;

    private ConversationClientCache() {
    }

    public record ConversationPage(List<CompoundTag> messages, boolean hasMore, int skipFromEnd) {
    }

    public static void setListener(BiConsumer<String, ConversationPage> newListener) {
        listener = newListener;
    }

    public static void clearListener(BiConsumer<String, ConversationPage> expected) {
        if (listener == expected)
            listener = null;
    }

    public static void onPageReceived(String conversationId, ConversationPage page) {
        if (listener != null)
            listener.accept(conversationId, page);
    }
}
