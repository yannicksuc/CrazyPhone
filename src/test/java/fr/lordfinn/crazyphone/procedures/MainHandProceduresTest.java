package fr.lordfinn.crazyphone.procedures;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** LivingEntity is mocked purely to stub getMainHandItem() - Mockito bypasses the real constructor (which
 * needs a Level), so these run as plain, deterministic unit tests. */
class MainHandProceduresTest {

    private final LivingEntity entity = mock(LivingEntity.class);

    @AfterEach
    void resetClientSideSingleton() {
        PhoneRegistrySavedData.get(null).phones = new CompoundTag();
    }

    private static String storedNumber(ItemStack stack) {
        return fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(stack), "number");
    }

    // --- ResetCrazyPhoneNumberFromMainHandProcedure ---

    @Test
    void resetFromMainHand_nullEntity_returnsEmptyString() {
        assertEquals("", ResetCrazyPhoneNumberFromMainHandProcedure.execute(null));
    }

    @Test
    void resetFromMainHand_unsetPhone_generatesAndStoresNewNumber() {
        ItemStack stack = new ItemStack(Items.STICK);
        when(entity.getMainHandItem()).thenReturn(stack);

        String number = ResetCrazyPhoneNumberFromMainHandProcedure.execute(entity);

        assertTrue(number.matches("\\d{3}"));
        assertEquals(number, storedNumber(stack));
    }

    @Test
    void resetFromMainHand_alreadySetUpPhone_returnsExistingNumberWithoutRegenerating() {
        ItemStack stack = new ItemStack(Items.STICK);
        PhoneTagAccess.updateTag(stack, tag -> {
            tag.putString("name", "Alice"); // IsPhoneSetupProcedure checks for "name"
            tag.putString("number", "555");
        });
        when(entity.getMainHandItem()).thenReturn(stack);

        assertEquals("555", ResetCrazyPhoneNumberFromMainHandProcedure.execute(entity));
        assertEquals("555", storedNumber(stack), "must NOT regenerate a number for an already-set-up phone");
    }

    // --- IsPhoneItemStackInUseProcedure ---

    @Test
    void isPhoneItemStackInUse_unregisteredNumber_returnsFalse() {
        ItemStack stack = new ItemStack(Items.STICK);
        PhoneTagAccess.updateTag(stack, tag -> tag.putString("number", "555"));
        assertFalse(IsPhoneItemStackInUseProcedure.execute(null, stack));
    }

    @Test
    void isPhoneItemStackInUse_registeredNumber_returnsTrue() {
        PhoneRegistrySavedData.get(null).phones.put("555", new CompoundTag());
        ItemStack stack = new ItemStack(Items.STICK);
        PhoneTagAccess.updateTag(stack, tag -> tag.putString("number", "555"));
        assertTrue(IsPhoneItemStackInUseProcedure.execute(null, stack));
    }

    @Test
    void isPhoneItemStackInUse_stackWithNoNumberTag_returnsFalse() {
        assertFalse(IsPhoneItemStackInUseProcedure.execute(null, new ItemStack(Items.STICK)));
    }

    // --- GetCrazyPhoneNumberFromMainHandProcedure ---

    @Test
    void getFromMainHand_nullEntity_returnsEmptyString() {
        assertEquals("", GetCrazyPhoneNumberFromMainHandProcedure.execute(null, null));
    }

    @Test
    void getFromMainHand_nonCrazyPhoneItemHeld_returnsEmptyString() {
        when(entity.getMainHandItem()).thenReturn(new ItemStack(Items.STICK));
        assertEquals("", GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null));
    }

    @Test
    void getFromMainHand_crazyPhoneHeld_returnsItsStoredNumber() {
        ItemStack phone = new ItemStack(ModItems.CRAZY_PHONE.get());
        PhoneTagAccess.updateTag(phone, tag -> tag.putString("number", "555"));
        when(entity.getMainHandItem()).thenReturn(phone);

        assertEquals("555", GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null));
    }
}
