package fr.lordfinn.crazyphone.client;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-only, in-progress-message state that survives leaving the conversation screen: keyed by
 * conversationId, saved every time ANY conversation screen closes (see
 * CrazyPhoneConversationScreen#onClose) regardless of why (back button, contacts list, closing the
 * phone entirely) and restored the next time that same conversation reopens - the general "don't lose
 * what I was typing" behavior.
 */
public final class ClientMessageDraft {
    private static final Map<String, String> perConversation = new HashMap<>();

    private ClientMessageDraft() {
    }

    public static void saveOnClose(String conversationId, String text) {
        if (conversationId == null || conversationId.isEmpty())
            return;
        if (text == null || text.isEmpty())
            perConversation.remove(conversationId);
        else
            perConversation.put(conversationId, text);
    }

    public static String restore(String conversationId) {
        return conversationId == null ? "" : perConversation.getOrDefault(conversationId, "");
    }
}
