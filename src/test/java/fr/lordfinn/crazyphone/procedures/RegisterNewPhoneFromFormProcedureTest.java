package fr.lordfinn.crazyphone.procedures;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** entity is mocked (a plain Entity, not ServerPlayer) - Mockito builds it without ever invoking Entity's
 * real constructor, so no Level is needed. This intentionally exercises the non-ServerPlayer branch (skin
 * lookup skipped) to stay deterministic; the ServerPlayer/skin-fetch branch needs a GameTest instead. */
class RegisterNewPhoneFromFormProcedureTest {

    private final Entity entity = mock(Entity.class);

    RegisterNewPhoneFromFormProcedureTest() {
        when(entity.getStringUUID()).thenReturn("11111111-1111-1111-1111-111111111111");
        // Used by the "name" field's fallback (see missingName_fallsBackToTheEntitysOwnName below) - Mockito
        // returns null for an unstubbed Component-returning method, which would NPE the very first time that
        // fallback is exercised rather than genuinely testing it.
        when(entity.getName()).thenReturn(Component.literal("EntityFallbackName"));
    }

    @AfterEach
    void resetClientSideSingleton() {
        PhoneRegistrySavedData.get(null).phones = new CompoundTag();
    }

    private static HashMap<String, Object> textstate(String number, String name, String password) {
        HashMap<String, Object> state = new HashMap<>();
        state.put("textin:number", number);
        state.put("textin:name", name);
        state.put("textin:password", password);
        return state;
    }

    private static String storedTag(ItemStack stack, String key) {
        return fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(stack), key);
    }

    @Test
    void nullEntity_isNoOp() {
        ItemStack stack = new ItemStack(Items.STICK);
        RegisterNewPhoneFromFormProcedure.execute(null, null, stack, textstate("555", "Alice", "1234"));
        assertTrue(PhoneRegistrySavedData.get(null).phones.isEmpty());
    }

    @Test
    void nullTextstate_isNoOp() {
        ItemStack stack = new ItemStack(Items.STICK);
        RegisterNewPhoneFromFormProcedure.execute(null, entity, stack, null);
        assertTrue(PhoneRegistrySavedData.get(null).phones.isEmpty());
    }

    @Test
    void newNumber_registersPhoneWithFormData() {
        ItemStack stack = new ItemStack(Items.STICK);
        RegisterNewPhoneFromFormProcedure.execute(null, entity, stack, textstate("555", "Alice", "1234"));

        assertTrue(PhoneRegistrySavedData.get(null).phones.contains("555"));
        CompoundTag phone = fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(PhoneRegistrySavedData.get(null).phones, "555");
        assertEquals("Alice", fr.lordfinn.crazyphone.utils.NbtCompat.getString(phone, "name"));
        assertEquals("1234", fr.lordfinn.crazyphone.utils.NbtCompat.getString(phone, "password"));
        assertEquals("11111111-1111-1111-1111-111111111111", fr.lordfinn.crazyphone.utils.NbtCompat.getString(phone, "uuid"));
    }

    @Test
    void newNumber_stampsFinalNameAndNumberOntoTheItemFromTextinKeys() {
        ItemStack stack = new ItemStack(Items.STICK);
        RegisterNewPhoneFromFormProcedure.execute(null, entity, stack, textstate("555", "Alice", "1234"));

        assertEquals("555", storedTag(stack, "number"));
        assertEquals("Alice", storedTag(stack, "name"));
    }

    @Test
    void alreadyRegisteredNumber_refusesToOverwriteExistingRegistration() {
        CompoundTag existing = new CompoundTag();
        existing.putString("name", "Original Owner");
        PhoneRegistrySavedData.get(null).phones.put("555", existing);

        ItemStack stack = new ItemStack(Items.STICK);
        RegisterNewPhoneFromFormProcedure.execute(null, entity, stack, textstate("555", "Attacker", "0000"));

        assertEquals("Original Owner", fr.lordfinn.crazyphone.utils.NbtCompat.getString(fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(PhoneRegistrySavedData.get(null).phones, "555"), "name"),
                "must not let a second registration attempt hijack an already-taken number");
    }

    @Test
    void alreadyRegisteredNumber_stillDoesNotStampTextinFieldsOntoTheItem() {
        // The success-only updateItemStackTag(..., "textin:number"/"textin:name", ...) calls happen AFTER
        // the uniqueness check returns - unlike the earlier "text:name"/"text:number" writes (a separate,
        // unconditional pair this test deliberately leaves out of textstate() to isolate what's actually
        // being verified here), a rejected registration must not leave the textin-derived number/name on
        // the item, since the player never actually registered under it.
        PhoneRegistrySavedData.get(null).phones.put("555", new CompoundTag());

        ItemStack stack = new ItemStack(Items.STICK);
        RegisterNewPhoneFromFormProcedure.execute(null, entity, stack, textstate("555", "Attacker", "0000"));

        assertEquals("", storedTag(stack, "number"), "a rejected registration must not stamp the taken number onto the item");
        assertEquals("", storedTag(stack, "name"));
    }

    @Test
    void missingFormFields_doNotThrow() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertDoesNotThrow(() -> RegisterNewPhoneFromFormProcedure.execute(null, entity, stack, new HashMap<>()));
    }

    /** The name field is the one exception to "missing fields default to empty strings" - a submitted-blank
     * (or never-typed) name falls back to the entity's own Minecraft username instead of being stored blank,
     * matching the client-side EditBox's own ghosted placeholder (see CrazyPhonePasswordScreenScreen). */
    @Test
    void missingName_fallsBackToTheEntitysOwnName() {
        ItemStack stack = new ItemStack(Items.STICK);
        RegisterNewPhoneFromFormProcedure.execute(null, entity, stack, textstate("555", "", "1234"));

        assertEquals("EntityFallbackName", fr.lordfinn.crazyphone.utils.NbtCompat.getString(fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(PhoneRegistrySavedData.get(null).phones, "555"), "name"));
        assertEquals("EntityFallbackName", storedTag(stack, "name"));
    }

    @Test
    void blankName_alsoFallsBackToTheEntitysOwnName() {
        // Not just empty - a name field that's all whitespace (e.g. the player typed then deleted spaces)
        // should be treated the same as never having typed anything, not stored as literal whitespace.
        ItemStack stack = new ItemStack(Items.STICK);
        RegisterNewPhoneFromFormProcedure.execute(null, entity, stack, textstate("555", "   ", "1234"));

        assertEquals("EntityFallbackName", fr.lordfinn.crazyphone.utils.NbtCompat.getString(fr.lordfinn.crazyphone.utils.NbtCompat.getCompound(PhoneRegistrySavedData.get(null).phones, "555"), "name"));
    }
}
