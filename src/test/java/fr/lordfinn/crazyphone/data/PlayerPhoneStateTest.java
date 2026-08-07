package fr.lordfinn.crazyphone.data;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerPhoneStateTest {

    @Test
    void serializeAndDeserialize_roundTrips() {
        PlayerPhoneState state = new PlayerPhoneState();
        state.currentCrazyPhoneScreenOpened = "crazyphone:crazy_phone_conversation;111.222";
        state.crazyPhoneScreenHistory = "crazyphone:crazyphone_home_screen|crazyphone:crazy_phone_contacts_screen";

        CompoundTag nbt = state.serializeNBT(RegistryAccess.EMPTY);

        PlayerPhoneState restored = new PlayerPhoneState();
        restored.deserializeNBT(RegistryAccess.EMPTY, nbt);

        assertEquals(state.currentCrazyPhoneScreenOpened, restored.currentCrazyPhoneScreenOpened);
        assertEquals(state.crazyPhoneScreenHistory, restored.crazyPhoneScreenHistory);
    }

    @Test
    void newInstance_defaultsToEmptyStrings() {
        PlayerPhoneState state = new PlayerPhoneState();
        assertEquals("", state.currentCrazyPhoneScreenOpened);
        assertEquals("", state.crazyPhoneScreenHistory);
    }
}
