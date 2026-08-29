package fr.lordfinn.crazyphone.init;

//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
//? if >=1.20.5 {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
*///? } else {
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import fr.lordfinn.crazyphone.utils.RegistryEntry;
import io.netty.buffer.Unpooled;
*///?}

import net.minecraft.world.inventory.MenuType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import fr.lordfinn.crazyphone.world.inventory.CrazyphoneHomeScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneSignInScreenMenu;
//? if neoforge {
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMayorCandidateScreenMenu;
//?}
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePasswordScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMayorsCandidatesListMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMyPhotosScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneConversationMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactsScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactInfoScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneGroupSettingsScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneCallingScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneIncomingCallScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneInCallScreenMenu;
import fr.lordfinn.crazyphone.Crazyphone;
import org.jetbrains.annotations.NotNull;

// Menu type entries are added below as each screen/menu pair is ported.
//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber
*///?}
//?}
public class ModMenus {
    //? if neoforge {
	public static final DeferredRegister<MenuType<?>> REGISTRY = DeferredRegister.create(Registries.MENU, Crazyphone.MODID);

	public static final DeferredHolder<MenuType<?>, MenuType<CrazyphoneHomeScreenMenu>> CRAZYPHONE_HOME_SCREEN = REGISTRY.register("crazyphone_home_screen", () -> IMenuTypeExtension.create(CrazyphoneHomeScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhonePasswordScreenMenu>> CRAZY_PHONE_PASSWORD_SCREEN = REGISTRY.register("crazy_phone_password_screen", () -> IMenuTypeExtension.create(CrazyPhonePasswordScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneSignInScreenMenu>> CRAZY_PHONE_SIGN_IN_SCREEN = REGISTRY.register("crazy_phone_sign_in_screen", () -> IMenuTypeExtension.create(CrazyPhoneSignInScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneContactsScreenMenu>> CRAZY_PHONE_CONTACTS_SCREEN = REGISTRY.register("crazy_phone_contacts_screen", () -> IMenuTypeExtension.create(CrazyPhoneContactsScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneContactInfoScreenMenu>> CRAZY_PHONE_CONTACT_INFO_SCREEN = REGISTRY.register("crazy_phone_contact_info_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneContactInfoScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneConversationMenu>> CRAZY_PHONE_CONVERSATION = REGISTRY.register("crazy_phone_conversation", () -> IMenuTypeExtension.create(CrazyPhoneConversationMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneMayorsCandidatesListMenu>> CRAZY_PHONE_MAYORS_CANDIDATES_LIST = REGISTRY.register("crazy_phone_mayors_candidates_list",
			() -> IMenuTypeExtension.create(CrazyPhoneMayorsCandidatesListMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneMyPhotosScreenMenu>> CRAZY_PHONE_MY_PHOTOS_SCREEN = REGISTRY.register("crazy_phone_my_photos_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneMyPhotosScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneMayorCandidateScreenMenu>> CRAZY_PHONE_MAYOR_CANDIDATE_SCREEN = REGISTRY.register("crazy_phone_mayor_candidate_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneMayorCandidateScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneGroupSettingsScreenMenu>> CRAZY_PHONE_GROUP_SETTINGS_SCREEN = REGISTRY.register("crazy_phone_group_settings_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneGroupSettingsScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneCallingScreenMenu>> CRAZY_PHONE_CALLING_SCREEN = REGISTRY.register("crazy_phone_calling_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneCallingScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneInCallScreenMenu>> CRAZY_PHONE_IN_CALL_SCREEN = REGISTRY.register("crazy_phone_in_call_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneInCallScreenMenu::new));
	public static final DeferredHolder<MenuType<?>, MenuType<CrazyPhoneIncomingCallScreenMenu>> CRAZY_PHONE_INCOMING_CALL_SCREEN = REGISTRY.register("crazy_phone_incoming_call_screen",
			() -> IMenuTypeExtension.create(CrazyPhoneIncomingCallScreenMenu::new));
    //?}
    //? if fabric && >=1.20.5 {
    /*// Vanilla's own MenuType constructor is private (only Fabric API's own access-widened
    // ExtendedScreenHandlerType can reach it) - every menu here needs extra opening data (block pos, hand,
    // and per-menu payload), which is exactly what ExtendedScreenHandlerType<T, D> is for. D is the raw
    // RegistryFriendlyByteBuf itself via PASSTHROUGH_CODEC below, so every existing menu constructor
    // (int, Inventory, FriendlyByteBuf) keeps working unmodified - RegistryFriendlyByteBuf IS-A
    // FriendlyByteBuf. The decode side must actually consume every byte of the network buffer itself
    // (copying them into a fresh buffer with its own reader index reset to 0) rather than just handing
    // back the network buffer unread: the packet layer verifies the network buffer was fully drained
    // the moment decode() returns, before the menu constructor ever gets a chance to read from it - an
    // earlier version of this codec returned the network buffer as-is, which always failed that check
    // ("N bytes extra") since decode itself never advanced its reader index.
    public static final StreamCodec<RegistryFriendlyByteBuf, RegistryFriendlyByteBuf> PASSTHROUGH_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf networkBuf, RegistryFriendlyByteBuf data) -> networkBuf.writeBytes(data),
            (RegistryFriendlyByteBuf networkBuf) -> {
                int length = networkBuf.readableBytes();
                RegistryFriendlyByteBuf copy = new RegistryFriendlyByteBuf(Unpooled.buffer(length), networkBuf.registryAccess());
                networkBuf.readBytes(copy, length);
                return copy;
            });

    public static RegistryEntry<MenuType<CrazyphoneHomeScreenMenu>> CRAZYPHONE_HOME_SCREEN;
    public static RegistryEntry<MenuType<CrazyPhonePasswordScreenMenu>> CRAZY_PHONE_PASSWORD_SCREEN;
    public static RegistryEntry<MenuType<CrazyPhoneSignInScreenMenu>> CRAZY_PHONE_SIGN_IN_SCREEN;
    public static RegistryEntry<MenuType<CrazyPhoneContactsScreenMenu>> CRAZY_PHONE_CONTACTS_SCREEN;
    public static RegistryEntry<MenuType<CrazyPhoneContactInfoScreenMenu>> CRAZY_PHONE_CONTACT_INFO_SCREEN;
    public static RegistryEntry<MenuType<CrazyPhoneConversationMenu>> CRAZY_PHONE_CONVERSATION;
    public static RegistryEntry<MenuType<CrazyPhoneMayorsCandidatesListMenu>> CRAZY_PHONE_MAYORS_CANDIDATES_LIST;
    public static RegistryEntry<MenuType<CrazyPhoneMyPhotosScreenMenu>> CRAZY_PHONE_MY_PHOTOS_SCREEN;
    public static RegistryEntry<MenuType<CrazyPhoneGroupSettingsScreenMenu>> CRAZY_PHONE_GROUP_SETTINGS_SCREEN;
    public static RegistryEntry<MenuType<CrazyPhoneCallingScreenMenu>> CRAZY_PHONE_CALLING_SCREEN;
    public static RegistryEntry<MenuType<CrazyPhoneInCallScreenMenu>> CRAZY_PHONE_IN_CALL_SCREEN;
    public static RegistryEntry<MenuType<CrazyPhoneIncomingCallScreenMenu>> CRAZY_PHONE_INCOMING_CALL_SCREEN;

    private static <T extends net.minecraft.world.inventory.AbstractContainerMenu> RegistryEntry<MenuType<T>> registerMenu(
            String id, ExtendedScreenHandlerType.ExtendedFactory<T, RegistryFriendlyByteBuf> factory) {
        return new RegistryEntry<>(Registry.register(BuiltInRegistries.MENU, Crazyphone.resource(id),
                new ExtendedScreenHandlerType<>(factory, PASSTHROUGH_CODEC)));
    }

    public static void register() {
        CRAZYPHONE_HOME_SCREEN = registerMenu("crazyphone_home_screen", CrazyphoneHomeScreenMenu::new);
        CRAZY_PHONE_PASSWORD_SCREEN = registerMenu("crazy_phone_password_screen", CrazyPhonePasswordScreenMenu::new);
        CRAZY_PHONE_SIGN_IN_SCREEN = registerMenu("crazy_phone_sign_in_screen", CrazyPhoneSignInScreenMenu::new);
        CRAZY_PHONE_CONTACTS_SCREEN = registerMenu("crazy_phone_contacts_screen", CrazyPhoneContactsScreenMenu::new);
        CRAZY_PHONE_CONTACT_INFO_SCREEN = registerMenu("crazy_phone_contact_info_screen", CrazyPhoneContactInfoScreenMenu::new);
        CRAZY_PHONE_CONVERSATION = registerMenu("crazy_phone_conversation", CrazyPhoneConversationMenu::new);
        CRAZY_PHONE_MAYORS_CANDIDATES_LIST = registerMenu("crazy_phone_mayors_candidates_list", CrazyPhoneMayorsCandidatesListMenu::new);
        CRAZY_PHONE_MY_PHOTOS_SCREEN = registerMenu("crazy_phone_my_photos_screen", CrazyPhoneMyPhotosScreenMenu::new);
        CRAZY_PHONE_GROUP_SETTINGS_SCREEN = registerMenu("crazy_phone_group_settings_screen", CrazyPhoneGroupSettingsScreenMenu::new);
        CRAZY_PHONE_CALLING_SCREEN = registerMenu("crazy_phone_calling_screen", CrazyPhoneCallingScreenMenu::new);
        CRAZY_PHONE_IN_CALL_SCREEN = registerMenu("crazy_phone_in_call_screen", CrazyPhoneInCallScreenMenu::new);
        CRAZY_PHONE_INCOMING_CALL_SCREEN = registerMenu("crazy_phone_incoming_call_screen", CrazyPhoneIncomingCallScreenMenu::new);
        // CRAZY_PHONE_MAYOR_CANDIDATE_SCREEN stays NeoForge-only, matching the mayor-poster feature itself
        // (see build.fabric.gradle.kts) - the picture-folders/album screens it used to sit alongside are gone.
    }
    *///?}

	/** Always targeted at one player - a textbox value belongs to whoever is looking at that screen, never broadcast it. */
	//? if neoforge {
	public static void setText(String boxname, String value, ServerPlayer player) {
		//? if >=1.20.5 {
		/*//? if >=1.20.5 {
		/^PacketDistributor.sendToPlayer(player, new GuiSyncMessage(boxname, value));
		^///? } else {
		PacketDistributor.PLAYER.with(player).send(new GuiSyncMessage(boxname, value));
		//?}
		*///? } else {
		PacketDistributor.PLAYER.with(player).send(new GuiSyncMessage(boxname, value));
		//?}
	}

	public static record GuiSyncMessage(String editbox, String value) implements CustomPacketPayload {
		//? if >=1.20.5 {
		/*public static final Type<GuiSyncMessage> TYPE = new Type<>(Crazyphone.resource("gui_sync"));
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
		*///? } else {
		public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = new /*$ res_loc {*/ResourceLocation/*$}*/(Crazyphone.MODID, "gui_sync");

		public GuiSyncMessage(FriendlyByteBuf buffer) {
			this(buffer.readUtf(), buffer.readUtf());
		}

		public void write(FriendlyByteBuf buffer) {
			buffer.writeUtf(editbox);
			buffer.writeUtf(value);
		}

		@Override
		public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
			return ID;
		}
		//?}

		//? if >=1.20.5 {
		/*public static void handleData(final GuiSyncMessage message, final IPayloadContext context) {
			if (context.flow() == PacketFlow.CLIENTBOUND) {
				context.enqueueWork(() -> {
					ModScreens.handleTextBoxMessage(message);
				}).exceptionally(e -> {
					context.connection().disconnect(Component.literal(e.getMessage()));
					return null;
				});
			}
		}
		*///? } else {
		public static void handleData(final GuiSyncMessage message, final PlayPayloadContext context) {
			if (context.flow() == PacketFlow.CLIENTBOUND) {
				context.workHandler().submitAsync(() -> {
					ModScreens.handleTextBoxMessage(message);
				}).exceptionally(e -> {
					context.packetHandler().disconnect(Component.literal(e.getMessage()));
					return null;
				});
			}
		}
		//?}
	}

	@SubscribeEvent
	public static void init(FMLCommonSetupEvent event) {
		//? if >=1.20.5 {
		/*Crazyphone.addNetworkMessage(GuiSyncMessage.TYPE, GuiSyncMessage.STREAM_CODEC, GuiSyncMessage::handleData);
		*///? } else {
		Crazyphone.addNetworkMessage(GuiSyncMessage.ID, GuiSyncMessage::new, GuiSyncMessage::handleData);
		//?}
	}
	//?}
}
