package fr.lordfinn.crazyphone.procedures;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the client-side PhoneRegistrySavedData singleton (world=null), same pattern as
 * CrazyPhoneDeletePhoneByNumberProcedureTest/IsPhoneInUseProcedureTest. */
class ContactProceduresTest {

    @AfterEach
    void resetClientSideSingleton() {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(null);
        registry.phones = new CompoundTag();
        registry.contacts = new CompoundTag();
    }

    private static void registerPhone(String number, String name) {
        CompoundTag phone = new CompoundTag();
        phone.putString("name", name);
        PhoneRegistrySavedData.get(null).phones.put(number, phone);
    }

    private static ListTag contactsOf(String owner) {
        Tag tag = PhoneRegistrySavedData.get(null).contacts.get(owner);
        return tag instanceof ListTag list ? list : new ListTag();
    }

    // --- add ---

    @Test
    void add_nullContactOrOwner_isNoOp() {
        CrazyPhoneAddContactToPhoneProcedure.execute(null, null, "555");
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", null);
        assertTrue(PhoneRegistrySavedData.get(null).contacts.isEmpty());
    }

    @Test
    void add_firstContact_createsTheList() {
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", "555");
        ListTag contacts = contactsOf("555");
        assertEquals(1, contacts.size());
        assertEquals("666", ((StringTag) contacts.get(0)).getAsString());
    }

    @Test
    void add_duplicateContact_isNotAddedTwice() {
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", "555");
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", "555");
        assertEquals(1, contactsOf("555").size(), "adding the same contact twice must not duplicate it");
    }

    @Test
    void add_secondContact_isInsertedAtTheFront() {
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", "555");
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "777", "555");
        ListTag contacts = contactsOf("555");
        assertEquals(2, contacts.size());
        assertEquals("777", ((StringTag) contacts.get(0)).getAsString(), "most recently added contact should be first");
    }

    // --- remove ---

    @Test
    void remove_nullContactOrOwner_isNoOp() {
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", "555");
        CrazyPhoneRemoveContactFromPhoneProcedure.execute(null, null, "555");
        CrazyPhoneRemoveContactFromPhoneProcedure.execute(null, "666", null);
        assertEquals(1, contactsOf("555").size());
    }

    @Test
    void remove_existingContact_removesIt() {
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", "555");
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "777", "555");
        CrazyPhoneRemoveContactFromPhoneProcedure.execute(null, "666", "555");
        ListTag contacts = contactsOf("555");
        assertEquals(1, contacts.size());
        assertEquals("777", ((StringTag) contacts.get(0)).getAsString());
    }

    @Test
    void remove_nonExistentContact_isNoOp() {
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", "555");
        CrazyPhoneRemoveContactFromPhoneProcedure.execute(null, "999", "555");
        assertEquals(1, contactsOf("555").size());
    }

    @Test
    void remove_allDuplicateEntries_removesEveryOne() {
        // The list is normally add-with-dedup, but this guards the removeIf's own doc comment ("removes
        // every matching entry, not just the last one found, in case duplicates ever exist") in case that
        // invariant is ever broken upstream.
        ListTag contacts = new ListTag();
        contacts.add(StringTag.valueOf("666"));
        contacts.add(StringTag.valueOf("666"));
        contacts.add(StringTag.valueOf("777"));
        PhoneRegistrySavedData.get(null).contacts.put("555", contacts);

        CrazyPhoneRemoveContactFromPhoneProcedure.execute(null, "666", "555");

        ListTag remaining = contactsOf("555");
        assertEquals(1, remaining.size());
        assertEquals("777", ((StringTag) remaining.get(0)).getAsString());
    }

    // --- get (resolves numbers into full contact records) ---

    @Test
    void get_nullOwner_returnsEmptyList() {
        assertTrue(CrazyPhoneGetContactsProcedure.execute(null, null).isEmpty());
    }

    @Test
    void get_ownerWithNoContacts_returnsEmptyList() {
        assertTrue(CrazyPhoneGetContactsProcedure.execute(null, "555").isEmpty());
    }

    @Test
    void get_resolvesContactNumbersToFullRecordsIncludingTheNumberItself() {
        registerPhone("666", "Bob");
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", "555");

        ListTag resolved = CrazyPhoneGetContactsProcedure.execute(null, "555");

        assertEquals(1, resolved.size());
        CompoundTag entry = resolved.getCompound(0);
        assertEquals("Bob", entry.getString("name"));
        assertEquals("666", entry.getString("number"), "the raw phone record has no number field of its own - it must be stamped on here");
    }

    @Test
    void get_contactWhosePhoneWasDeleted_isSilentlyOmittedNotErrored() {
        // The contact-number reference outlives the actual phone registration (e.g. the other player
        // deleted their phone) - CrazyPhoneDeletePhoneByNumberProcedure is supposed to clean these up, but
        // this procedure must be defensive on its own too rather than crash on a dangling reference.
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", "555");
        // Deliberately do NOT register 666, simulating a stale/dangling contact reference.
        assertDoesNotThrow(() -> {
            ListTag resolved = CrazyPhoneGetContactsProcedure.execute(null, "555");
            assertTrue(resolved.isEmpty());
        });
    }

    @Test
    void get_multipleContacts_reversesTheStoredOrder() {
        // Characterizes current (possibly surprising) behavior: add() prepends new contacts (newest-first
        // storage order), but get() ALSO prepends each resolved record while iterating the list front-to-
        // back - net effect is the final list comes back OLDEST-added-first, not newest-first as stored.
        registerPhone("666", "Bob");
        registerPhone("777", "Carol");
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "666", "555"); // added first
        CrazyPhoneAddContactToPhoneProcedure.execute(null, "777", "555"); // added second, stored at front

        ListTag stored = contactsOf("555");
        assertEquals("777", ((StringTag) stored.get(0)).getAsString(), "sanity check on storage order");

        ListTag resolved = CrazyPhoneGetContactsProcedure.execute(null, "555");
        assertEquals("666", resolved.getCompound(0).getString("number"),
                "get() reverses storage order back to oldest-added-first - see method comment for why this is worth locking in");
    }
}
