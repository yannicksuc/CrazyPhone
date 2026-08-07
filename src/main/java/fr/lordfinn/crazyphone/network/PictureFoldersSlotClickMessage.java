package fr.lordfinn.crazyphone.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePictureFoldersScreenMenu;

@EventBusSubscriber
public record PictureFoldersSlotClickMessage(int slotIndex) implements CustomPacketPayload {

	public static final Type<PictureFoldersSlotClickMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "picture_folders_slot_click"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PictureFoldersSlotClickMessage> STREAM_CODEC = StreamCodec.of(
		(buffer, message) -> buffer.writeInt(message.slotIndex),
		buffer -> new PictureFoldersSlotClickMessage(buffer.readInt())
	);

	@Override
	public Type<PictureFoldersSlotClickMessage> type() {
		return TYPE;
	}

	public static void handleData(final PictureFoldersSlotClickMessage message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				Player entity = context.player();
				if (entity.containerMenu instanceof CrazyPhonePictureFoldersScreenMenu menu) {
					menu.handleSlotClick(message.slotIndex());
				}
			}).exceptionally(e -> {
				context.connection().disconnect(net.minecraft.network.chat.Component.literal("Error: " + e.getMessage()));
				return null;
			});
		}
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, PictureFoldersSlotClickMessage::handleData);
	}
}
