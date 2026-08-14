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

        //? if >=1.20.5 <1.21.10 {
        /*CompoundTag nbt = state.serializeNBT(RegistryAccess.EMPTY);

        PlayerPhoneState restored = new PlayerPhoneState();
        restored.deserializeNBT(RegistryAccess.EMPTY, nbt);
        *///?}
        //? if <1.20.5 {
        CompoundTag nbt = state.serializeNBT();

        PlayerPhoneState restored = new PlayerPhoneState();
        restored.deserializeNBT(nbt);
        //?}
        //? if >=1.21.10 {
        /*net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, RegistryAccess.EMPTY);
        state.serialize(output);
        CompoundTag nbt = output.buildResult();

        PlayerPhoneState restored = new PlayerPhoneState();
        net.minecraft.world.level.storage.ValueInput input = net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, RegistryAccess.EMPTY, nbt);
        restored.deserialize(input);
        *///?}

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
