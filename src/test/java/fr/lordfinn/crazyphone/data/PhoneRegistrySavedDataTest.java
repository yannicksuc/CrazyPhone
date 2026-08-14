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
        assertTrue(data.groupMeta.isEmpty());
        assertTrue(data.favorites.isEmpty());
    }

    @Test
    void save_and_load_roundTripsGroupMetaAndFavorites() {
        PhoneRegistrySavedData data = new PhoneRegistrySavedData();

        CompoundTag group = new CompoundTag();
        group.putString("name", "Squad");
        group.putString("admin", "555");
        ListTag members = new ListTag();
        members.add(StringTag.valueOf("555"));
        members.add(StringTag.valueOf("666"));
        group.put("members", members);
        data.groupMeta.put("555.666", group);

        ListTag aliceFavorites = new ListTag();
        aliceFavorites.add(StringTag.valueOf("777"));
        data.favorites.put("555", aliceFavorites);

        //? if >=1.20.5 <1.21.10 {
        /*CompoundTag saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        PhoneRegistrySavedData loaded = PhoneRegistrySavedData.load(saved, RegistryAccess.EMPTY);
        *///?}
        //? if <1.20.5 {
        CompoundTag saved = data.save(new CompoundTag());
        PhoneRegistrySavedData loaded = PhoneRegistrySavedData.load(saved);
        //?}
        //? if >=1.21.10 {
        /*CompoundTag saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        PhoneRegistrySavedData loaded = PhoneRegistrySavedData.load(saved);
        *///?}

        assertTrue(loaded.groupMeta.get("555.666") instanceof CompoundTag);
        assertEquals("Squad", fr.lordfinn.crazyphone.utils.NbtCompat.getString((CompoundTag) loaded.groupMeta.get("555.666"), "name"));
        assertTrue(loaded.favorites.get("555") instanceof ListTag);
        assertEquals(1, ((ListTag) loaded.favorites.get("555")).size());
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

        //? if >=1.20.5 <1.21.10 {
        /*CompoundTag saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        PhoneRegistrySavedData loaded = PhoneRegistrySavedData.load(saved, RegistryAccess.EMPTY);
        *///?}
        //? if <1.20.5 {
        CompoundTag saved = data.save(new CompoundTag());
        PhoneRegistrySavedData loaded = PhoneRegistrySavedData.load(saved);
        //?}
        //? if >=1.21.10 {
        /*CompoundTag saved = data.save(new CompoundTag(), RegistryAccess.EMPTY);
        PhoneRegistrySavedData loaded = PhoneRegistrySavedData.load(saved);
        *///?}

        assertTrue(loaded.phones.get("555") instanceof CompoundTag);
        assertEquals("Alice", fr.lordfinn.crazyphone.utils.NbtCompat.getString((CompoundTag) loaded.phones.get("555"), "name"));
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
        // A client that was previously in a group must not keep seeing it after a fresh sync says
        // otherwise (e.g. it was disbanded, or this player was excluded) - readFrom has to actually
        // replace groupMeta/favorites too, not just the fields the original version of this test covered.
        data.groupMeta.put("555.666", new CompoundTag());
        data.favorites.put("555", new ListTag());

        CompoundTag freshState = new CompoundTag();
        freshState.put("phones", new CompoundTag());
        freshState.put("contacts", new CompoundTag());
        freshState.put("mayorVotes", new CompoundTag());
        freshState.put("mayorsCandidates", new CompoundTag());
        freshState.put("lastMayorVoteTimestamps", new CompoundTag());
        freshState.putBoolean("isMayorVotingOn", false);
        freshState.putBoolean("isMayorElectionOn", false);
        freshState.put("groupMeta", new CompoundTag());
        freshState.put("favorites", new CompoundTag());

        data.readFrom(freshState);

        assertTrue(data.phones.isEmpty(), "readFrom should replace, not merge, state (used by client-side packet sync)");
        assertFalse(data.isMayorElectionOn);
        assertTrue(data.groupMeta.isEmpty(), "a stale group membership must not survive a fresh sync");
        assertTrue(data.favorites.isEmpty());
    }

    @Test
    void load_withMissingFields_fallsBackToEmptyRatherThanThrowing() {
        //? if >=1.20.5 <1.21.10 {
        /*PhoneRegistrySavedData data = PhoneRegistrySavedData.load(new CompoundTag(), RegistryAccess.EMPTY);
        *///?}
        //? if <1.20.5 {
        PhoneRegistrySavedData data = PhoneRegistrySavedData.load(new CompoundTag());
        //?}
        //? if >=1.21.10 {
        /*PhoneRegistrySavedData data = PhoneRegistrySavedData.load(new CompoundTag());
        *///?}
        assertTrue(data.phones.isEmpty());
        assertTrue(data.contacts.isEmpty());
        assertFalse(data.isMayorVotingOn);
    }
}
