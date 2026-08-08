package fr.lordfinn.crazyphone.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.registries.Registries;

import fr.lordfinn.crazyphone.world.inventory.CrazyphoneHomeScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneSignInScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePicturesScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePictureFoldersScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePasswordScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMayorsCandidatesListMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMayorCandidateScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneConversationMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactsScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactInfoScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneGroupSettingsScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneCallingScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneInCallScreenMenu;
import fr.lordfinn.crazyphone.Crazyphone;
import org.jetbrains.annotations.NotNull;

// Menu type entries are added below as each screen/menu pair is ported.
@EventBusSubscriber
public class ModMenus {
	public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, Crazyphone.MODID);

	public static final DeferredHolder<MenuType<?>, MenuType<CrazyphoneHomeScreenMenu>> CRAZYPHONE_HOME_SCREEN = REGISTRY.register("crazyphone_home_screen", () -> IMenuTypeExtension.create(CrazyphoneHomeScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhonePasswordScreenMenu>> CRAZY_PHONE_PASSWORD_SCREEN = REGISTRY.register("crazy_phone_password_screen", () -> IMenuTypeExtension.create(CrazyPhonePasswordScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneSignInScreenMenu>> CRAZY_PHONE_SIGN_IN_SCREEN = REGISTRY.register("crazy_phone_sign_in_screen", () -> IMenuTypeExtension.create(CrazyPhoneSignInScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhonePictureFoldersScreenMenu>> CRAZY_PHONE_PICTURE_FOLDERS_SCREEN = REGISTRY.register("crazy_phone_picture_folders_screen",
			() -> IMenuTypeExtension.create(CrazyPhonePictureFoldersScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhonePicturesScreenMenu>> CRAZY_PHONE_PICTURES_SCREEN = REGISTRY.register("crazy_phone_pictures_screen", () -> IMenuTypeExtension.create(CrazyPhonePicturesScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneContactsScreenMenu>> CRAZY_PHONE_CONTACTS_SCREEN = REGISTRY.register("crazy_phone_contacts_screen", () -> IMenuTypeExtension.create(CrazyPhoneContactsScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneContactInfoScreenMenu>> CRAZY_PHONE_CONTACT_INFO_SCREEN = REGISTRY.register("crazy_phone_contact_info_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneContactInfoScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneConversationMenu>> CRAZY_PHONE_CONVERSATION = REGISTRY.register("crazy_phone_conversation", () -> IMenuTypeExtension.create(CrazyPhoneConversationMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneMayorsCandidatesListMenu>> CRAZY_PHONE_MAYORS_CANDIDATES_LIST = REGISTRY.register("crazy_phone_mayors_candidates_list",
			() -> IMenuTypeExtension.create(CrazyPhoneMayorsCandidatesListMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneMayorCandidateScreenMenu>> CRAZY_PHONE_MAYOR_CANDIDATE_SCREEN = REGISTRY.register("crazy_phone_mayor_candidate_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneMayorCandidateScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneGroupSettingsScreenMenu>> CRAZY_PHONE_GROUP_SETTINGS_SCREEN = REGISTRY.register("crazy_phone_group_settings_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneGroupSettingsScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneCallingScreenMenu>> CRAZY_PHONE_CALLING_SCREEN = REGISTRY.register("crazy_phone_calling_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneCallingScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneInCallScreenMenu>> CRAZY_PHONE_IN_CALL_SCREEN = REGISTRY.register("crazy_phone_in_call_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneInCallScreenMenu::new));

	/** Always targeted at one player - a textbox value belongs to whoever is looking at that screen, never broadcast it. */
	public static void setText(String boxname, String value, ServerPlayer player) {
		PacketDistributor.sendToPlayer(player, new GuiSyncMessage(boxname, value));
	}

	public static record GuiSyncMessage(String editbox, String value) implements CustomPacketPayload {
		public static final Type<GuiSyncMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "gui_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, GuiSyncMessage> STREAM_CODEC = StreamCodec.of((RegistryFriendlyByteBuf buffer, GuiSyncMessage message) -> {
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, Component.literal(message.editbox));
			ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buffer, Component.literal(message.value));
		}, (RegistryFriendlyByteBuf buffer) -> {
			String editbox = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer).getString();
			String value = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer).getString();
			return new GuiSyncMessage(editbox, value);
		});

		@Override
		public @NotNull Type<GuiSyncMessage> type() {
			return TYPE;
		}

		public static void handleData(final GuiSyncMessage message, final IPayloadContext context) {
			if (context.flow() == PacketFlow.CLIENTBOUND) {
				context.enqueueWork(() -> {
					ModScreens.handleTextBoxMessage(message);
				}).exceptionally(e -> {
					context.connection().disconnect(Component.literal(e.getMessage()));
					return null;
				});
			}
		}
	}

	@SubscribeEvent
	public static void init(FMLCommonSetupEvent event) {
		Crazyphone.addNetworkMessage(GuiSyncMessage.TYPE, GuiSyncMessage.STREAM_CODEC, GuiSyncMessage::handleData);
	}
}
