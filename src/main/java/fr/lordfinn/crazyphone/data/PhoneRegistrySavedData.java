package fr.lordfinn.crazyphone.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
//? if <1.20.5 {
import net.minecraft.util.datafix.DataFixTypes;
//?}
//? if >=1.21.10 {
/*import com.mojang.serialization.Codec;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedDataType;
*///?}
// Real vanilla SavedData.Factory always requires a DataFixTypes, at every version it exists at all - see
// ConversationSavedData.java's import block for the full javap-verified explanation.
//? if fabric && >=1.20.5 <1.21.10 {
/*import net.minecraft.util.datafix.DataFixTypes;
*///?}

import fr.lordfinn.crazyphone.network.PhoneRegistrySyncPacket;
import fr.lordfinn.crazyphone.utils.NbtCompat;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import org.jetbrains.annotations.NotNull;

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
    /** Group conversation metadata, keyed by conversationId: {@code {name, icon, admin, members: [numbers...]}}.
     * {@code members} is the authoritative, live membership list for a group - unlike a 1:1 conversation,
     * a group's conversationId (sorted-joined participant numbers) stays fixed for the life of the
     * conversation even after someone is excluded, so membership can't be re-derived from the id alone
     * once exclusion is possible. Bounded by group count, so it's safe alongside the rest of this
     * always-synced registry. */
    public CompoundTag groupMeta = new CompoundTag();
    /** Favorited contact numbers, keyed by owner phone number -> ListTag of favorited numbers. A subset
     * of that owner's {@code contacts} list, shown pinned in their own section above the rest of the
     * contacts grid. Bounded by contact count, so it's safe alongside the rest of this always-synced registry. */
    public CompoundTag favorites = new CompoundTag();

    //? if <1.20.5 {
    public static PhoneRegistrySavedData load(CompoundTag tag) {
        PhoneRegistrySavedData data = new PhoneRegistrySavedData();
        data.read(tag);
        return data;
    }
    //?}
    //? if >=1.20.5 <1.21.10 {
    /*public static PhoneRegistrySavedData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        PhoneRegistrySavedData data = new PhoneRegistrySavedData();
        data.read(tag);
        return data;
    }
    *///?}
    //? if >=1.21.10 {
    /*// 1.21.10 dropped SavedData's own save(CompoundTag)/Factory contract for a Codec-driven SavedDataType
    // (see #type() below), but read()/writeNbt() are also this class's own hand-rolled NETWORK serialization
    // for PhoneRegistrySyncPacket - completely unrelated to how SavedData persists to disk - so they stay
    // exactly as they were, just no longer wired through the vanilla save()/load() override points.
    public static PhoneRegistrySavedData load(CompoundTag tag) {
        PhoneRegistrySavedData data = new PhoneRegistrySavedData();
        data.read(tag);
        return data;
    }

    public static final Codec<PhoneRegistrySavedData> CODEC = CompoundTag.CODEC.xmap(PhoneRegistrySavedData::load,
            data -> data.writeNbt(new CompoundTag()));

    public static final SavedDataType<PhoneRegistrySavedData> TYPE =
            new SavedDataType<>(DATA_NAME, PhoneRegistrySavedData::new, CODEC, DataFixTypes.LEVEL);
    *///?}

    private void read(CompoundTag nbt) {
        this.phones = nbt.get("phones") instanceof CompoundTag t ? t : new CompoundTag();
        this.contacts = nbt.get("contacts") instanceof CompoundTag t ? t : new CompoundTag();
        this.mayorVotes = nbt.get("mayorVotes") instanceof CompoundTag t ? t : new CompoundTag();
        this.mayorsCandidates = nbt.get("mayorsCandidates") instanceof CompoundTag t ? t : new CompoundTag();
        this.lastMayorVoteTimestamps = nbt.get("lastMayorVoteTimestamps") instanceof CompoundTag t ? t : new CompoundTag();
        this.isMayorVotingOn = NbtCompat.getBoolean(nbt, "isMayorVotingOn");
        this.isMayorElectionOn = NbtCompat.getBoolean(nbt, "isMayorElectionOn");
        this.groupMeta = nbt.get("groupMeta") instanceof CompoundTag t ? t : new CompoundTag();
        this.favorites = nbt.get("favorites") instanceof CompoundTag t ? t : new CompoundTag();
    }

    //? if <1.20.5 {
    @Override
    public @NotNull CompoundTag save(CompoundTag nbt) {
        return writeNbt(nbt);
    }
    //?}
    //? if >=1.20.5 <1.21.10 {
    /*@Override
    public @NotNull CompoundTag save(CompoundTag nbt, HolderLookup.@NotNull Provider lookupProvider) {
        return writeNbt(nbt);
    }
    *///?}
    //? if >=1.21.10 {
    /*// No longer a SavedData override point (see #CODEC above) - this is now purely this class's own
    // network-sync serialization, reused by the Codec's encode side too so there's still only one place
    // that actually knows this class's on-the-wire/on-disk shape.
    public @NotNull CompoundTag save(CompoundTag nbt, HolderLookup.@NotNull Provider lookupProvider) {
        return writeNbt(nbt);
    }
    *///?}

    private CompoundTag writeNbt(CompoundTag nbt) {
        nbt.put("phones", this.phones);
        nbt.put("contacts", this.contacts);
        nbt.put("mayorVotes", this.mayorVotes);
        nbt.put("mayorsCandidates", this.mayorsCandidates);
        nbt.put("lastMayorVoteTimestamps", this.lastMayorVoteTimestamps);
        nbt.putBoolean("isMayorVotingOn", isMayorVotingOn);
        nbt.putBoolean("isMayorElectionOn", isMayorElectionOn);
        nbt.put("groupMeta", this.groupMeta);
        nbt.put("favorites", this.favorites);
        return nbt;
    }

    /** Marks dirty for disk persistence and pushes the (small, bounded) registry to every online player. */
    public void syncToAll(LevelAccessor world) {
        this.setDirty();
        if (world instanceof Level level && !level.isClientSide())
            NetworkAccess.sendToAllPlayers(level.getServer(), new PhoneRegistrySyncPacket(this));
    }

    /** Marks dirty for disk persistence and pushes the registry to a single player, e.g. right after they join. */
    public void syncTo(ServerPlayer player) {
        this.setDirty();
        NetworkAccess.sendToPlayer(player, new PhoneRegistrySyncPacket(this));
    }

    static final PhoneRegistrySavedData CLIENT_SIDE = new PhoneRegistrySavedData();

    public static PhoneRegistrySavedData get(LevelAccessor world) {
        if (world instanceof ServerLevelAccessor serverLevelAcc) {
            return serverLevelAcc.getLevel().getServer().overworld().getDataStorage()
                    //? if neoforge && <1.20.5 {
                    .computeIfAbsent(new SavedData.Factory<>(PhoneRegistrySavedData::new, PhoneRegistrySavedData::load, DataFixTypes.LEVEL), DATA_NAME);
                    //?}
                    //? if neoforge && >=1.20.5 <1.21.10 {
                    /*.computeIfAbsent(new SavedData.Factory<>(PhoneRegistrySavedData::new, PhoneRegistrySavedData::load), DATA_NAME);
                    *///?}
                    //? if neoforge && >=1.21.10 {
                    /*.computeIfAbsent(TYPE);
                    *///?}
                    // Fabric branches use real vanilla SavedData/DimensionDataStorage signatures (javap-
                    // verified - see ConversationSavedData.java's import block for the full explanation).
                    //? if fabric && <1.20.5 {
                    /*.computeIfAbsent(PhoneRegistrySavedData::load, PhoneRegistrySavedData::new, DATA_NAME);
                    *///?}
                    //? if fabric && >=1.20.5 {
                    /*.computeIfAbsent(new SavedData.Factory<>(PhoneRegistrySavedData::new, PhoneRegistrySavedData::load, DataFixTypes.LEVEL), DATA_NAME);
                    *///?}
        }
        return CLIENT_SIDE;
    }

    public void readFrom(CompoundTag nbt) {
        read(nbt);
    }
}
