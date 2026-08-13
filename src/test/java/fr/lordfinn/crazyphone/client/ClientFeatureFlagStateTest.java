package fr.lordfinn.crazyphone.client;

import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.network.FeatureFlagSyncPacket;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the client-side mirror fed by {@link FeatureFlagSyncPacket} - the logic behind every "grey
 * out this icon" check (call/mic/image/mayor-vote buttons, camera-insert hint). Since {@link
 * ClientFeatureFlagState}'s backing map is a static, JVM-wide singleton, every test here drives it
 * through a real {@code onPacket} call first rather than asserting on whatever state a previous test
 * left behind - {@code onPacket} always clears before applying, so this keeps tests order-independent.
 */
class ClientFeatureFlagStateTest {

    @Test
    void onPacket_thenIsEnabled_reflectsReceivedStates() {
        ClientFeatureFlagState.onPacket(new FeatureFlagSyncPacket(Map.of(
                FeatureFlag.CALLS.id, false,
                FeatureFlag.VOICE_MESSAGES.id, true
        )));

        assertFalse(ClientFeatureFlagState.isEnabled(FeatureFlag.CALLS));
        assertTrue(ClientFeatureFlagState.isEnabled(FeatureFlag.VOICE_MESSAGES));
    }

    @Test
    void isEnabled_forFlagMissingFromTheLastPacket_defaultsToEnabled() {
        // Only CALLS is present in this packet - a flag never mentioned by the server must never read
        // as disabled just because it's absent, or every icon would flicker greyed-out on a partial sync.
        ClientFeatureFlagState.onPacket(new FeatureFlagSyncPacket(Map.of(FeatureFlag.CALLS.id, false)));

        assertTrue(ClientFeatureFlagState.isEnabled(FeatureFlag.IMAGES));
        assertTrue(ClientFeatureFlagState.isEnabled(FeatureFlag.MAYOR_VOTING));
    }

    @Test
    void onPacket_replacesPreviousStateEntirely_ratherThanMerging() {
        ClientFeatureFlagState.onPacket(new FeatureFlagSyncPacket(Map.of(FeatureFlag.CALLS.id, false)));
        assertFalse(ClientFeatureFlagState.isEnabled(FeatureFlag.CALLS));

        // A second sync that doesn't even mention CALLS must not leave the stale "disabled" entry
        // behind - onPacket clears the whole map before applying the new one.
        ClientFeatureFlagState.onPacket(new FeatureFlagSyncPacket(Map.of(FeatureFlag.VOICE_MESSAGES.id, false)));

        assertTrue(ClientFeatureFlagState.isEnabled(FeatureFlag.CALLS),
                "a stale disabled entry from a previous sync must not survive a newer sync that omits it");
        assertFalse(ClientFeatureFlagState.isEnabled(FeatureFlag.VOICE_MESSAGES));
    }
}
