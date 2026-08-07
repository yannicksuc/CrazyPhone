package fr.lordfinn.crazyphone.procedures;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IsPhoneInUseProcedure takes a LevelAccessor; passing null resolves to PhoneRegistrySavedData's
 * client-side singleton (since null is never a ServerLevelAccessor), which lets us test the logic
 * without spinning up a real ServerLevel. The singleton is reset after each test so state doesn't
 * leak between tests.
 */
class IsPhoneInUseProcedureTest {

    @AfterEach
    void resetClientSideSingleton() {
        PhoneRegistrySavedData.get(null).phones = new CompoundTag();
    }

    @Test
    void returnsFalseForNullNumber() {
        assertFalse(IsPhoneInUseProcedure.execute(null, null));
    }

    @Test
    void returnsFalseWhenNumberNotRegistered() {
        assertFalse(IsPhoneInUseProcedure.execute(null, "555"));
    }

    @Test
    void returnsTrueWhenNumberIsRegistered() {
        PhoneRegistrySavedData.get(null).phones.put("555", new CompoundTag());
        assertTrue(IsPhoneInUseProcedure.execute(null, "555"));
    }
}
