package fr.lordfinn.crazyphone.data;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This registry is the piece that's STILL fully synced to every player - it must stay small
 * (bounded by phone/contact/candidate count, never by message volume) which these tests don't
 * directly measure, but they do lock in that it round-trips correctly and independently of
 * ConversationSavedData (which holds the actual unbounded-risk data).
 */
class PhoneRegistrySavedDataTest {

    @Test
    void newInstance_hasEmptyDefaults() {
        PhoneRegistrySavedData data = new PhoneRegistrySavedData();
        assertTrue(data.phones.isEmpty());
        assertTrue(data.contacts.isEmpty());
        assertTrue(data.mayorVotes.isEmpty());
        assertTrue(data.mayorsCandidates.isEmpty());
        assertTrue(data.lastMayorVoteTimestamps.isEmpty());
        assertFalse(data.isMayorVotingOn);
        assertFalse(data.isMayorElectionOn);
    }

    @Test
    void save_and_load_roundTripsPhonesAndContacts() {
        PhoneRegistrySavedData data = new PhoneRegistrySavedData();

        CompoundTag phone = new CompoundTag();
        phone.putString("uuid", "11111111-1111-1111-1111-111111111111");
        phone.putString("name", "Alice");
        phone.putString("password", "1234");
        data.phones.put("555", phone);

        ListTag aliceContacts = new ListTag();
        aliceContacts.add(StringTag.valueOf("666"));
        data.contacts.put("555", aliceContacts);

        data.isMayorElectionOn = true;
        data.isMayorVotingOn = true;

        CompoundTag saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        PhoneRegistrySavedData loaded = PhoneRegistrySavedData.load(saved, RegistryAccess.EMPTY);

        assertTrue(loaded.phones.get("555") instanceof CompoundTag);
        assertEquals("Alice", ((CompoundTag) loaded.phones.get("555")).getString("name"));
        assertTrue(loaded.contacts.get("555") instanceof ListTag);
        assertEquals(1, ((ListTag) loaded.contacts.get("555")).size());
        assertTrue(loaded.isMayorElectionOn);
        assertTrue(loaded.isMayorVotingOn);
    }

    @Test
    void readFrom_replacesExistingState() {
        PhoneRegistrySavedData data = new PhoneRegistrySavedData();
        data.phones.put("555", new CompoundTag());
        data.isMayorElectionOn = true;

        CompoundTag freshState = new CompoundTag();
        freshState.put("phones", new CompoundTag());
        freshState.put("contacts", new CompoundTag());
        freshState.put("mayorVotes", new CompoundTag());
        freshState.put("mayorsCandidates", new CompoundTag());
        freshState.put("lastMayorVoteTimestamps", new CompoundTag());
        freshState.putBoolean("isMayorVotingOn", false);
        freshState.putBoolean("isMayorElectionOn", false);

        data.readFrom(freshState);

        assertTrue(data.phones.isEmpty(), "readFrom should replace, not merge, state (used by client-side packet sync)");
        assertFalse(data.isMayorElectionOn);
    }

    @Test
    void load_withMissingFields_fallsBackToEmptyRatherThanThrowing() {
        PhoneRegistrySavedData data = PhoneRegistrySavedData.load(new CompoundTag(), RegistryAccess.EMPTY);
        assertTrue(data.phones.isEmpty());
        assertTrue(data.contacts.isEmpty());
        assertFalse(data.isMayorVotingOn);
    }
}
