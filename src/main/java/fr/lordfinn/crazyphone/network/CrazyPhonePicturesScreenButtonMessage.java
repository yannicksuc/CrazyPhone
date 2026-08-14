
package fr.lordfinn.crazyphone.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import fr.lordfinn.crazyphone.data.PlayerPhoneState;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePicturesScreenMenu;
import net.minecraft.core.BlockPos;
//? if >=1.20.5 {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
*///? }
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if >=1.20.5 {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
*///? } else {
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
//?}

//? if <1.20.5 {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber
*///?}
public record CrazyPhonePicturesScreenButtonMessage(int buttonID, int x, int y, int z, HashMap<String, String> textstate) implements CustomPacketPayload {

	//? if >=1.20.5 {
	/*public static final Type<CrazyPhonePicturesScreenButtonMessage> TYPE = new Type<>(Crazyphone.resource("crazy_phone_pictures_screen_buttons"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhonePicturesScreenButtonMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, CrazyPhonePicturesScreenButtonMessage message) -> {
		buffer.writeInt(message.buttonID);
		buffer.writeInt(message.x);
		buffer.writeInt(message.y);
		buffer.writeInt(message.z);
		writeTextState(message.textstate, buffer);
	}, (RegistryFriendlyByteBuf buffer) -> new CrazyPhonePicturesScreenButtonMessage(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), readTextState(buffer)));
	@Override
	public Type<CrazyPhonePicturesScreenButtonMessage> type() {
		return TYPE;
	}
	*///? } else {
	public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "crazy_phone_pictures_screen_buttons");

	public CrazyPhonePicturesScreenButtonMessage(FriendlyByteBuf buffer) {
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
	//?}

	//? if >=1.20.5 {
	/*public static void handleData(final CrazyPhonePicturesScreenButtonMessage message, final IPayloadContext context) {
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
	*///? } else {
	public static void handleData(final CrazyPhonePicturesScreenButtonMessage message, final PlayPayloadContext context) {
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
	//?}

	public static void handleButtonAction(Player entity, int buttonID, int x, int y, int z, HashMap<String, String> textstate) {
		Level world = entity.level();
		HashMap guistate = CrazyPhonePicturesScreenMenu.guistate;
		// connect EditBox and CheckBox to guistate
		for (Map.Entry<String, String> entry : textstate.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			guistate.put(key, value);
		}
		// security measure to prevent arbitrary chunk generation
		if (!world.hasChunkAt(new BlockPos(x, y, z)))
			return;
		String albumIdStr = textstate.get("albumIndex");
		String selected = textstate.get("selectedSlots");
		int albumId;
		try {
			albumId = Integer.parseInt(albumIdStr);
		} catch (NumberFormatException e) {
			return;
		}
		Set<Integer> selectedSlots = parseSlotList(selected);
		if (buttonID == 0) {
			CrazyPhoneHelper.deleteSelectedAlbumSlotsFromHeldPhone(entity, world, selectedSlots, albumId);
		} else if (buttonID == 1) {
			CrazyPhoneHelper.takeSelectedAlbumSlotsFromHeldPhone(entity, world, selectedSlots, albumId);
		} else if (buttonID == 2) {
			if (entity instanceof ServerPlayer serverPlayer && !FeatureFlag.IMAGES.isEnabledFor(serverPlayer))
				return;
			PlayerPhoneState playerData = entity.getData(PhoneAttachmentTypes.PLAYER_PHONE_STATE);
			List<String> screenHistory = ScreenMenuUtils.getScreenHistory(playerData.crazyPhoneScreenHistory);
			if (screenHistory.size() >= 3) {
				String potentialConversationPage = screenHistory.get(screenHistory.size() - 3);
				if (potentialConversationPage.contains("crazy_phone_conversation")) {
					String conversationId = ScreenMenuUtils.parseScreenDataFromTag(potentialConversationPage);
					if (conversationId == null || conversationId.isEmpty())
						return;
					CrazyPhoneHelper.sendSelectedAlbumSlotsFromHeldPhone(entity, world, selectedSlots, albumId, conversationId);
					ScreenMenuUtils.openPhoneConversationMenu(entity, InteractionHand.MAIN_HAND, conversationId);
				}
			}
		}
	}

	private static Set<Integer>  parseSlotList(String csv) {
		Set<Integer>  slots = new HashSet<>();
	if (csv == null || csv.isEmpty()) return slots;

	String[] parts = csv.split(",");
	for (String part : parts) {
		try {
			slots.add(Integer.parseInt(part.trim()));
		} catch (NumberFormatException ignored) {
			// Optionally log invalid entry
		}
	}
	return slots;
}

	//? if >=1.20.5 {
	/*private static void writeTextState(HashMap<String, String> map, RegistryFriendlyByteBuf buffer) {
	*///? } else {
	private static void writeTextState(HashMap<String, String> map, FriendlyByteBuf buffer) {
	//?}
		buffer.writeInt(map.size());
		for (Map.Entry<String, String> entry : map.entrySet()) {
			buffer.writeUtf(entry.getKey());
			buffer.writeUtf(entry.getValue());
		}
	}

	//? if >=1.20.5 {
	/*private static HashMap<String, String> readTextState(RegistryFriendlyByteBuf buffer) {
	*///? } else {
	private static HashMap<String, String> readTextState(FriendlyByteBuf buffer) {
	//?}
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
		/*Crazyphone.addNetworkMessage(CrazyPhonePicturesScreenButtonMessage.TYPE, CrazyPhonePicturesScreenButtonMessage.STREAM_CODEC, CrazyPhonePicturesScreenButtonMessage::handleData);
		*///? } else {
		Crazyphone.addNetworkMessage(CrazyPhonePicturesScreenButtonMessage.ID, CrazyPhonePicturesScreenButtonMessage::new, CrazyPhonePicturesScreenButtonMessage::handleData);
		//?}
	}
}
