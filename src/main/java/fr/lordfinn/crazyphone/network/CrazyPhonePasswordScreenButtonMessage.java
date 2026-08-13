
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

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
//? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePasswordScreenMenu;
import fr.lordfinn.crazyphone.procedures.ResetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneInitialFormValidationButtonClickProcedure;
import fr.lordfinn.crazyphone.Crazyphone;

import java.util.Map;
import java.util.HashMap;

@EventBusSubscriber
public record CrazyPhonePasswordScreenButtonMessage(int buttonID, int x, int y, int z, HashMap<String, String> textstate) implements CustomPacketPayload {

	//? if >=1.20.5 {
	public static final Type<CrazyPhonePasswordScreenButtonMessage> TYPE = new Type<>(Crazyphone.resource("crazy_phone_password_screen_buttons"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhonePasswordScreenButtonMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, CrazyPhonePasswordScreenButtonMessage message) -> {
		buffer.writeInt(message.buttonID);
		buffer.writeInt(message.x);
		buffer.writeInt(message.y);
		buffer.writeInt(message.z);
		writeTextState(message.textstate, buffer);
	}, (RegistryFriendlyByteBuf buffer) -> new CrazyPhonePasswordScreenButtonMessage(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), readTextState(buffer)));
	@Override
	public Type<CrazyPhonePasswordScreenButtonMessage> type() {
		return TYPE;
	}
	//? } else {
	/*public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "crazy_phone_password_screen_buttons");

	public CrazyPhonePasswordScreenButtonMessage(FriendlyByteBuf buffer) {
		this(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), readTextState(buffer));
	}

	public void write(FriendlyByteBuf buffer) {
		buffer.writeInt(buttonID);
		buffer.writeInt(x);
		buffer.writeInt(y);
		buffer.writeInt(z);
		writeTextState(textstate, buffer);
	}

	@Override
	public ResourceLocation id() {
		return ID;
	}
	*///?}

	//? if >=1.20.5 {
	public static void handleData(final CrazyPhonePasswordScreenButtonMessage message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> {
				Player entity = context.player();
				int buttonID = message.buttonID;
				int x = message.x;
				int y = message.y;
				int z = message.z;
				HashMap<String, String> textstate = message.textstate;
				handleButtonAction(entity, buttonID, x, y, z, textstate);
			}).exceptionally(e -> {
				context.connection().disconnect(Component.literal(e.getMessage()));
				return null;
			});
		}
	}
	//? } else {
	/*public static void handleData(final CrazyPhonePasswordScreenButtonMessage message, final PlayPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.workHandler().submitAsync(() -> {
				Player entity = context.player().orElse(null);
				int buttonID = message.buttonID;
				int x = message.x;
				int y = message.y;
				int z = message.z;
				HashMap<String, String> textstate = message.textstate;
				handleButtonAction(entity, buttonID, x, y, z, textstate);
			}).exceptionally(e -> {
				context.packetHandler().disconnect(Component.literal(e.getMessage()));
				return null;
			});
		}
	}
	*///?}

	public static void handleButtonAction(Player entity, int buttonID, int x, int y, int z, HashMap<String, String> textstate) {
		Level world = entity.level();
		HashMap guistate = CrazyPhonePasswordScreenMenu.guistate;
		// connect EditBox and CheckBox to guistate
		for (Map.Entry<String, String> entry : textstate.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			guistate.put(key, value);
		}
		// security measure to prevent arbitrary chunk generation
		if (!world.hasChunkAt(new BlockPos(x, y, z)))
			return;
		if (buttonID == 0) {

			CrazyPhoneInitialFormValidationButtonClickProcedure.execute(world, x, y, z, entity, textstate);
		}
		if (buttonID == 1) {

			ResetCrazyPhoneNumberFromMainHandProcedure.execute(entity);
		}
	}

	//? if >=1.20.5 {
	private static void writeTextState(HashMap<String, String> map, RegistryFriendlyByteBuf buffer) {
	//? } else {
	/*private static void writeTextState(HashMap<String, String> map, FriendlyByteBuf buffer) {
	*///?}
		buffer.writeInt(map.size());
		for (Map.Entry<String, String> entry : map.entrySet()) {
			buffer.writeUtf(entry.getKey());
			buffer.writeUtf(entry.getValue());
		}
	}

	//? if >=1.20.5 {
	private static HashMap<String, String> readTextState(RegistryFriendlyByteBuf buffer) {
	//? } else {
	/*private static HashMap<String, String> readTextState(FriendlyByteBuf buffer) {
	*///?}
		int size = buffer.readInt();
		HashMap<String, String> map = new HashMap<>();
		for (int i = 0; i < size; i++) {
			String key = buffer.readUtf();
			String value = buffer.readUtf();
			map.put(key, value);
		}
		return map;
	}

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		//? if >=1.20.5 {
		Crazyphone.addNetworkMessage(CrazyPhonePasswordScreenButtonMessage.TYPE, CrazyPhonePasswordScreenButtonMessage.STREAM_CODEC, CrazyPhonePasswordScreenButtonMessage::handleData);
		//? } else {
		/*Crazyphone.addNetworkMessage(CrazyPhonePasswordScreenButtonMessage.ID, CrazyPhonePasswordScreenButtonMessage::new, CrazyPhonePasswordScreenButtonMessage::handleData);
		*///?}
	}
}
