
package fr.lordfinn.crazyphone.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

@EventBusSubscriber
public record CrazyPhoneContactsScreenButtonMessage(int buttonID, int x, int y, int z, HashMap<String, String> textstate) implements CustomPacketPayload {

	public static final Type<CrazyPhoneContactsScreenButtonMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "crazy_phone_contacts_screen_buttons"));
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

	public static void handleData(final CrazyPhoneContactsScreenButtonMessage message, final IPayloadContext context) {
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
			SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("minecraft:ui.button.click"));
			if (sound != null) {
				entity.playNotifySound(sound, SoundSource.PLAYERS, 0.2f, 1.0f);
			}
		}
			ScreenMenuUtils.openPhoneCustomMenu(entity, InteractionHand.MAIN_HAND, CrazyPhoneContactInfoScreenMenu.class);
		}
		else if (buttonID == 1) {
			if (world.isClientSide()) {
				SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("minecraft:ui.button.click"));
				if (sound != null) {
					entity.playNotifySound(sound, SoundSource.PLAYERS, 0.2f, 1.0f);
				}
			}
			String conversationNumber = CrazyPhoneHelper.getConversationNumber(textstate.get("contactNumber"), entity);
			ScreenMenuUtils.openPhoneConversationMenu(entity, InteractionHand.MAIN_HAND, conversationNumber);
		}
		else if (buttonID == 2) { // Create a group conversation with the selected contacts
			if (world.isClientSide()) {
				SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("minecraft:ui.button.click"));
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
			ScreenMenuUtils.openPhoneConversationMenu(entity, InteractionHand.MAIN_HAND, conversationId);
		}
		else if (buttonID == 3) { // Remove the selected contacts
			if (world.isClientSide()) {
				SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("minecraft:ui.button.click"));
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
				SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("minecraft:ui.button.click"));
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
				SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("minecraft:ui.button.click"));
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

	private static void writeTextState(HashMap<String, String> map, RegistryFriendlyByteBuf buffer) {
		buffer.writeInt(map.size());
		for (Map.Entry<String, String> entry : map.entrySet()) {
			buffer.writeUtf(entry.getKey());
			buffer.writeUtf(entry.getValue());
		}
	}

	private static HashMap<String, String> readTextState(RegistryFriendlyByteBuf buffer) {
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
		Crazyphone.addNetworkMessage(CrazyPhoneContactsScreenButtonMessage.TYPE, CrazyPhoneContactsScreenButtonMessage.STREAM_CODEC, CrazyPhoneContactsScreenButtonMessage::handleData);
	}
}
