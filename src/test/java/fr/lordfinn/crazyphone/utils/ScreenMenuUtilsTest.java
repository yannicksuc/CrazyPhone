package fr.lordfinn.crazyphone.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the screen-navigation-stack string encoding (screenId[;screenData], history joined by "|").
 * The player-mutating methods (pushScreen/popScreen/resetToHomeScreen) wrap this same parsing logic
 * around a live ServerPlayer and are better suited to GameTest-style integration coverage than a plain
 * unit test; what's tested here is the part that's actually error-prone (delimiter parsing).
 */
class ScreenMenuUtilsTest {

    @Test
    void parseScreenIdFromTag_withoutData_returnsWholeTag() {
        assertEquals("crazyphone:crazyphone_home_screen",
                ScreenMenuUtils.parseScreenIdFromTag("crazyphone:crazyphone_home_screen"));
    }

    @Test
    void parseScreenIdFromTag_withData_returnsOnlyTheIdPart() {
        assertEquals("crazyphone:crazy_phone_conversation",
                ScreenMenuUtils.parseScreenIdFromTag("crazyphone:crazy_phone_conversation;111.222"));
    }

    @Test
    void parseScreenDataFromTag_withoutData_returnsNull() {
        assertNull(ScreenMenuUtils.parseScreenDataFromTag("crazyphone:crazyphone_home_screen"));
    }

    @Test
    void parseScreenDataFromTag_withData_returnsOnlyTheDataPart() {
        assertEquals("111.222",
                ScreenMenuUtils.parseScreenDataFromTag("crazyphone:crazy_phone_conversation;111.222"));
    }

    @Test
    void parseScreenDataFromTag_withTrailingSemicolonAndNoData_returnsNull() {
        assertNull(ScreenMenuUtils.parseScreenDataFromTag("crazyphone:crazy_phone_conversation;"));
    }

    @Test
    void parseScreenDataFromTag_dataContainingSemicolons_keepsEverythingAfterFirstSemicolon() {
        // conversation IDs are two numbers joined by a dot, but this guards against any future screenData
        // value that itself might contain a semicolon.
        assertEquals("a;b;c", ScreenMenuUtils.parseScreenDataFromTag("crazyphone:some_screen;a;b;c"));
    }

    @Test
    void getScreenHistory_nullOrEmpty_returnsEmptyList() {
        assertTrue(ScreenMenuUtils.getScreenHistory(null).isEmpty());
        assertTrue(ScreenMenuUtils.getScreenHistory("").isEmpty());
    }

    @Test
    void getScreenHistory_splitsOnPipe() {
        List<String> history = ScreenMenuUtils.getScreenHistory(
                "crazyphone:crazyphone_home_screen|crazyphone:crazy_phone_contacts_screen|crazyphone:crazy_phone_conversation;111.222");

        assertEquals(3, history.size());
        assertEquals("crazyphone:crazyphone_home_screen", history.get(0));
        assertEquals("crazyphone:crazy_phone_contacts_screen", history.get(1));
        assertEquals("crazyphone:crazy_phone_conversation;111.222", history.get(2));
    }

    @Test
    void serializeScreenHistory_joinsWithPipe() {
        String serialized = ScreenMenuUtils.serializeScreenHistory(
                List.of("crazyphone:crazyphone_home_screen", "crazyphone:crazy_phone_contacts_screen"));

        assertEquals("crazyphone:crazyphone_home_screen|crazyphone:crazy_phone_contacts_screen", serialized);
    }

    @Test
    void getScreenHistory_and_serializeScreenHistory_roundTrip() {
        String original = "crazyphone:crazyphone_home_screen|crazyphone:crazy_phone_conversation;111.222";
        List<String> history = ScreenMenuUtils.getScreenHistory(original);
        assertEquals(original, ScreenMenuUtils.serializeScreenHistory(history));
    }
}
