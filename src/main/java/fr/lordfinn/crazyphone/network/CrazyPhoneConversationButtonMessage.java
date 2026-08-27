
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

import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.Crazyphone;

import java.util.Map;
import java.time.Instant;
import java.util.HashMap;

//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber
*///?}
//?}
public record CrazyPhoneConversationButtonMessage(int buttonID, int x, int y, int z, HashMap<String, String> textstate)
		implements CustomPacketPayload {

	//? if >=1.20.5 {
	/*public static final Type<CrazyPhoneConversationButtonMessage> TYPE = new Type<>(
			Crazyphone.resource("crazy_phone_conversation_buttons"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneConversationButtonMessage> STREAM_CODEC = StreamCodec
			.of((RegistryFriendlyByteBuf buffer, CrazyPhoneConversationButtonMessage message) -> {
				buffer.writeInt(message.buttonID);
				buffer.writeInt(message.x);
				buffer.writeInt(message.y);
				buffer.writeInt(message.z);
				writeTextState(message.textstate, buffer);
			}, (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneConversationButtonMessage(buffer.readInt(),
					buffer.readInt(), buffer.readInt(), buffer.readInt(), readTextState(buffer)));
	@Override
	public Type<CrazyPhoneConversationButtonMessage> type() {
		return TYPE;
	}
	*///? } else {
	public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "crazy_phone_conversation_buttons");

	public CrazyPhoneConversationButtonMessage(FriendlyByteBuf buffer) {
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

	//? if neoforge {
	//? if >=1.20.5 {
	/*public static void handleData(final CrazyPhoneConversationButtonMessage message, final IPayloadContext context) {
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
	public static void handleData(final CrazyPhoneConversationButtonMessage message, final PlayPayloadContext context) {
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
	//?}
	//? if fabric && >=1.20.5 {
	/*public static void handleDataFabric(CrazyPhoneConversationButtonMessage message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
		handleButtonAction(context.player(), message.buttonID, message.x, message.y, message.z, message.textstate);
	}
	*///?}

	public static void handleButtonAction(Player entity, int buttonID, int x, int y, int z,
			HashMap<String, String> textstate) {
		Level world = entity.level();
		// security measure to prevent arbitrary chunk generation
		if (!world.hasChunkAt(new BlockPos(x, y, z)))
			return;

		if (buttonID == 0) {
			String message = textstate.containsKey("textin:message") ? (String) textstate.get("textin:message") : "";
			if (message.isEmpty())
				return;
			int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
			String conversationId = textstate.containsKey("conversationId") ? (String) textstate.get("conversationId")
					: "";
			String senderNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null);
			// handleButtonAction is invoked both from the authoritative SERVERBOUND packet handler above
			// AND directly by the client screen for immediate UI feedback (see
			// CrazyPhoneConversationScreen's send-button handler) - so anything here that mutates real data
			// must be server-only. ConversationSavedData intentionally refuses client-side access (it's
			// never held client-side in full - see that class), so calling addMessage unconditionally
			// crashed the client the moment this ran locally; guarding it also avoids double-adding the
			// message once the server processes the same packet.
			//
			// addMessage appends to the per-conversation ConversationSavedData (bounded, never broadcast
			// wholesale) and notifies the other participant with a targeted packet - it replaces the old
			// CrazythingsModVariables.MapVariables ListTag manipulation. The old code additionally called
			// MapVariables.get(world).syncData(world) here, which broadcast the ENTIRE
			// phones/contacts/message-history blob to every online player after every single message sent;
			// that unbounded full broadcast was the root cause of the server crashing on login as
			// conversation history grew, so it is intentionally NOT reintroduced here.
			if (!world.isClientSide()) {
				// senderNumber comes from the phone actually held by the connected player, but
				// conversationId is client-supplied - without checking that senderNumber is really a
				// current participant (checked live, not just the numbers baked into the id, so an
				// excluded group member loses write access immediately) a modified client could inject a
				// message into a conversation it has no business being in.
				if (senderNumber.isEmpty() || !CrazyPhoneHelper.getGroupMembers(world, conversationId).contains(senderNumber))
					return;
				CrazyPhoneHelper.addMessage(world, conversationId, senderNumber, message, timestampInMinutes);
			} else {
				//? if >=1.20.5 {
				/*entity.playNotifySound(SoundEvents.WIND_CHARGE_THROW, SoundSource.NEUTRAL, 1, 1);
				*///? } else {
				entity.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1, 1);
				//?}
			}
			// Deliberately NOT reopening the conversation menu here (the old code called
			// ScreenMenuUtils.openPhoneConversationMenu after every send to refresh the sender's own view).
			// player.openMenu() always closes the current container first, which briefly returns the client
			// to "no menu" and back, causing Minecraft's mouse grab/release cycle to warp the cursor to the
			// center of the screen on every single message sent. The sender's own message is instead shown
			// via an optimistic local append in CrazyPhoneConversationScreen's send handler - no server
			// round trip, no menu reopen, no cursor jump.
		} else if (buttonID == 2) {
			String conversationId = textstate.containsKey("conversationId") ? textstate.get("conversationId") : "";
			if (conversationId.isEmpty())
				return;
			ScreenMenuUtils.openGroupSettingsMenu(entity, InteractionHand.MAIN_HAND, conversationId);
		}
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

	//? if neoforge {
	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		//? if >=1.20.5 {
		/*Crazyphone.addNetworkMessage(CrazyPhoneConversationButtonMessage.TYPE,
				CrazyPhoneConversationButtonMessage.STREAM_CODEC, CrazyPhoneConversationButtonMessage::handleData);
		*///? } else {
		Crazyphone.addNetworkMessage(CrazyPhoneConversationButtonMessage.ID,
				CrazyPhoneConversationButtonMessage::new, CrazyPhoneConversationButtonMessage::handleData);
		//?}
	}
	//?}
	//? if fabric && >=1.20.5 {
	/*public static void registerFabricType() {
		fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
	}

	public static void registerFabricServerReceiver() {
		fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneConversationButtonMessage::handleDataFabric);
	}
	*///?}
}
