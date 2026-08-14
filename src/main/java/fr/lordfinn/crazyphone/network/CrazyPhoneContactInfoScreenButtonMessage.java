
package fr.lordfinn.crazyphone.network;

import java.util.HashMap;
import java.util.Map;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneAddContactToPhoneProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactInfoScreenMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
//? if >=1.20.5 {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.network.PacketDistributor;
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
public record CrazyPhoneContactInfoScreenButtonMessage(int buttonID, int x, int y, int z, HashMap<String, String> textstate) implements CustomPacketPayload {

	//? if >=1.20.5 {
	/*public static final Type<CrazyPhoneContactInfoScreenButtonMessage> TYPE = new Type<>(Crazyphone.resource("crazy_phone_contact_info_screen_buttons"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneContactInfoScreenButtonMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, CrazyPhoneContactInfoScreenButtonMessage message) -> {
		buffer.writeInt(message.buttonID);
		buffer.writeInt(message.x);
		buffer.writeInt(message.y);
		buffer.writeInt(message.z);
		writeTextState(message.textstate, buffer);
	}, (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneContactInfoScreenButtonMessage(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), readTextState(buffer)));
	@Override
	public Type<CrazyPhoneContactInfoScreenButtonMessage> type() {
		return TYPE;
	}
	*///? } else {
	public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "crazy_phone_contact_info_screen_buttons");

	public CrazyPhoneContactInfoScreenButtonMessage(FriendlyByteBuf buffer) {
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
	/*public static void handleData(final CrazyPhoneContactInfoScreenButtonMessage message, final IPayloadContext context) {
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
	public static void handleData(final CrazyPhoneContactInfoScreenButtonMessage message, final PlayPayloadContext context) {
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
		HashMap guistate = CrazyPhoneContactInfoScreenMenu.guistate;
		// connect EditBox and CheckBox to guistate
		for (Map.Entry<String, String> entry : textstate.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			guistate.put(key, value);
		}
		// security measure to prevent arbitrary chunk generation
		if (!world.hasChunkAt(new BlockPos(x, y, z)))
			return;
		String number = textstate.get("textin:number");
		if (number == null || number.isEmpty()) return;
		if (buttonID == 0) {
			String owner = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null);
			if (owner != null && !owner.isEmpty()) {
				CrazyPhoneAddContactToPhoneProcedure.execute(world, number, owner);
				ScreenMenuUtils.openPhoneContactsMenu(entity, InteractionHand.MAIN_HAND);
			}
		} else if (buttonID == 1) {
			String name = "";
			String owner = "";
			// phones.get(number) is a compound tag only when that number is actually registered - anything
			// else (missing key, wrong tag type) means "not found", not a real CompoundTag we can copy().
			if (!(PhoneRegistrySavedData.get(world).phones.get(number) instanceof CompoundTag phone))
				return;
			phone = phone.copy();
			name = fr.lordfinn.crazyphone.utils.NbtCompat.getString(phone, "name");
			owner = fr.lordfinn.crazyphone.utils.NbtCompat.getString(phone, "uuid");
			if (!name.isEmpty() && !owner.isEmpty() && entity instanceof ServerPlayer serverPlayer) {
				//? if >=1.20.5 {
				/*PacketDistributor.sendToPlayer(serverPlayer, new UpdateContactInfoMessage(name, owner, number));
				*///? } else {
				PacketDistributor.PLAYER.with(serverPlayer).send(new UpdateContactInfoMessage(name, owner, number));
				//?}
			}
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

	@SubscribeEvent
	public static void registerMessage(FMLCommonSetupEvent event) {
		//? if >=1.20.5 {
		/*Crazyphone.addNetworkMessage(CrazyPhoneContactInfoScreenButtonMessage.TYPE, CrazyPhoneContactInfoScreenButtonMessage.STREAM_CODEC, CrazyPhoneContactInfoScreenButtonMessage::handleData);
		*///? } else {
		Crazyphone.addNetworkMessage(CrazyPhoneContactInfoScreenButtonMessage.ID, CrazyPhoneContactInfoScreenButtonMessage::new, CrazyPhoneContactInfoScreenButtonMessage::handleData);
		//?}
	}
}
