package fr.lordfinn.crazyphone.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

    public static final Type<CrazyPhoneTakePhotoRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "crazy_phone_take_photo_request")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneTakePhotoRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new CrazyPhoneTakePhotoRequestPacket());

    @Override
    public Type<CrazyPhoneTakePhotoRequestPacket> type() {
        return TYPE;
    }

    public static void handleData(final CrazyPhoneTakePhotoRequestPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (player.getItemInHand(InteractionHand.MAIN_HAND).getItem() != ModItems.CRAZY_PHONE.get())
                return;
            CrazyPhoneTakePhotoProcedure.execute(player.level(), player);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneTakePhotoRequestPacket::handleData);
        }
    }
}
