package fr.lordfinn.crazyphone.network;

//? if >=1.20.5 {
import net.neoforged.neoforge.network.handling.IPayloadContext;
//? } else {
/*import net.neoforged.neoforge.network.handling.PlayPayloadContext;
*///?}
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if >=1.20.5 {
import net.neoforged.fml.common.EventBusSubscriber;
//? } else {
/*import net.neoforged.fml.common.Mod.EventBusSubscriber;
*///?}
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
//? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePictureFoldersScreenMenu;

@EventBusSubscriber
public record PictureFoldersSlotClickMessage(int slotIndex) implements CustomPacketPayload {

	//? if >=1.20.5 {
	public static final Type<PictureFoldersSlotClickMessage> TYPE = new Type<>(Crazyphone.resource("picture_folders_slot_click"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PictureFoldersSlotClickMessage> STREAM_CODEC = StreamCodec.of(
		(buffer, message) -> buffer.writeInt(message.slotIndex),
		buffer -> new PictureFoldersSlotClickMessage(buffer.readInt())
	);

	@Override
	public Type<PictureFoldersSlotClickMessage> type() {
		return TYPE;
	}
	//? } else {
	/*public static final ResourceLocation ID = Crazyphone.resource("picture_folders_slot_click");

	public PictureFoldersSlotClickMessage(FriendlyByteBuf buffer) {
		this(buffer.readInt());
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeInt(slotIndex);
	}

	@Override
	public ResourceLocation id() {
		return ID;
	}
	*///?}

	private static void handle(Player entity, int slotIndex) {
		if (entity.containerMenu instanceof CrazyPhonePictureFoldersScreenMenu menu) {
			menu.handleSlotClick(slotIndex);
		}
	}

	//? if >=1.20.5 {
	public static void handleData(final PictureFoldersSlotClickMessage message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> handle(context.player(), message.slotIndex())).exceptionally(e -> {
				context.connection().disconnect(net.minecraft.network.chat.Component.literal("Error: " + e.getMessage()));
				return null;
			});
		}
	}
	//? } else {
	/*public static void handleData(final PictureFoldersSlotClickMessage message, final PlayPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.workHandler().submitAsync(() -> handle(context.player().orElse(null), message.slotIndex())).exceptionally(e -> {
				context.packetHandler().disconnect(net.minecraft.network.chat.Component.literal("Error: " + e.getMessage()));
				return null;
			});
		}
	}
	*///?}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		//? if >=1.20.5 {
		Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, PictureFoldersSlotClickMessage::handleData);
		//? } else {
		/*Crazyphone.addNetworkMessage(ID, PictureFoldersSlotClickMessage::new, PictureFoldersSlotClickMessage::handleData);
		*///?}
	}
}
