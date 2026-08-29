package fr.lordfinn.crazyphone.utils;

import fr.lordfinn.crazyphone.client.gui.components.MessageData;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrazyPhoneHelperTest {

    @Test
    void getConversationNumber_isOrderIndependent() {
        // Two players' phone numbers must map to the SAME conversation id regardless of who initiates -
        // otherwise the same conversation would fork into two independent, desynced histories.
        String ab = CrazyPhoneHelper.getConversationNumber("222", "111");
        String ba = CrazyPhoneHelper.getConversationNumber("111", "222");
        assertEquals(ab, ba);
        assertEquals("111.222", ab);
    }

    @Test
    void getConversationNumber_usesBothNumbers() {
        // Regression test for a bug found during porting: the two-arg overload passed numberA twice
        // instead of numberA/numberB, so every conversation ID collapsed to a single repeated number.
        String id = CrazyPhoneHelper.getConversationNumber("111", "999");
        assertTrue(id.contains("999"), "conversation id must include the second participant's number, was: " + id);
        assertEquals("111.999", id);
    }

    @Test
    void getNumbersFromConversationId_splitsOnDot() {
        List<String> numbers = CrazyPhoneHelper.getNumbersFromConversationId("111.222");
        assertEquals(List.of("111", "222"), numbers);
    }

    @Test
    void getConversationNumber_and_getNumbersFromConversationId_roundTrip() {
        String id = CrazyPhoneHelper.getConversationNumber("555", "333");
        List<String> numbers = CrazyPhoneHelper.getNumbersFromConversationId(id);
        assertTrue(numbers.contains("555"));
        assertTrue(numbers.contains("333"));
    }

    @Test
    void getMessageFromTag_withoutImage_parsesTextFields() {
        CompoundTag tag = new CompoundTag();
        tag.putString("sender", "111");
        tag.putString("value", "hello there");
        tag.putInt("timecode", 42);

        MessageData message = CrazyPhoneHelper.getMessageFromTag(tag);

        assertNotNull(message);
        assertEquals("111", message.getSender());
        assertEquals("hello there", message.getMessage());
        assertEquals(42, message.getTimecode());
        assertFalse(message.isImage());
    }

    @Test
    void getMessageFromTag_nullTag_returnsNull() {
        assertNull(CrazyPhoneHelper.getMessageFromTag(null));
    }

    // --- getGroupMembers ---

    @AfterEach
    void resetClientSideSingleton() {
        PhoneRegistrySavedData.get(null).groupMeta = new CompoundTag();
    }

    @Test
    void getGroupMembers_plainOneOnOneConversation_derivesFromTheIdItself() {
        // No groupMeta entry at all for "111.222" - a genuine 1:1 conversation never has one.
        assertEquals(List.of("111", "222"), CrazyPhoneHelper.getGroupMembers(null, "111.222"));
    }

    @Test
    void getGroupMembers_groupWithLiveMembers_usesGroupMetaNotTheConversationId() {
        CompoundTag meta = new CompoundTag();
        ListTag members = new ListTag();
        members.add(StringTag.valueOf("111"));
        members.add(StringTag.valueOf("222"));
        members.add(StringTag.valueOf("333"));
        meta.put("members", members);
        PhoneRegistrySavedData.get(null).groupMeta.put("group-abc", meta);

        assertEquals(List.of("111", "222", "333"), CrazyPhoneHelper.getGroupMembers(null, "group-abc"));
    }

    @Test
    void getGroupMembers_groupShrunkToEmptyMembers_fallsBackToConversationIdDerivation() {
        // Every member excluded - readMembers() returns an empty list, and getGroupMembers must not
        // return that empty list as-is (which would silently lock everyone out of a group id that still
        // technically exists), falling back to id-derived numbers instead. This matches the id format a
        // group id would never actually have ("group-..."), so the fallback naturally yields a harmless
        // single-element list rather than granting access to anyone real - the important behavior locked
        // in here is simply "never returns empty".
        CompoundTag meta = new CompoundTag();
        meta.put("members", new ListTag());
        PhoneRegistrySavedData.get(null).groupMeta.put("group-abc", meta);

        assertFalse(CrazyPhoneHelper.getGroupMembers(null, "group-abc").isEmpty());
    }

    @Test
    void getGroupMembers_excludedMemberLosesAccessImmediately() {
        // The live-membership contract this method exists for: a group's own id never changes even after
        // someone is excluded, so callers MUST re-resolve through here on every check, not cache the
        // original participant list from before the exclusion.
        CompoundTag meta = new CompoundTag();
        ListTag members = new ListTag();
        members.add(StringTag.valueOf("111"));
        members.add(StringTag.valueOf("222"));
        members.add(StringTag.valueOf("333"));
        meta.put("members", members);
        PhoneRegistrySavedData.get(null).groupMeta.put("group-abc", meta);
        assertTrue(CrazyPhoneHelper.getGroupMembers(null, "group-abc").contains("333"));

        // Simulate excludeGroupMember("333"): the members list shrinks in place.
        ListTag afterExclusion = new ListTag();
        afterExclusion.add(StringTag.valueOf("111"));
        afterExclusion.add(StringTag.valueOf("222"));
        meta.put("members", afterExclusion);

        assertFalse(CrazyPhoneHelper.getGroupMembers(null, "group-abc").contains("333"),
                "an excluded member must lose access on the very next check, not just after their client resyncs");
    }
}
