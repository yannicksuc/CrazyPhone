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
// Real, original Forge 1.20.1 - same FMLCommonSetupEvent/EventBusSubscriber/SubscribeEvent shape as
// NeoForge's own <1.20.5 branch above (see NetworkAccess.java's own doc comment) - PlayPayloadContext
// itself is NOT imported here: this file is in the same package as the same-package compat shim
// (fr.lordfinn.crazyphone.network.PlayPayloadContext), so it resolves with no import at all.
//? if legacyforge {
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
//?}

//? if fabric || neoforge {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
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

import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PhotoSavedData;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.PhotoItemData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

/**
 * Client -> server: uploads a freshly-captured photo's two resolutions (thumbnail + full, both PNG,
 * produced from the same screenshot - see FabricPictureCapture) and immediately posts it as an image
 * message in the given conversation. Mirrors VoiceMessageUploadPacket's shape closely - same "receive
 * bytes, validate live membership, store, append a message" flow, just for PNG bytes ending up directly as
 * an image message instead of PCM ending up as a voice message.
 *
 * {@code photoId} is client-generated (see CrazyPhoneCaptureMode#triggerCapture), same reasoning as
 * VoiceMessageUploadPacket's own voiceId: the sender optimistically seeds its own FabricPictureCache and
 * appends the message locally the instant the shot is taken, without waiting on this packet's own round
 * trip - a server-assigned id would mean briefly showing a message with nothing to actually display.
 * PhotoSavedData#storePhoto still owns the FINAL id: this one is only used when no content-hash duplicate
 * is found (see that method's own doc comment) - re-sending a byte-identical photo still correctly reuses
 * whatever id it was already stored under, exactly as before this field existed.
 * <p>
 * {@code preferPhysicalItem} is only ever true for a STANDALONE upload from the photo editor's own Replace/
 * Create Copy when the editor was opened from a held Photo item (edits made from the phone stay in the
 * phone's gallery) - see {@link #handle}'s own call into {@code givePhysicalItemInsteadOfGallery}
 * for what it actually does (creative or a real Paper consumed turns this into a physical Photo item
 * instead of a phone gallery entry; out of paper in survival falls back to the gallery with an explanatory
 * chat message). A live capture or a PC import always passes false - those are meant to land in the phone,
 * never as a surprise item.
 */
//? if legacyforge {
/*public record CrazyPhoneUploadPicturePacket(String conversationId, UUID photoId, byte[] thumbnailPng, byte[] fullPng, boolean preferPhysicalItem) {
*///? } else {
public record CrazyPhoneUploadPicturePacket(String conversationId, UUID photoId, byte[] thumbnailPng, byte[] fullPng, boolean preferPhysicalItem) implements CustomPacketPayload {
//?}
    private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");
    // Generous but real ceilings - the client already downscales/compresses before sending (see
    // FabricPictureCapture), this is defense in depth against a modified client the same way
    // VoiceMessageUploadPacket caps PCM length. The full-image ceiling is configurable (Config#
    // photoFullMaxUploadBytes) since it's coupled to Config#photoFullMaxDimension - raising the capture
    // resolution needs this raised too, or legitimate higher-quality uploads start getting rejected.
    private static final int THUMBNAIL_MAX_BYTES = 200_000;

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneUploadPicturePacket> TYPE = new Type<>(
            Crazyphone.resource("upload_picture")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneUploadPicturePacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneUploadPicturePacket message) -> {
                        buffer.writeUtf(message.conversationId);
                        buffer.writeUUID(message.photoId);
                        buffer.writeByteArray(message.thumbnailPng);
                        buffer.writeByteArray(message.fullPng);
                        buffer.writeBoolean(message.preferPhysicalItem);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneUploadPicturePacket(
                            buffer.readUtf(),
                            buffer.readUUID(),
                            buffer.readByteArray(),
                            buffer.readByteArray(),
                            buffer.readBoolean()
                    )
            );

    @Override
    public Type<CrazyPhoneUploadPicturePacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("upload_picture");

    public CrazyPhoneUploadPicturePacket(FriendlyByteBuf buffer) {
        this(buffer.readUtf(), buffer.readUUID(), buffer.readByteArray(), buffer.readByteArray(), buffer.readBoolean());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUtf(conversationId);
        buffer.writeUUID(photoId);
        buffer.writeByteArray(thumbnailPng);
        buffer.writeByteArray(fullPng);
        buffer.writeBoolean(preferPhysicalItem);
    }

    //? if fabric || neoforge {
    @Override
    //?}
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    private static void handle(ServerPlayer player, String conversationId, UUID clientPhotoId, byte[] thumbnailPng, byte[] fullPng, boolean preferPhysicalItem) {
        if (thumbnailPng.length == 0 || thumbnailPng.length > THUMBNAIL_MAX_BYTES) {
            LOGGER.warn("Picture upload rejected: thumbnail {} bytes", thumbnailPng.length);
            return;
        }
        if (fullPng.length == 0 || fullPng.length > fr.lordfinn.crazyphone.Config.photoFullMaxUploadBytes) {
            LOGGER.warn("Picture upload rejected: full image {} bytes", fullPng.length);
            return;
        }
        Level world = player.level();
        // Empty conversationId means a standalone shot (taken via the home screen's Photo icon or the
        // punch-to-shoot shortcut, neither of which has a target conversation) - saved to the phone's own
        // photo list only, never posted as a message anywhere. A real conversationId still needs the usual
        // live-membership check.
        boolean standalone = conversationId.isEmpty();
        String senderNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        // The editor can be opened from a held Photo item, so the main hand holds that item, not a phone.
        // A physical-item result needs no phone at all: attribute it to the player's own phone if they have
        // one, otherwise leave it unattributed.
        if (senderNumber.isEmpty() && standalone && preferPhysicalItem)
            senderNumber = CrazyPhoneHelper.getOwnedPhoneNumber(world, player.getUUID());
        if (senderNumber.isEmpty() && !(standalone && preferPhysicalItem)) {
            LOGGER.warn("Picture upload rejected for {}: no phone number resolved from main hand",
                    fr.lordfinn.crazyphone.utils.GameProfileCompat.name(player.getGameProfile()));
            return;
        }
        if (!standalone && !CrazyPhoneHelper.getGroupMembers(world, conversationId).contains(senderNumber))
            return;

        int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
        UUID photoId = PhotoSavedData.get(world).storePhoto(senderNumber, conversationId, clientPhotoId, thumbnailPng, fullPng, timestampInMinutes);
        if (!standalone) {
            CrazyPhoneHelper.addImageMessage(world, conversationId, senderNumber, photoId, timestampInMinutes);
            return;
        }
        if (preferPhysicalItem)
            givePhysicalItemInsteadOfGallery(player, photoId, senderNumber, timestampInMinutes);
    }

    /** Editor-commit-only path (live request) - storePhoto above already dropped this standalone upload into
     * the sender's own phone gallery; this turns it into a real, physical Photo item instead whenever that's
     * actually possible (creative, or a real Paper consumed - mirrors {@link fr.lordfinn.crazyphone.network.CrazyPhoneGivePhotoItemPacket}'s
     * own item-creation/markPhysical steps), then removes the just-added gallery entry again so the edited
     * photo doesn't end up duplicated in both places ("mettre l'image dans l'inventaire du joueur en tant
     * qu'item plutot"). Falls back to just leaving it in the gallery (already done above) with an
     * explanatory chat message when neither applies - survival, no paper on hand. */
    private static void givePhysicalItemInsteadOfGallery(ServerPlayer player, UUID photoId, String owner, int createdMinutes) {
        boolean creative = player.getAbilities().instabuild;
        if (!creative) {
            int removed = player.getInventory().clearOrCountMatchingItems(stack -> stack.is(Items.PAPER), 1, player.getInventory());
            if (removed < 1) {
                if (owner.isEmpty()) {
                    // No phone to fall back to: the gallery entry just stored would belong to nobody.
                    PhotoSavedData.get(player.level()).deletePhotos(owner, java.util.Set.of(photoId));
                    CrazyPhoneHelper.sendClientMessage(player, Component.translatable("message.crazyphone.photo_edit_no_paper_no_phone"), true);
                } else {
                    CrazyPhoneHelper.sendClientMessage(player, Component.translatable("message.crazyphone.photo_edit_no_paper"), true);
                }
                return;
            }
        }
        ItemStack stack = new ItemStack(ModItems.CRAZY_PHONE_PHOTO.get());
        new PhotoItemData(photoId, owner, createdMinutes).writeTo(stack);
        if (!player.getInventory().add(stack))
            player.drop(stack, false);
        PhotoSavedData saved = PhotoSavedData.get(player.level());
        // Marked physical BEFORE removing the gallery reference - eraseIfOrphaned (called from within
        // deletePhotos) would otherwise see no owner left referencing this id and no physical flag yet set,
        // and wipe the bytes the item itself still points at.
        saved.markPhysical(photoId);
        saved.deletePhotos(owner, java.util.Set.of(photoId));
    }

    //? if neoforge || legacyforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhoneUploadPicturePacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player, message.conversationId, message.photoId, message.thumbnailPng, message.fullPng, message.preferPhysicalItem);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CrazyPhoneUploadPicturePacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player, message.conversationId, message.photoId, message.thumbnailPng, message.fullPng, message.preferPhysicalItem);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneUploadPicturePacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.conversationId, message.photoId, message.thumbnailPng, message.fullPng, message.preferPhysicalItem);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneUploadPicturePacket::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneUploadPicturePacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneUploadPicturePacket::new, CrazyPhoneUploadPicturePacket::handleData);
            //?}
        }
    }
    //?}


    //? if legacyforge {
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static class CrazyPhoneUploadPicturePacketLegacyForgeRegistration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(CrazyPhoneUploadPicturePacket.class, 26, CrazyPhoneUploadPicturePacket::write, CrazyPhoneUploadPicturePacket::new, CrazyPhoneUploadPicturePacket::handleData);
        }
    }
    //?}
}