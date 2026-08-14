package fr.lordfinn.crazyphone.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
//? if <1.21.10 {
import net.neoforged.neoforge.common.util.INBTSerializable;
//? } else {
/*import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}
//? if >=1.20.5 {
/*import net.neoforged.neoforge.network.PacketDistributor;
*///? } else {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import fr.lordfinn.crazyphone.network.PlayerPhoneStateSyncPacket;
import org.jetbrains.annotations.NotNull;

/** Small per-player UI state (which phone screen is open and its navigation history). Cheap, per-player, synced as-is - not part of the crash fix. */
//? if <1.21.10 {
public class PlayerPhoneState implements INBTSerializable<CompoundTag> {
//? } else {
/*public class PlayerPhoneState implements ValueIOSerializable {
*///?}
    public String currentCrazyPhoneScreenOpened = "";
    public String crazyPhoneScreenHistory = "";

    private static String readString(CompoundTag nbt, String key) {
        //? if <1.21.10 {
        return nbt.getString(key);
        //? } else {
        /*return nbt.getStringOr(key, "");
        *///?}
    }

    //? if >=1.21.10 {
    /*@Override
    public void serialize(ValueOutput output) {
        output.putString("currentCrazyPhoneScreenOpened", currentCrazyPhoneScreenOpened);
        output.putString("crazyPhoneScreenHistory", crazyPhoneScreenHistory);
    }

    @Override
    public void deserialize(ValueInput input) {
        currentCrazyPhoneScreenOpened = input.getStringOr("currentCrazyPhoneScreenOpened", "");
        crazyPhoneScreenHistory = input.getStringOr("crazyPhoneScreenHistory", "");
    }
    *///?}

    //? if >=1.20.5 {
    /*@Override
    public CompoundTag serializeNBT(HolderLookup.@NotNull Provider lookupProvider) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("currentCrazyPhoneScreenOpened", currentCrazyPhoneScreenOpened);
        nbt.putString("crazyPhoneScreenHistory", crazyPhoneScreenHistory);
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider lookupProvider, CompoundTag nbt) {
        currentCrazyPhoneScreenOpened = readString(nbt, "currentCrazyPhoneScreenOpened");
        crazyPhoneScreenHistory = readString(nbt, "crazyPhoneScreenHistory");
    }
    *///? } else {
    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("currentCrazyPhoneScreenOpened", currentCrazyPhoneScreenOpened);
        nbt.putString("crazyPhoneScreenHistory", crazyPhoneScreenHistory);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        currentCrazyPhoneScreenOpened = nbt.getString("currentCrazyPhoneScreenOpened");
        crazyPhoneScreenHistory = nbt.getString("crazyPhoneScreenHistory");
    }
    //?}

    public void syncTo(Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer)
            //? if >=1.20.5 {
            /*PacketDistributor.sendToPlayer(serverPlayer, new PlayerPhoneStateSyncPacket(this));
            *///? } else {
            PacketDistributor.PLAYER.with(serverPlayer).send(new PlayerPhoneStateSyncPacket(this));
            //?}
    }
}
