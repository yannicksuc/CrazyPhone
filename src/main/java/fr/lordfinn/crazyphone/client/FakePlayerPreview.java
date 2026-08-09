package fr.lordfinn.crazyphone.client;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.player.Player;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * A synthetic Player built purely for a 3D preview (never actually simulated by a server - see
 * CrazyPhoneContactInfoScreenScreen and CrazyPhoneInCallScreenScreen) never gets its
 * DATA_PLAYER_MODE_CUSTOMISATION entity data set. Player itself defines that field to default to
 * {@code (byte) 0}, meaning every optional skin layer (jacket, sleeves, pants legs, hat, cape) starts OFF -
 * for a REAL player this comes from their own client options synced on join, but a fake preview entity never
 * goes through that. The visible symptom is a skin that renders as if every layer checkbox in Options > Skin
 * Customization were unticked: base layer only, no jacket/pants/sleeve overlay, reading as "naked" even for
 * a skin whose outer layer is what actually draws its clothes. DATA_PLAYER_MODE_CUSTOMISATION is protected in
 * vanilla's Player class, so this reaches it via reflection rather than needing a mixin just to widen one
 * field's visibility.
 */
public final class FakePlayerPreview {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    private static Field modeCustomisationField;
    private static boolean lookupFailed;

    private FakePlayerPreview() {
    }

    @SuppressWarnings("unchecked")
    public static void showAllSkinLayers(Player player) {
        try {
            if (modeCustomisationField == null && !lookupFailed) {
                modeCustomisationField = Player.class.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");
                modeCustomisationField.setAccessible(true);
            }
            if (modeCustomisationField == null)
                return;
            EntityDataAccessor<Byte> accessor = (EntityDataAccessor<Byte>) modeCustomisationField.get(null);
            player.getEntityData().set(accessor, (byte) 0x7F);
        } catch (ReflectiveOperationException e) {
            lookupFailed = true;
            LOGGER.error("Failed to enable all skin layers on a fake preview player - it will render with default layers hidden", e);
        }
    }
}
