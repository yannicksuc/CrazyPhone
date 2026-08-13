package fr.lordfinn.crazyphone.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

import fr.lordfinn.crazyphone.network.PlayerPhoneStateSyncPacket;
import org.jetbrains.annotations.NotNull;

/** Small per-player UI state (which phone screen is open and its navigation history). Cheap, per-player, synced as-is - not part of the crash fix. */
public class PlayerPhoneState implements INBTSerializable<CompoundTag> {
    public String currentCrazyPhoneScreenOpened = "";
    public String crazyPhoneScreenHistory = "";

    //? if >=1.20.5 {
    @Override
    public CompoundTag serializeNBT(HolderLookup.@NotNull Provider lookupProvider) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("currentCrazyPhoneScreenOpened", currentCrazyPhoneScreenOpened);
        nbt.putString("crazyPhoneScreenHistory", crazyPhoneScreenHistory);
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider lookupProvider, CompoundTag nbt) {
        currentCrazyPhoneScreenOpened = nbt.getString("currentCrazyPhoneScreenOpened");
        crazyPhoneScreenHistory = nbt.getString("crazyPhoneScreenHistory");
    }
    //? } else {
    /*@Override
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
    *///?}

    public void syncTo(Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer)
            //? if >=1.20.5 {
            PacketDistributor.sendToPlayer(serverPlayer, new PlayerPhoneStateSyncPacket(this));
            //? } else {
            /*PacketDistributor.PLAYER.with(serverPlayer).send(new PlayerPhoneStateSyncPacket(this));
            *///?}
    }
}
