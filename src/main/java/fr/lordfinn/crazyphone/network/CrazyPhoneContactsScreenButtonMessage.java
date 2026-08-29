
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactInfoScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactsScreenMenu;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneRemoveContactFromPhoneProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.Crazyphone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber
*///?}
//?}
public record CrazyPhoneContactsScreenButtonMessage(int buttonID, int x, int y, int z, HashMap<String, String> textstate) implements CustomPacketPayload {

	//? if >=1.20.5 {
	/*public static final Type<CrazyPhoneContactsScreenButtonMessage> TYPE = new Type<>(Crazyphone.resource("crazy_phone_contacts_screen_buttons"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneContactsScreenButtonMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, CrazyPhoneContactsScreenButtonMessage message) -> {
		buffer.writeInt(message.buttonID);
		buffer.writeInt(message.x);
		buffer.writeInt(message.y);
		buffer.writeInt(message.z);
		writeTextState(message.textstate, buffer);
	}, (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneContactsScreenButtonMessage(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), readTextState(buffer)));
	@Override
	public Type<CrazyPhoneContactsScreenButtonMessage> type() {
		return TYPE;
	}
	*///? } else {
	public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "crazy_phone_contacts_screen_buttons");

	public CrazyPhoneContactsScreenButtonMessage(FriendlyByteBuf buffer) {
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
	/*public static void handleData(final CrazyPhoneContactsScreenButtonMessage message, final IPayloadContext context) {
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
	public static void handleData(final CrazyPhoneContactsScreenButtonMessage message, final PlayPayloadContext context) {
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
	/*public static void handleDataFabric(CrazyPhoneContactsScreenButtonMessage message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
		handleButtonAction(context.player(), message.buttonID, message.x, message.y, message.z, message.textstate);
	}
	*///?}

	public static void handleButtonAction(Player entity, int buttonID, int x, int y, int z, HashMap<String, String> textstate) {
		Level world = entity.level();
		HashMap guistate = CrazyPhoneContactsScreenMenu.guistate;
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
			if (world.isClientSide()) {
			SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, Crazyphone.parseId("minecraft:ui.button.click"));
			if (sound != null) {
				entity.playNotifySound(sound, SoundSource.PLAYERS, 0.2f, 1.0f);
			}
		}
			ScreenMenuUtils.openPhoneCustomMenu(entity, InteractionHand.MAIN_HAND, CrazyPhoneContactInfoScreenMenu.class);
		}
		else if (buttonID == 1) {
			if (world.isClientSide()) {
				SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, Crazyphone.parseId("minecraft:ui.button.click"));
				if (sound != null) {
					entity.playNotifySound(sound, SoundSource.PLAYERS, 0.2f, 1.0f);
				}
			}
			String conversationNumber = CrazyPhoneHelper.getConversationNumber(textstate.get("contactNumber"), entity);
			ScreenMenuUtils.openPhoneConversationMenu(entity, InteractionHand.MAIN_HAND, conversationNumber);
		}
		else if (buttonID == 2) { // Create a group conversation with the selected contacts
			if (world.isClientSide()) {
				SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, Crazyphone.parseId("minecraft:ui.button.click"));
				if (sound != null) {
					entity.playNotifySound(sound, SoundSource.PLAYERS, 0.2f, 1.0f);
				}
			}
			List<String> otherNumbers = parseSelectedNumbers(textstate.get("selectedNumbers"));
			if (otherNumbers.size() < 2)
				return; // a "group" needs at least 2 other people - fewer is just the regular 1:1 conversation
			String creatorNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null);
			List<String> members = new ArrayList<>(otherNumbers);
			members.add(creatorNumber);
			// A fresh random id, NOT derived from the members' numbers like a 1:1 conversation's id is -
			// otherwise creating a second group with the exact same people would collide onto the first
			// group's conversation instead of starting an independent one.
			String conversationId = CrazyPhoneHelper.generateGroupConversationId();
			// Register the group for every participant right away (not just the creator, and not only
			// once someone happens to send the first message) so it shows up in everyone's Contacts screen.
			// The creator becomes the initial admin.
			CrazyPhoneHelper.createGroup(world, conversationId, members, creatorNumber);

			var creatorContact = CrazyPhoneHelper.getContact(world, creatorNumber);
			String creatorName = creatorContact != null ? creatorContact.getName() : creatorNumber;
			for (String otherNumber : otherNumbers)
				CrazyPhoneHelper.notifyGroupAddition(world, otherNumber, "Groupe", creatorName);

			ScreenMenuUtils.openPhoneConversationMenu(entity, InteractionHand.MAIN_HAND, conversationId);
		}
		else if (buttonID == 3) { // Remove the selected contacts
			if (world.isClientSide()) {
				SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, Crazyphone.parseId("minecraft:ui.button.click"));
				if (sound != null) {
					entity.playNotifySound(sound, SoundSource.PLAYERS, 0.2f, 1.0f);
				}
			}
			List<String> selectedNumbers = parseSelectedNumbers(textstate.get("selectedNumbers"));
			if (selectedNumbers.isEmpty())
				return;
			String owner = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null);
			if (owner.isEmpty())
				return;
			for (String number : selectedNumbers) {
				CrazyPhoneRemoveContactFromPhoneProcedure.execute(world, number, owner);
			}
			ScreenMenuUtils.openPhoneContactsMenu(entity, InteractionHand.MAIN_HAND);
		}
		else if (buttonID == 5) { // Toggle favorite status for the selected contacts
			if (world.isClientSide()) {
				SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, Crazyphone.parseId("minecraft:ui.button.click"));
				if (sound != null) {
					entity.playNotifySound(sound, SoundSource.PLAYERS, 0.2f, 1.0f);
				}
			}
			List<String> selectedNumbers = parseSelectedNumbers(textstate.get("selectedNumbers"));
			if (selectedNumbers.isEmpty())
				return;
			String owner = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null);
			if (owner.isEmpty())
				return;
			for (String number : selectedNumbers) {
				CrazyPhoneHelper.toggleFavorite(world, owner, number);
			}
			ScreenMenuUtils.openPhoneContactsMenu(entity, InteractionHand.MAIN_HAND);
		}
		else if (buttonID == 4) { // Open an already-known group conversation directly
			if (world.isClientSide()) {
				SoundEvent sound = fr.lordfinn.crazyphone.utils.RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, Crazyphone.parseId("minecraft:ui.button.click"));
				if (sound != null) {
					entity.playNotifySound(sound, SoundSource.PLAYERS, 0.2f, 1.0f);
				}
			}
			String conversationId = textstate.get("conversationId");
			if (conversationId == null || conversationId.isEmpty())
				return;
			ScreenMenuUtils.openPhoneConversationMenu(entity, InteractionHand.MAIN_HAND, conversationId);
		}
	}

	private static List<String> parseSelectedNumbers(String csv) {
		if (csv == null || csv.isEmpty())
			return List.of();
		return Arrays.asList(csv.split(","));
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
		/*Crazyphone.addNetworkMessage(CrazyPhoneContactsScreenButtonMessage.TYPE, CrazyPhoneContactsScreenButtonMessage.STREAM_CODEC, CrazyPhoneContactsScreenButtonMessage::handleData);
		*///? } else {
		Crazyphone.addNetworkMessage(CrazyPhoneContactsScreenButtonMessage.ID, CrazyPhoneContactsScreenButtonMessage::new, CrazyPhoneContactsScreenButtonMessage::handleData);
		//?}
	}
	//?}
	//? if fabric && >=1.20.5 {
	/*public static void registerFabricType() {
		fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
	}

	public static void registerFabricServerReceiver() {
		fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneContactsScreenButtonMessage::handleDataFabric);
	}
	*///?}
}
