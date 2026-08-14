package fr.lordfinn.crazyphone.procedures;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These procedures only read/write CUSTOM_DATA on whatever ItemStack they're given, so a plain vanilla
 * stack (Items.STICK) is used rather than the mod's own CrazyPhone item - keeps the test independent of
 * mod registration ordering and exercises exactly the logic under test.
 *
 * Number generation also checks the registry for collisions (see IsPhoneInUseProcedure), so these pass
 * {@code null} as the world - it resolves to PhoneRegistrySavedData's client-side singleton (see
 * IsPhoneInUseProcedureTest), letting the uniqueness check run without a real ServerLevel. The singleton
 * is reset after each test so state doesn't leak between tests.
 */
class PhoneNumberProceduresTest {

    @AfterEach
    void resetClientSideSingleton() {
        PhoneRegistrySavedData.get(null).phones = new CompoundTag();
    }

    @Test
    void resetCrazyPhoneNumber_generatesThreeDigitNumberAndStoresIt() {
        ItemStack stack = new ItemStack(Items.STICK);

        String number = ResetCrazyPhoneNumberProcedure.execute(stack, null);

        assertTrue(number.matches("\\d{3}"), "expected a 3-digit number, got: " + number);
        int value = Integer.parseInt(number);
        assertTrue(value >= 100 && value <= 999);

        String stored = fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(stack), "number");
        assertEquals(number, stored);
    }

    @Test
    void getCrazyPhoneNumber_returnsStoredNumberIfPresent() {
        ItemStack stack = new ItemStack(Items.STICK);
        PhoneTagAccess.updateTag(stack, tag -> tag.putString("number", "123"));

        assertEquals("123", GetCrazyPhoneNumberProcedure.execute(stack, null));
    }

    @Test
    void getCrazyPhoneNumber_generatesAndPersistsNumberIfMissing() {
        ItemStack stack = new ItemStack(Items.STICK);

        String number = GetCrazyPhoneNumberProcedure.execute(stack, null);

        assertNotNull(number);
        assertFalse(number.isEmpty());
        // Calling it again must return the SAME number (it was persisted, not regenerated).
        assertEquals(number, GetCrazyPhoneNumberProcedure.execute(stack, null));
    }

    @Test
    void isPhoneSetup_falseWhenNameNotSet() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertFalse(IsPhoneSetupProcedure.execute(stack));
    }

    @Test
    void isPhoneSetup_trueOnceNameIsSet() {
        ItemStack stack = new ItemStack(Items.STICK);
        PhoneTagAccess.updateTag(stack, tag -> tag.putString("name", "Alice"));
        assertTrue(IsPhoneSetupProcedure.execute(stack));
    }

    @Test
    void isPhoneOpen_defaultsToFalse() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertFalse(IsPhoneOpenProcedure.execute(stack));
    }

    @Test
    void isPhoneOpen_trueOnceFlagIsSet() {
        ItemStack stack = new ItemStack(Items.STICK);
        PhoneTagAccess.updateTag(stack, tag -> tag.putBoolean("isOpen", true));
        assertTrue(IsPhoneOpenProcedure.execute(stack));
    }
}
