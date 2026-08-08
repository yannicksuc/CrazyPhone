package fr.lordfinn.crazyphone;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only covers the global-switch half of isEnabledFor (isGloballyEnabled/byId) - the permission-node half
 * needs a real ServerPlayer + PermissionAPI, better suited to a GameTest.
 *
 * setGloballyEnabled() itself isn't exercised end-to-end here: it calls ModConfigSpec.ConfigValue#set(),
 * which only updates Config's live mirror field via the ModConfigEvent reload NeoForge fires when a config
 * registered through the real mod-loading lifecycle changes - that lifecycle isn't present in this bare
 * unitTest sandbox, so .set() here is a silent no-op on the mirror field (confirmed: an earlier version of
 * this test called it and asserted the flag actually flipped, which failed even though the exact same
 * sequence works correctly in game - see Config#onLoad). Writing the mirror fields directly instead tests
 * exactly what's actually this class's own responsibility: that isGloballyEnabled() reads the right one.
 */
class FeatureFlagTest {

    @AfterEach
    void restoreDefaults() {
        Config.callsFeatureEnabled = true;
        Config.voiceMessagesFeatureEnabled = true;
        Config.imagesFeatureEnabled = true;
        Config.cameraFeatureEnabled = true;
        Config.mayorElectionFeatureEnabled = true;
    }

    @Test
    void everyFlag_defaultsToEnabled() {
        for (FeatureFlag flag : FeatureFlag.values())
            assertTrue(flag.isGloballyEnabled(), flag.id + " should default to enabled");
    }

    @Test
    void isGloballyEnabled_readsItsOwnConfigMirrorFieldIndependently() {
        Config.callsFeatureEnabled = false;

        assertFalse(FeatureFlag.CALLS.isGloballyEnabled());
        assertTrue(FeatureFlag.VOICE_MESSAGES.isGloballyEnabled(), "disabling one flag must not affect another");
    }

    @Test
    void byId_findsTheMatchingFlag() {
        assertEquals(FeatureFlag.CALLS, FeatureFlag.byId("calls"));
        assertEquals(FeatureFlag.MAYOR_VOTING, FeatureFlag.byId("mayor_voting"));
    }

    @Test
    void byId_unknownId_returnsNull() {
        assertNull(FeatureFlag.byId("does_not_exist"));
    }

    @Test
    void byId_everyRealFlagIsRoundTrippable() {
        for (FeatureFlag flag : FeatureFlag.values())
            assertEquals(flag, FeatureFlag.byId(flag.id));
    }

    @Test
    void permissionNode_idIncludesTheFlagId() {
        // The permission node id is what a server admin actually types into a permissions plugin -
        // getting this wrong would silently break every server relying on per-permission gating.
        assertTrue(FeatureFlag.CALLS.permission.getNodeName().contains("calls"));
    }
}
