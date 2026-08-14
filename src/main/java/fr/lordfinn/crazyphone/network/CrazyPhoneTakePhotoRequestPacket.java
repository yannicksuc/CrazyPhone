package fr.lordfinn.crazyphone.network;

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

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneTakePhotoProcedure;

/**
 * Client -> server: "take a photo now" - sent when the player left-clicks empty space (no block or entity
 * under the crosshair) while holding the CrazyPhone. NeoForge's PlayerInteractEvent.LeftClickBlock and
 * AttackEntityEvent both fire server-side already (see CrazyPhoneLeftClickInterceptor), so this packet only
 * exists to cover the one case NeoForge itself documents as client-only and uncancellable:
 * PlayerInteractEvent.LeftClickEmpty. No payload needed beyond the requesting player themselves. Re-checks
 * the held item server-side rather than trusting the client, same as every other button-triggered action in
 * this mod.
 */
public record CrazyPhoneTakePhotoRequestPacket() implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneTakePhotoRequestPacket> TYPE = new Type<>(
            Crazyphone.resource("crazy_phone_take_photo_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneTakePhotoRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new CrazyPhoneTakePhotoRequestPacket());

    @Override
    public Type<CrazyPhoneTakePhotoRequestPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final ResourceLocation ID = Crazyphone.resource("crazy_phone_take_photo_request");

    public CrazyPhoneTakePhotoRequestPacket(FriendlyByteBuf buffer) {
        this();
    }

    public void write(FriendlyByteBuf buffer) {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
    //?}

    private static void handle(ServerPlayer player) {
        if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() != ModItems.CRAZY_PHONE.get())
            return;
        CrazyPhoneTakePhotoProcedure.execute(player.level(), player);
    }

    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhoneTakePhotoRequestPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CrazyPhoneTakePhotoRequestPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}

    //? if <1.20.5 {
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    //?} else {
    /*@EventBusSubscriber
    *///?}
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            //? if >=1.20.5 {
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneTakePhotoRequestPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneTakePhotoRequestPacket::new, CrazyPhoneTakePhotoRequestPacket::handleData);
            //?}
        }
    }
}
