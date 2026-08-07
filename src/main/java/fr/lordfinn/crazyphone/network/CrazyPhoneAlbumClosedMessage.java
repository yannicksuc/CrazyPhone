package fr.lordfinn.crazyphone.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

@EventBusSubscriber
public record CrazyPhoneAlbumClosedMessage() implements CustomPacketPayload {
	public static final Type<CrazyPhoneAlbumClosedMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "crazy_phone_album_closed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneAlbumClosedMessage> STREAM_CODEC =
        StreamCodec.unit(new CrazyPhoneAlbumClosedMessage());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CrazyPhoneAlbumClosedMessage message, IPayloadContext context) {
        if (context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(() -> {
                Player player = context.player();
                ScreenMenuUtils.openCurrentCrazyPhoneMenu(player, InteractionHand.MAIN_HAND);
            });
        }
    }

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneAlbumClosedMessage::handle);
	}
}
