package fr.lordfinn.crazyphone.procedures;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.LevelAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CrazyPhoneGetGroupsProcedure needs a LevelAccessor purely so CrazyPhoneHelper#getGroupMeta /
 * encodeItemStack can reach registryAccess() - a Mockito stub returning RegistryAccess.EMPTY is enough as
 * long as every group icon involved stays ItemStack.EMPTY (encode/decode both short-circuit before ever
 * touching the codec for an empty stack), which is true for every case tested here.
 */
class GroupProceduresTest {

    private final LevelAccessor world = mock(LevelAccessor.class);

    GroupProceduresTest() {
        when(world.registryAccess()).thenReturn(RegistryAccess.EMPTY);
    }

    @AfterEach
    void resetClientSideSingleton() {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        registry.phones = new CompoundTag();
        registry.groupMeta = new CompoundTag();
    }

    private static void registerPhone(String number, String name) {
        CompoundTag phone = new CompoundTag();
        phone.putString("name", name);
        PhoneRegistrySavedData.get(null).phones.put(number, phone);
    }

    private static void giveGroupMembership(String owner, String conversationId) {
        CompoundTag phone = PhoneRegistrySavedData.get(null).phones.get(owner) instanceof CompoundTag existing
                ? existing : new CompoundTag();
        ListTag groups = phone.get("groups") instanceof ListTag existing ? existing : new ListTag();
        groups.add(StringTag.valueOf(conversationId));
        phone.put("groups", groups);
        PhoneRegistrySavedData.get(null).phones.put(owner, phone);
    }

    private static void defineGroup(String conversationId, String name, String admin, String... members) {
        CompoundTag meta = new CompoundTag();
        meta.putString("name", name);
        meta.putString("admin", admin);
        ListTag memberList = new ListTag();
        for (String m : members)
            memberList.add(StringTag.valueOf(m));
        meta.put("members", memberList);
        PhoneRegistrySavedData.get(null).groupMeta.put(conversationId, meta);
    }

    @Test
    void execute_nullOwner_returnsEmpty() {
        assertTrue(CrazyPhoneGetGroupsProcedure.execute(world, null).isEmpty());
    }

    @Test
    void execute_unregisteredOwner_returnsEmpty() {
        assertTrue(CrazyPhoneGetGroupsProcedure.execute(world, "555").isEmpty());
    }

    @Test
    void execute_ownerWithNoGroupsTag_returnsEmpty() {
        registerPhone("555", "Alice");
        assertTrue(CrazyPhoneGetGroupsProcedure.execute(world, "555").isEmpty());
    }

    @Test
    void execute_groupIdReferencedButMissingFromGroupMeta_producesNoEntryNotACrash() {
        // Dangling reference: owner's phone still lists a group id that groupMeta no longer has an entry
        // for. getGroupMeta() falls back to deriving members from the conversationId itself (which won't
        // resolve to any registered phone for a "group-<uuid>" id), so the entry ends up with zero
        // resolvable members and must be skipped, not thrown.
        registerPhone("555", "Alice");
        giveGroupMembership("555", "group-doesnotexist");
        assertDoesNotThrow(() -> assertTrue(CrazyPhoneGetGroupsProcedure.execute(world, "555").isEmpty()));
    }

    @Test
    void execute_memberWhosePhoneWasDeleted_isExcludedFromTheGroupEntry() {
        registerPhone("555", "Alice");
        giveGroupMembership("555", "group-1");
        // "666" is a member per groupMeta but was never registered (or was later deleted).
        defineGroup("group-1", "Squad", "555", "555", "666");

        ListTag groups = CrazyPhoneGetGroupsProcedure.execute(world, "555");
        assertTrue(groups.isEmpty(), "a group whose only OTHER member is unresolvable must produce no entry");
    }

    @Test
    void execute_happyPath_resolvesGroupWithOtherMembersExcludingSelf() {
        registerPhone("555", "Alice");
        registerPhone("666", "Bob");
        giveGroupMembership("555", "group-1");
        defineGroup("group-1", "Squad", "555", "555", "666");

        ListTag groups = CrazyPhoneGetGroupsProcedure.execute(world, "555");

        assertEquals(1, groups.size());
        CompoundTag entry = groups.getCompound(0);
        assertEquals("group-1", entry.getString("conversationId"));
        assertEquals("Squad", entry.getString("name"));
        assertEquals("555", entry.getString("admin"));
        ListTag members = entry.getList("members", net.minecraft.nbt.Tag.TAG_COMPOUND);
        assertEquals(1, members.size(), "the owner's own record must be excluded from the members list");
        assertEquals("666", members.getCompound(0).getString("number"));
        assertEquals("Bob", members.getCompound(0).getString("name"));
    }

    @Test
    void execute_multipleGroups_returnsOneEntryPerGroup() {
        registerPhone("555", "Alice");
        registerPhone("666", "Bob");
        registerPhone("777", "Carol");
        giveGroupMembership("555", "group-1");
        giveGroupMembership("555", "group-2");
        defineGroup("group-1", "Squad A", "555", "555", "666");
        defineGroup("group-2", "Squad B", "555", "555", "777");

        ListTag groups = CrazyPhoneGetGroupsProcedure.execute(world, "555");
        assertEquals(2, groups.size());
    }
}
