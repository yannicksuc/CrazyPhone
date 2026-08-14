package fr.lordfinn.crazyphone.procedures;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import fr.lordfinn.crazyphone.utils.NbtCompat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the client-side PhoneRegistrySavedData singleton (world=null) the same way IsPhoneInUseProcedureTest does, to avoid needing a real ServerLevel. */
class CrazyPhoneDeletePhoneByNumberProcedureTest {

    @AfterEach
    void resetClientSideSingleton() {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(null);
        registry.phones = new CompoundTag();
        registry.contacts = new CompoundTag();
    }

    private static void registerPhone(String number) {
        PhoneRegistrySavedData.get(null).phones.put(number, new CompoundTag());
    }

    private static void addContact(String owner, String contactNumber) {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(null);
        ListTag numbers = registry.contacts.get(owner) instanceof ListTag existing ? existing : new ListTag();
        numbers.add(StringTag.valueOf(contactNumber));
        registry.contacts.put(owner, numbers);
    }

    @Test
    void returnsFalseForNullOrEmptyNumber() {
        assertFalse(CrazyPhoneDeletePhoneByNumberProcedure.execute(null, null));
        assertFalse(CrazyPhoneDeletePhoneByNumberProcedure.execute(null, ""));
    }

    @Test
    void returnsFalseWhenNumberNotRegistered() {
        assertFalse(CrazyPhoneDeletePhoneByNumberProcedure.execute(null, "555"));
    }

    @Test
    void removesThePhoneFromTheRegistry() {
        registerPhone("555");
        assertTrue(CrazyPhoneDeletePhoneByNumberProcedure.execute(null, "555"));
        assertFalse(PhoneRegistrySavedData.get(null).phones.contains("555"));
    }

    @Test
    void removesTheDeletedPhonesOwnContactList() {
        registerPhone("555");
        addContact("555", "666");
        CrazyPhoneDeletePhoneByNumberProcedure.execute(null, "555");
        assertFalse(PhoneRegistrySavedData.get(null).contacts.contains("555"));
    }

    @Test
    void removesTheDeletedNumberFromOtherPeoplesContactLists() {
        registerPhone("555");
        registerPhone("666");
        registerPhone("777");
        addContact("666", "555");
        addContact("666", "777");
        addContact("777", "555");

        CrazyPhoneDeletePhoneByNumberProcedure.execute(null, "555");

        ListTag contactsOf666 = (ListTag) PhoneRegistrySavedData.get(null).contacts.get("666");
        assertEquals(1, contactsOf666.size());
        assertEquals("777", NbtCompat.asString(contactsOf666.get(0)));

        Tag contactsOf777 = PhoneRegistrySavedData.get(null).contacts.get("777");
        assertTrue(contactsOf777 == null || ((ListTag) contactsOf777).isEmpty());
    }

    @Test
    void doesNotTouchUnrelatedPhonesOrContacts() {
        registerPhone("555");
        registerPhone("999");
        addContact("999", "111");

        CrazyPhoneDeletePhoneByNumberProcedure.execute(null, "555");

        assertTrue(PhoneRegistrySavedData.get(null).phones.contains("999"));
        ListTag contactsOf999 = (ListTag) PhoneRegistrySavedData.get(null).contacts.get("999");
        assertEquals(1, contactsOf999.size());
        assertEquals("111", NbtCompat.asString(contactsOf999.get(0)));
    }
}
