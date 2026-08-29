
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

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
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
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.ScreenMenuUtils;
import fr.lordfinn.crazyphone.Crazyphone;

import java.util.HashMap;
import java.util.Map;

/**
 * Validates staged group-settings changes (rename / icon / exclusions) made in
 * {@link fr.lordfinn.crazyphone.client.gui.CrazyPhoneGroupSettingsScreenScreen}. Nothing from the client
 * is trusted at face value: every permission check (must be a current member; only the admin may exclude
 * someone other than themselves) is re-verified here against server-side state before anything is applied,
 * same discipline as every other write path in this mod.
 */
//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber
*///?}
//?}
public record CrazyPhoneGroupSettingsButtonMessage(int buttonID, int x, int y, int z, HashMap<String, String> textstate) implements CustomPacketPayload {

	//? if >=1.20.5 {
	/*public static final Type<CrazyPhoneGroupSettingsButtonMessage> TYPE = new Type<>(Crazyphone.resource("crazy_phone_group_settings_buttons"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneGroupSettingsButtonMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, CrazyPhoneGroupSettingsButtonMessage message) -> {
		buffer.writeInt(message.buttonID);
		buffer.writeInt(message.x);
		buffer.writeInt(message.y);
		buffer.writeInt(message.z);
		writeTextState(message.textstate, buffer);
	}, (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneGroupSettingsButtonMessage(buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readInt(), readTextState(buffer)));

	@Override
	public Type<CrazyPhoneGroupSettingsButtonMessage> type() {
		return TYPE;
	}
	*///? } else {
	public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "crazy_phone_group_settings_buttons");

	public CrazyPhoneGroupSettingsButtonMessage(FriendlyByteBuf buffer) {
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
	/*public static void handleData(final CrazyPhoneGroupSettingsButtonMessage message, final IPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.enqueueWork(() -> handleButtonAction(context.player(), message.buttonID, message.x, message.y, message.z, message.textstate))
				.exceptionally(e -> {
					context.connection().disconnect(Component.literal(e.getMessage()));
					return null;
				});
		}
	}
	*///? } else {
	public static void handleData(final CrazyPhoneGroupSettingsButtonMessage message, final PlayPayloadContext context) {
		if (context.flow() == PacketFlow.SERVERBOUND) {
			context.workHandler().submitAsync(() -> handleButtonAction(context.player().orElse(null), message.buttonID, message.x, message.y, message.z, message.textstate))
				.exceptionally(e -> {
					context.packetHandler().disconnect(Component.literal(e.getMessage()));
					return null;
				});
		}
	}
	//?}
	//?}
	//? if fabric && >=1.20.5 {
	/*public static void handleDataFabric(CrazyPhoneGroupSettingsButtonMessage message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
		handleButtonAction(context.player(), message.buttonID, message.x, message.y, message.z, message.textstate);
	}
	*///?}

	public static void handleButtonAction(Player entity, int buttonID, int x, int y, int z, HashMap<String, String> textstate) {
		if (buttonID != 0 || entity == null || entity.level().isClientSide())
			return; // every change here is server-authoritative only - no client-side optimistic echo

		Level world = entity.level();
		if (!world.hasChunkAt(new BlockPos(x, y, z)))
			return;

		String conversationId = textstate.getOrDefault("conversationId", "");
		if (conversationId.isEmpty())
			return;

		String requesterNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null);
		if (requesterNumber.isEmpty() || !CrazyPhoneHelper.getGroupMembers(world, conversationId).contains(requesterNumber))
			return;

		CrazyPhoneHelper.GroupMeta meta = CrazyPhoneHelper.getGroupMeta(world, conversationId);
		String requesterName = contactName(world, requesterNumber);

		applyRename(world, conversationId, meta, textstate, requesterName);
		applyIconChange(world, conversationId, meta, textstate, requesterName);
		applyAdditions(world, conversationId, textstate, requesterName);
		boolean selfExcluded = applyExclusions(world, conversationId, textstate, requesterNumber, requesterName);

		if (selfExcluded) {
			ScreenMenuUtils.openPhoneContactsMenu(entity, InteractionHand.MAIN_HAND);
		} else {
			ScreenMenuUtils.openPhoneConversationMenu(entity, InteractionHand.MAIN_HAND, conversationId);
		}
	}

	private static void applyRename(Level world, String conversationId, CrazyPhoneHelper.GroupMeta meta, HashMap<String, String> textstate, String requesterName) {
		String newName = textstate.getOrDefault("groupName", "").trim();
		if (newName.equals(meta.name()))
			return;
		CrazyPhoneHelper.renameGroup(world, conversationId, newName);
		Component text = newName.isEmpty()
				? Component.translatable("gui.crazyphone.crazy_phone_group_settings.system_name_cleared", requesterName)
				: Component.translatable("gui.crazyphone.crazy_phone_group_settings.system_renamed", requesterName, newName);
		CrazyPhoneHelper.addSystemMessage(world, conversationId, text, new ItemStack(Items.NAME_TAG));
	}

	private static void applyIconChange(Level world, String conversationId, CrazyPhoneHelper.GroupMeta meta, HashMap<String, String> textstate, String requesterName) {
		ItemStack newIcon = decodeIconFromTextState(world, textstate);
		//? if >=1.20.5 {
		/*if (ItemStack.isSameItemSameComponents(newIcon, meta.icon()))
			return;
		*///? } else {
		if (ItemStack.isSameItemSameTags(newIcon, meta.icon()))
			return;
		//?}
		CrazyPhoneHelper.setGroupIcon(world, conversationId, newIcon);
		Component text = Component.translatable("gui.crazyphone.crazy_phone_group_settings.system_icon_changed", requesterName);
		CrazyPhoneHelper.addSystemMessage(world, conversationId, text, newIcon);
	}

	/** The client stages the full staged icon (every data component, not just the item id) as SNBT text in
	 * the generic textstate string map - crude, but keeps the existing map-based packet shape rather than
	 * adding a whole second, ItemStack-typed field just for this. */
	private static ItemStack decodeIconFromTextState(Level world, HashMap<String, String> textstate) {
		String iconNbt = textstate.getOrDefault("iconItem", "");
		if (iconNbt.isEmpty())
			return ItemStack.EMPTY;
		try {
			//? if <1.21.10 {
			CompoundTag tag = TagParser.parseTag(iconNbt);
			//? } else {
			/*CompoundTag tag = TagParser.parseCompoundFully(iconNbt);
			*///?}
			return CrazyPhoneHelper.decodeItemStack(world, tag);
		} catch (Exception e) {
			return ItemStack.EMPTY;
		}
	}

	/** Any current member (not just the admin) may invite one of their own contacts into the group. */
	private static void applyAdditions(Level world, String conversationId, HashMap<String, String> textstate, String requesterName) {
		String addedCsv = textstate.getOrDefault("addedNumbers", "");
		if (addedCsv.isEmpty())
			return;

		String groupLabel = groupLabel(world, conversationId);
		for (String target : addedCsv.split(",")) {
			if (target.isEmpty())
				continue;
			if (CrazyPhoneHelper.getGroupMembers(world, conversationId).contains(target))
				continue; // already a member

			CrazyPhoneHelper.addGroupMember(world, conversationId, target);
			Component text = Component.translatable("gui.crazyphone.crazy_phone_group_settings.system_added", requesterName, contactName(world, target));
			CrazyPhoneHelper.addSystemMessage(world, conversationId, text, new ItemStack(Items.PLAYER_HEAD));
			CrazyPhoneHelper.notifyGroupAddition(world, target, groupLabel, requesterName);
		}
	}

	/** The group's custom name if set, otherwise a generic fallback - used for the "you were added to X"
	 * notification, which needs a single readable label regardless of whether the group's been named yet.
	 * Hardcoded French, like the sibling {@link fr.lordfinn.crazyphone.network.CrazyPhoneNewMessageNotificationPacket}
	 * notification it mirrors - a Component wouldn't resolve to translated text server-side anyway (no lang
	 * files loaded there), and that packet's toast text isn't run through the lang files either. */
	private static String groupLabel(Level world, String conversationId) {
		String name = CrazyPhoneHelper.getGroupMeta(world, conversationId).name();
		return (name != null && !name.isEmpty()) ? name : "Groupe";
	}

	/** Returns whether the requester excluded themselves (so the caller knows to send them back to
	 * Contacts instead of reopening a conversation they no longer have access to). */
	private static boolean applyExclusions(Level world, String conversationId, HashMap<String, String> textstate, String requesterNumber, String requesterName) {
		String excludedCsv = textstate.getOrDefault("excludedNumbers", "");
		if (excludedCsv.isEmpty())
			return false;

		boolean selfExcluded = false;
		String admin = CrazyPhoneHelper.getGroupMeta(world, conversationId).admin();
		String groupLabel = groupLabel(world, conversationId);
		for (String target : excludedCsv.split(",")) {
			if (target.isEmpty())
				continue;
			boolean isSelf = target.equals(requesterNumber);
			if (!isSelf && !requesterNumber.equals(admin))
				continue; // only the admin may exclude someone other than themselves
			if (!CrazyPhoneHelper.getGroupMembers(world, conversationId).contains(target))
				continue; // already excluded / never a member

			String targetName = contactName(world, target);
			String newAdmin = CrazyPhoneHelper.excludeGroupMember(world, conversationId, target);

			Component text = isSelf
					? Component.translatable("gui.crazyphone.crazy_phone_group_settings.system_left", targetName)
					: Component.translatable("gui.crazyphone.crazy_phone_group_settings.system_removed", requesterName, targetName);
			CrazyPhoneHelper.addSystemMessage(world, conversationId, text, new ItemStack(Items.PLAYER_HEAD));

			if (!isSelf)
				CrazyPhoneHelper.notifyGroupRemoval(world, target, groupLabel, requesterName);

			if (newAdmin != null) {
				Component adminText = Component.translatable("gui.crazyphone.crazy_phone_group_settings.system_new_admin", contactName(world, newAdmin));
				CrazyPhoneHelper.addSystemMessage(world, conversationId, adminText, new ItemStack(Items.PLAYER_HEAD));
			}

			if (isSelf)
				selfExcluded = true;
		}
		return selfExcluded;
	}

	private static String contactName(Level world, String number) {
		Contact contact = CrazyPhoneHelper.getContact(world, number);
		return contact != null ? contact.getName() : number;
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
		/*Crazyphone.addNetworkMessage(CrazyPhoneGroupSettingsButtonMessage.TYPE, CrazyPhoneGroupSettingsButtonMessage.STREAM_CODEC, CrazyPhoneGroupSettingsButtonMessage::handleData);
		*///? } else {
		Crazyphone.addNetworkMessage(CrazyPhoneGroupSettingsButtonMessage.ID, CrazyPhoneGroupSettingsButtonMessage::new, CrazyPhoneGroupSettingsButtonMessage::handleData);
		//?}
	}
	//?}
	//? if fabric && >=1.20.5 {
	/*public static void registerFabricType() {
		fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
	}

	public static void registerFabricServerReceiver() {
		fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneGroupSettingsButtonMessage::handleDataFabric);
	}
	*///?}
}
