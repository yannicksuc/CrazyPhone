package fr.lordfinn.crazyphone.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

import fr.lordfinn.crazyphone.network.PhoneRegistrySyncPacket;

/**
 * Bounded, always-synced phone state: one entry per registered phone/contact/mayor record.
 * Grows with the number of players/phones, never with message volume, so it stays small
 * for the lifetime of a server and is safe to send in full on every login.
 *
 * Message history lives in {@link ConversationSavedData} instead, which is never broadcast wholesale.
 */
public class PhoneRegistrySavedData extends SavedData {
    public static final String DATA_NAME = "crazyphone_registry";

    public CompoundTag phones = new CompoundTag();
    public CompoundTag contacts = new CompoundTag();
    public CompoundTag mayorVotes = new CompoundTag();
    public CompoundTag mayorsCandidates = new CompoundTag();
    public CompoundTag lastMayorVoteTimestamps = new CompoundTag();
    public boolean isMayorVotingOn = false;
    public boolean isMayorElectionOn = false;

    public static PhoneRegistrySavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        PhoneRegistrySavedData data = new PhoneRegistrySavedData();
        data.read(tag);
        return data;
    }

    private void read(CompoundTag nbt) {
        this.phones = nbt.get("phones") instanceof CompoundTag t ? t : new CompoundTag();
        this.contacts = nbt.get("contacts") instanceof CompoundTag t ? t : new CompoundTag();
        this.mayorVotes = nbt.get("mayorVotes") instanceof CompoundTag t ? t : new CompoundTag();
        this.mayorsCandidates = nbt.get("mayorsCandidates") instanceof CompoundTag t ? t : new CompoundTag();
        this.lastMayorVoteTimestamps = nbt.get("lastMayorVoteTimestamps") instanceof CompoundTag t ? t : new CompoundTag();
        this.isMayorVotingOn = nbt.getBoolean("isMayorVotingOn");
        this.isMayorElectionOn = nbt.getBoolean("isMayorElectionOn");
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider lookupProvider) {
        nbt.put("phones", this.phones);
        nbt.put("contacts", this.contacts);
        nbt.put("mayorVotes", this.mayorVotes);
        nbt.put("mayorsCandidates", this.mayorsCandidates);
        nbt.put("lastMayorVoteTimestamps", this.lastMayorVoteTimestamps);
        nbt.putBoolean("isMayorVotingOn", isMayorVotingOn);
        nbt.putBoolean("isMayorElectionOn", isMayorElectionOn);
        return nbt;
    }

    /** Marks dirty for disk persistence and pushes the (small, bounded) registry to every online player. */
    public void syncToAll(LevelAccessor world) {
        this.setDirty();
        if (world instanceof Level level && !level.isClientSide())
            PacketDistributor.sendToAllPlayers(new PhoneRegistrySyncPacket(this));
    }

    /** Marks dirty for disk persistence and pushes the registry to a single player, e.g. right after they join. */
    public void syncTo(ServerPlayer player) {
        this.setDirty();
        PacketDistributor.sendToPlayer(player, new PhoneRegistrySyncPacket(this));
    }

    static final PhoneRegistrySavedData CLIENT_SIDE = new PhoneRegistrySavedData();

    public static PhoneRegistrySavedData get(LevelAccessor world) {
        if (world instanceof ServerLevelAccessor serverLevelAcc) {
            return serverLevelAcc.getLevel().getServer().overworld().getDataStorage()
                    .computeIfAbsent(new SavedData.Factory<>(PhoneRegistrySavedData::new, PhoneRegistrySavedData::load), DATA_NAME);
        }
        return CLIENT_SIDE;
    }

    public void readFrom(CompoundTag nbt) {
        read(nbt);
    }
}
