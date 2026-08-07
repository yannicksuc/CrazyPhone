package fr.lordfinn.crazyphone.utils;

import de.maxhenkel.camera.ImageData;
import fr.lordfinn.crazyphone.client.gui.components.MessageData;
import net.minecraft.nbt.CompoundTag;
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
        assertTrue(message.getImage().isEmpty());
    }

    @Test
    void getMessageFromTag_nullTag_returnsNull() {
        assertNull(CrazyPhoneHelper.getMessageFromTag(null));
    }

    @Test
    void imageDataToCompoundTag_encodesIdAndTimeAndOwner() {
        ImageData dummy = ImageData.dummy();
        CompoundTag tag = CrazyPhoneHelper.imageDataToCompoundTag(dummy);

        assertEquals(dummy.getId().getMostSignificantBits(), tag.getLong("image_id_most"));
        assertEquals(dummy.getId().getLeastSignificantBits(), tag.getLong("image_id_least"));
        assertEquals(dummy.getTime(), tag.getLong("image_time"));
        assertEquals(dummy.getOwner(), tag.getString("owner"));
    }
}
