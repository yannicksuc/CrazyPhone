package fr.lordfinn.crazyphone.client;

import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.network.FeatureFlagSyncPacket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of this player's own {@link FeatureFlag} states, fed by {@link FeatureFlagSyncPacket}.
 * Read by the conversation screen (call/mic/image icons), the mayor candidate screen (vote button) and the
 * camera-insert interaction to grey themselves out / show a disabled hint instead of sending a packet the
 * server would just silently reject. Defaults every flag to enabled so nothing flickers disabled during the
 * brief window before the first sync arrives right after login.
 */
public final class ClientFeatureFlagState {
    private static final Map<String, Boolean> enabledStates = new ConcurrentHashMap<>();

    private ClientFeatureFlagState() {
    }

    public static void onPacket(FeatureFlagSyncPacket packet) {
        enabledStates.clear();
        enabledStates.putAll(packet.enabledStates());
    }

    public static boolean isEnabled(FeatureFlag flag) {
        return enabledStates.getOrDefault(flag.id, true);
    }
}
