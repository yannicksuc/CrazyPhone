package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * These procedures only read/write CUSTOM_DATA on whatever ItemStack they're given, so a plain vanilla
 * stack (Items.STICK) is used rather than the mod's own CrazyPhone item - keeps the test independent of
 * mod registration ordering and exercises exactly the logic under test.
 */
class PhoneNumberProceduresTest {

    @Test
    void resetCrazyPhoneNumber_generatesThreeDigitNumberAndStoresIt() {
        ItemStack stack = new ItemStack(Items.STICK);

        String number = ResetCrazyPhoneNumberProcedure.execute(stack);

        assertTrue(number.matches("\\d{3}"), "expected a 3-digit number, got: " + number);
        int value = Integer.parseInt(number);
        assertTrue(value >= 100 && value <= 999);

        String stored = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("number");
        assertEquals(number, stored);
    }

    @Test
    void getCrazyPhoneNumber_returnsStoredNumberIfPresent() {
        ItemStack stack = new ItemStack(Items.STICK);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("number", "123"));

        assertEquals("123", GetCrazyPhoneNumberProcedure.execute(stack));
    }

    @Test
    void getCrazyPhoneNumber_generatesAndPersistsNumberIfMissing() {
        ItemStack stack = new ItemStack(Items.STICK);

        String number = GetCrazyPhoneNumberProcedure.execute(stack);

        assertNotNull(number);
        assertFalse(number.isEmpty());
        // Calling it again must return the SAME number (it was persisted, not regenerated).
        assertEquals(number, GetCrazyPhoneNumberProcedure.execute(stack));
    }

    @Test
    void isPhoneSetup_falseWhenNameNotSet() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertFalse(IsPhoneSetupProcedure.execute(stack));
    }

    @Test
    void isPhoneSetup_trueOnceNameIsSet() {
        ItemStack stack = new ItemStack(Items.STICK);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("name", "Alice"));
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
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean("isOpen", true));
        assertTrue(IsPhoneOpenProcedure.execute(stack));
    }
}
