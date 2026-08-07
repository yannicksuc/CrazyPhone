package fr.lordfinn.crazyphone.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.network.PacketDistributor;

import fr.lordfinn.crazyphone.network.PlayerPhoneStateSyncPacket;

/** Small per-player UI state (which phone screen is open and its navigation history). Cheap, per-player, synced as-is - not part of the crash fix. */
public class PlayerPhoneState implements INBTSerializable<CompoundTag> {
    public String currentCrazyPhoneScreenOpened = "";
    public String crazyPhoneScreenHistory = "";

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider lookupProvider) {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("currentCrazyPhoneScreenOpened", currentCrazyPhoneScreenOpened);
        nbt.putString("crazyPhoneScreenHistory", crazyPhoneScreenHistory);
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider lookupProvider, CompoundTag nbt) {
        currentCrazyPhoneScreenOpened = nbt.getString("currentCrazyPhoneScreenOpened");
        crazyPhoneScreenHistory = nbt.getString("crazyPhoneScreenHistory");
    }

    public void syncTo(Entity entity) {
        if (entity instanceof ServerPlayer serverPlayer)
            PacketDistributor.sendToPlayer(serverPlayer, new PlayerPhoneStateSyncPacket(this));
    }
}
