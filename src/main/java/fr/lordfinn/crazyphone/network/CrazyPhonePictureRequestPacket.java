package fr.lordfinn.crazyphone.network;

//? if neoforge {
//? if >=1.20.5 {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
*///? } else {
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
//?}
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//?}

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PhotoSavedData;
import fr.lordfinn.crazyphone.utils.NetworkAccess;
import fr.lordfinn.crazyphone.utils.PhotoResolution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client -> server: "send me the PNG bytes for these photos" - lazy fetch, sent only when the recipient's
 * client actually needs to render given image bubbles or open the full-size viewer (mirrors
 * VoiceMessageAudioRequestPacket's "only fetch on demand" shape). Carries a whole batch of (photoId,
 * resolution) entries in one packet rather than one packet per photo - {@link
 * fr.lordfinn.crazyphone.client.picture.FabricPictureCache#prefetch} is the main producer of multi-entry
 * requests (e.g. every thumbnail visible in a gallery page or conversation view at once), cutting packet
 * count and per-packet overhead versus firing one request per image; {@code getOrRequest}'s own single-photo
 * fetch just sends a one-entry list, so this replaces the old single-item packet everywhere without a second
 * packet type. Response: {@link CrazyPhonePictureResponsePacket}.
 */
public record CrazyPhonePictureRequestPacket(List<Entry> entries) implements CustomPacketPayload {
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");

    public record Entry(UUID photoId, PhotoResolution resolution) {
    }

    // Defense in depth against a modified/malicious client sending one giant batch - a legitimate client
    // never needs more than a screen's worth of photos in one request.
    private static final int MAX_ENTRIES_PER_REQUEST = 128;

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhonePictureRequestPacket> TYPE = new Type<>(
            Crazyphone.resource("picture_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhonePictureRequestPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhonePictureRequestPacket message) -> {
                        buffer.writeVarInt(message.entries.size());
                        for (Entry entry : message.entries) {
                            buffer.writeUUID(entry.photoId());
                            buffer.writeByte(entry.resolution().ordinal());
                        }
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhonePictureRequestPacket(readEntries(buffer))
            );

    @Override
    public Type<CrazyPhonePictureRequestPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("picture_request");

    public CrazyPhonePictureRequestPacket(FriendlyByteBuf buffer) {
        this(readEntries(buffer));
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buffer.writeUUID(entry.photoId());
            buffer.writeByte(entry.resolution().ordinal());
        }
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if >=1.20.5 {
    /*private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
    *///? } else {
    private static List<Entry> readEntries(FriendlyByteBuf buffer) {
    //?}
        int size = buffer.readVarInt();
        List<Entry> entries = new ArrayList<>(Math.max(0, Math.min(size, MAX_ENTRIES_PER_REQUEST)));
        for (int i = 0; i < size; i++)
            entries.add(new Entry(buffer.readUUID(), PhotoResolution.values()[buffer.readByte()]));
        return entries;
    }

    // Per-player token bucket, one packet's worth of state per online player - a malicious/broken client
    // sending huge or rapid-fire batches gets silently throttled rather than hammering PhotoSavedData reads
    // or saturating the connection; a legitimate client under normal use (batched per screen open/scroll,
    // not per photo) never gets close to this ceiling.
    private static final Map<UUID, long[]> RATE_LIMIT = new ConcurrentHashMap<>();
    private static final long RATE_LIMIT_WINDOW_MILLIS = 1000;
    private static final int RATE_LIMIT_MAX_ENTRIES_PER_WINDOW = 256;

    private static boolean allowRequest(UUID playerId, int entryCount) {
        long now = System.currentTimeMillis();
        long[] state = RATE_LIMIT.computeIfAbsent(playerId, k -> new long[]{now, 0});
        synchronized (state) {
            if (now - state[0] > RATE_LIMIT_WINDOW_MILLIS) {
                state[0] = now;
                state[1] = 0;
            }
            state[1] += entryCount;
            return state[1] <= RATE_LIMIT_MAX_ENTRIES_PER_WINDOW;
        }
    }

    private static void handle(ServerPlayer player, List<Entry> entries) {
        if (entries.isEmpty())
            return;
        if (entries.size() > MAX_ENTRIES_PER_REQUEST) {
            LOGGER.warn("Picture request from {} rejected: {} entries exceeds max {}",
                    player.getScoreboardName(), entries.size(), MAX_ENTRIES_PER_REQUEST);
            return;
        }
        if (!allowRequest(player.getUUID(), entries.size())) {
            LOGGER.warn("Picture request from {} rate-limited ({} entries)", player.getScoreboardName(), entries.size());
            return;
        }

        Level world = player.level();
        PhotoSavedData data = PhotoSavedData.get(world);
        // Vanilla caps a whole encoded packet at 8 MiB (io.netty.handler.codec.EncoderException: "Packet too
        // large") - batching several FULL-resolution entries (each up to Config#photoFullMaxUploadBytes, a
        // few MB) into ONE response easily blows past that, which a single-photo-per-packet request never
        // could. Flushed as multiple response packets instead of one, bounded well under the real cap so
        // per-entry protocol overhead and a batch that's already close to the limit still can't tip it over.
        final int MAX_RESPONSE_PAYLOAD_BYTES = 3_000_000;
        List<CrazyPhonePictureResponsePacket.Entry> batch = new ArrayList<>();
        long batchBytes = 0;
        for (Entry request : entries) {
            PhotoSavedData.PhotoEntry entry = data.getPhoto(request.photoId());
            // Viewing a photo only requires knowing its id (the item's own in-hand/GUI/ground renderer and
            // the full-size viewer all fetch by photoId, never by conversation) - there's no legitimate way
            // to end up with a photoId you shouldn't be able to look at, so reads carry no ownership/
            // conversation check. Capturing a NEW photo is still gated elsewhere (an unlocked phone is
            // required to take one at all) - this is purely about reading bytes for a photo that already
            // exists. A missing entry sends empty bytes, not silence - FabricPictureCache marks that
            // (photoId, resolution) FAILED (so it stops retrying) only once it actually gets a response; not
            // responding at all leaves it stuck IN_FLIGHT forever with no error anywhere to explain why.
            byte[] bytes = entry == null ? new byte[0]
                    : request.resolution() == PhotoResolution.THUMBNAIL ? entry.thumbnail() : entry.full();
            if (!batch.isEmpty() && batchBytes + bytes.length > MAX_RESPONSE_PAYLOAD_BYTES) {
                NetworkAccess.sendToPlayer(player, new CrazyPhonePictureResponsePacket(batch));
                batch = new ArrayList<>();
                batchBytes = 0;
            }
            batch.add(new CrazyPhonePictureResponsePacket.Entry(request.photoId(), request.resolution(), bytes));
            batchBytes += bytes.length;
        }
        if (!batch.isEmpty())
            NetworkAccess.sendToPlayer(player, new CrazyPhonePictureResponsePacket(batch));
    }

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhonePictureRequestPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player, message.entries);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CrazyPhonePictureRequestPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player, message.entries);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhonePictureRequestPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.entries);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhonePictureRequestPacket::handleDataFabric);
    }
    *///?}

    //? if neoforge {
    //? if <1.20.5 {
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    //?} else {
    /*@EventBusSubscriber
    *///?}
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            //? if >=1.20.5 {
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhonePictureRequestPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhonePictureRequestPacket::new, CrazyPhonePictureRequestPacket::handleData);
            //?}
        }
    }
    //?}
}
