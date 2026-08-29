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

import java.util.UUID;

/**
 * Client -> server: "send me the PNG bytes for this photo, at this resolution" - lazy fetch, sent only when
 * the recipient's client actually needs to render a given image bubble or open the full-size viewer
 * (mirrors VoiceMessageAudioRequestPacket's "only fetch on demand" shape). Response:
 * {@link CrazyPhonePictureResponsePacket}.
 */
public record CrazyPhonePictureRequestPacket(UUID photoId, PhotoResolution resolution) implements CustomPacketPayload {
    // Temporary diagnostic logging for the "viewer shows nothing" investigation - remove once confirmed fixed
    // live (see FabricPictureCache's matching client-side logging).
    private static final Logger DEBUG_LOGGER = LoggerFactory.getLogger("crazyphone-picture-debug");

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhonePictureRequestPacket> TYPE = new Type<>(
            Crazyphone.resource("picture_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhonePictureRequestPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhonePictureRequestPacket message) -> {
                        buffer.writeUUID(message.photoId);
                        buffer.writeByte(message.resolution.ordinal());
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhonePictureRequestPacket(
                            buffer.readUUID(),
                            PhotoResolution.values()[buffer.readByte()]
                    )
            );

    @Override
    public Type<CrazyPhonePictureRequestPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("picture_request");

    public CrazyPhonePictureRequestPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID(), PhotoResolution.values()[buffer.readByte()]);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(photoId);
        buffer.writeByte(resolution.ordinal());
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    private static void handle(ServerPlayer player, UUID photoId, PhotoResolution resolution) {
        Level world = player.level();
        PhotoSavedData.PhotoEntry entry = PhotoSavedData.get(world).getPhoto(photoId);
        DEBUG_LOGGER.info("{} request from {} for photo {}: entry={}", resolution, player.getScoreboardName(), photoId,
                entry == null ? "MISSING" : "owner=" + entry.owner() + " conv=" + entry.conversationId()
                        + " thumbBytes=" + entry.thumbnail().length + " fullBytes=" + entry.full().length);
        if (entry == null) {
            // Empty bytes, not silence - FabricPictureCache marks a request FAILED (so it stops retrying and
            // the caller can fall back to a placeholder) only once it actually gets a response; not
            // responding at all leaves it stuck IN_FLIGHT forever with no error anywhere to explain why.
            NetworkAccess.sendToPlayer(player, new CrazyPhonePictureResponsePacket(photoId, resolution, new byte[0]));
            return;
        }

        // Viewing a photo only requires knowing its id (the item's own in-hand/GUI/ground renderer and the
        // full-size viewer all fetch by photoId, never by conversation) - there's no legitimate way to end up
        // with a photoId you shouldn't be able to look at, and gating this on "which phone number is
        // currently in your main hand" produced real false negatives (a player who owns/registered multiple
        // phones sees their OWN earlier photos go blank the moment a different phone is in hand). Capturing
        // a NEW photo is still gated elsewhere (an unlocked phone is required to take one at all) - this is
        // purely about reading bytes for a photo that already exists.
        byte[] bytes = resolution == PhotoResolution.THUMBNAIL ? entry.thumbnail() : entry.full();
        DEBUG_LOGGER.info("Sending {} bytes for {} photo {} to {}", bytes.length, resolution, photoId, player.getScoreboardName());
        NetworkAccess.sendToPlayer(player, new CrazyPhonePictureResponsePacket(photoId, resolution, bytes));
    }

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhonePictureRequestPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player, message.photoId, message.resolution);
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
            handle(player, message.photoId, message.resolution);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhonePictureRequestPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.photoId, message.resolution);
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
