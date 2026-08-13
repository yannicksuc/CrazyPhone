package fr.lordfinn.crazyphone.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
//? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
import net.neoforged.fml.common.EventBusSubscriber;
//? } else {
/*import net.neoforged.fml.common.Mod.EventBusSubscriber;
*///?}
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if >=1.20.5 {
import net.neoforged.neoforge.network.handling.IPayloadContext;
//? } else {
/*import net.neoforged.neoforge.network.handling.PlayPayloadContext;
*///?}

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;

@EventBusSubscriber
public record CrazyPhoneAlbumClosedMessage() implements CustomPacketPayload {
	//? if >=1.20.5 {
	public static final Type<CrazyPhoneAlbumClosedMessage> TYPE = new Type<>(Crazyphone.resource("crazy_phone_album_closed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneAlbumClosedMessage> STREAM_CODEC =
        StreamCodec.unit(new CrazyPhoneAlbumClosedMessage());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //? } else {
    /*public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "crazy_phone_album_closed");

    public CrazyPhoneAlbumClosedMessage(FriendlyByteBuf buffer) {
        this();
    }

    public void write(FriendlyByteBuf buffer) {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
    *///?}

    //? if >=1.20.5 {
    public static void handle(CrazyPhoneAlbumClosedMessage message, IPayloadContext context) {
        if (context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(() -> {
                Player player = context.player();
                ScreenMenuUtils.openCurrentCrazyPhoneMenu(player, InteractionHand.MAIN_HAND);
            });
        }
    }
    //? } else {
    /*public static void handle(CrazyPhoneAlbumClosedMessage message, PlayPayloadContext context) {
        if (context.flow() == PacketFlow.SERVERBOUND) {
            context.workHandler().submitAsync(() -> {
                Player player = context.player().orElse(null);
                ScreenMenuUtils.openCurrentCrazyPhoneMenu(player, InteractionHand.MAIN_HAND);
            });
        }
    }
    *///?}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		//? if >=1.20.5 {
		Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneAlbumClosedMessage::handle);
		//? } else {
		/*Crazyphone.addNetworkMessage(ID, CrazyPhoneAlbumClosedMessage::new, CrazyPhoneAlbumClosedMessage::handle);
		*///?}
	}
}
