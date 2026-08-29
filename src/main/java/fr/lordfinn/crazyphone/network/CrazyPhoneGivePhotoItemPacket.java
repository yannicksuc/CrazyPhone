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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PhotoSavedData;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.PhotoItemData;

import java.util.UUID;

/** Client -> server: "Save to Inventory" from the photo viewer - gives the player a physical Photo item pointing at this photoId. */
public record CrazyPhoneGivePhotoItemPacket(UUID photoId) implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneGivePhotoItemPacket> TYPE = new Type<>(
            Crazyphone.resource("give_photo_item")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneGivePhotoItemPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneGivePhotoItemPacket message) -> buffer.writeUUID(message.photoId),
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneGivePhotoItemPacket(buffer.readUUID())
            );

    @Override
    public Type<CrazyPhoneGivePhotoItemPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("give_photo_item");

    public CrazyPhoneGivePhotoItemPacket(FriendlyByteBuf buffer) {
        this(buffer.readUUID());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeUUID(photoId);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    // Package-private (not private) - reused by CrazyPhoneMyPhotosActionMessage's TAKE action to give
    // several photos at once with the exact same per-photo authorization/give logic.
    static void handle(ServerPlayer player, UUID photoId) {
        Level world = player.level();
        PhotoSavedData.PhotoEntry entry = PhotoSavedData.get(world).getPhoto(photoId);
        if (entry == null)
            return;

        String requesterNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        boolean authorized = !requesterNumber.isEmpty() && (requesterNumber.equals(entry.owner())
                || CrazyPhoneHelper.getGroupMembers(world, entry.conversationId()).contains(requesterNumber));
        if (!authorized)
            return;

        ItemStack stack = new ItemStack(ModItems.CRAZY_PHONE_PHOTO.get());
        new PhotoItemData(photoId, entry.owner(), entry.createdMinutes()).writeTo(stack);
        if (!player.getInventory().add(stack))
            player.drop(stack, false);
    }

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhoneGivePhotoItemPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player, message.photoId);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CrazyPhoneGivePhotoItemPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player, message.photoId);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneGivePhotoItemPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.photoId);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneGivePhotoItemPacket::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneGivePhotoItemPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneGivePhotoItemPacket::new, CrazyPhoneGivePhotoItemPacket::handleData);
            //?}
        }
    }
    //?}
}
