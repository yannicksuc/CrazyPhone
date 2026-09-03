package fr.lordfinn.crazyphone.init;

//? if neoforge {
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;
//?}
//? if fabric && >=1.20.5 {
/*import net.minecraft.client.gui.screens.MenuScreens;
*///?}

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;

//? if neoforge {
import fr.lordfinn.crazyphone.init.ModMenus.GuiSyncMessage;
//?}
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneDefaultScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyphoneHomeScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneSignInScreenScreen;
//? if neoforge {
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneMayorCandidateScreenScreen;
//?}
import fr.lordfinn.crazyphone.client.gui.CrazyPhonePasswordScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneMayorsCandidatesListScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneMyPhotosScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneContactsScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneContactInfoScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneGroupSettingsScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhonePhotoFrameResizeScreen;
// The 3 call screens (Calling/InCall/IncomingCall) need CrazyPhoneCallActionMessage, which needs
// voicechat.CallRegistry - not ported this pass (see build.fabric.gradle.kts's TODO(#165)/SVC note).
// Their MENU classes stay registered (see ModMenus.java) since ScreenMenuUtils.openCallScreenForPlayer,
// the only thing that would ever open them, is itself gated neoforge-only, so this is dead but harmless.
//? if neoforge {
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneCallingScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneIncomingCallScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneInCallScreenScreen;
//?}

import java.util.HashMap;

/**
 * Registers the client-side Screen for each of the 10 phone menus, and handles the {@code GuiSyncMessage}
 * text-box sync packet (server -> client, used to push a computed text value into an open EditBox).
 *
 * Ported from {@code net.mcreator.crazythings.init.CrazythingsModScreens}, minus the {@code RgghgScreen} and
 * {@code OuijaProgramingScreen} registrations - those are non-phone features and out of this port's scope.
 *
 * Deviation from the old file: the old {@code handleTextBoxMessage} checked
 * {@code instanceof CrazythingsModScreens.WidgetScreen}, a marker interface nested inside this very
 * registration class purely to expose {@code getWidgets()}. That interface doesn't exist in this codebase -
 * {@code getWidgets()} is declared directly on {@link CrazyPhoneDefaultScreenScreen} instead, so the check
 * below is {@code instanceof CrazyPhoneDefaultScreenScreen} (see that class's javadoc for the same note).
 */
//? if neoforge {
//? if <1.20.5 {
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
//?} else {
/*@EventBusSubscriber(value = Dist.CLIENT)
*///?}
//?}
public class ModScreens {
	//? if neoforge {
	@SubscribeEvent
	public static void clientLoad(RegisterMenuScreensEvent event) {
		event.register(ModMenus.CRAZYPHONE_HOME_SCREEN.get(), CrazyphoneHomeScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_PASSWORD_SCREEN.get(), CrazyPhonePasswordScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_SIGN_IN_SCREEN.get(), CrazyPhoneSignInScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_CONTACTS_SCREEN.get(), CrazyPhoneContactsScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_CONTACT_INFO_SCREEN.get(), CrazyPhoneContactInfoScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_CONVERSATION.get(), CrazyPhoneConversationScreen::new);
		event.register(ModMenus.CRAZY_PHONE_MAYORS_CANDIDATES_LIST.get(), CrazyPhoneMayorsCandidatesListScreen::new);
		event.register(ModMenus.CRAZY_PHONE_MY_PHOTOS_SCREEN.get(), CrazyPhoneMyPhotosScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_MAYOR_CANDIDATE_SCREEN.get(), CrazyPhoneMayorCandidateScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_GROUP_SETTINGS_SCREEN.get(), CrazyPhoneGroupSettingsScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_CALLING_SCREEN.get(), CrazyPhoneCallingScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_IN_CALL_SCREEN.get(), CrazyPhoneInCallScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_INCOMING_CALL_SCREEN.get(), CrazyPhoneIncomingCallScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_PHOTO_FRAME_RESIZE.get(), CrazyPhonePhotoFrameResizeScreen::new);
	}
	//?}
	//? if fabric && >=1.20.5 {
	/*// Called from CrazyphoneFabricClient#onInitializeClient, after ModMenus.register() has run (menu types
	// must exist before a screen can be bound to one). MenuScreens.register is vanilla, not Fabric API -
	// Loom's access widener set is what makes the otherwise-private method callable from mod code.
	public static void register() {
		MenuScreens.register(ModMenus.CRAZYPHONE_HOME_SCREEN.get(), CrazyphoneHomeScreenScreen::new);
		MenuScreens.register(ModMenus.CRAZY_PHONE_PASSWORD_SCREEN.get(), CrazyPhonePasswordScreenScreen::new);
		MenuScreens.register(ModMenus.CRAZY_PHONE_SIGN_IN_SCREEN.get(), CrazyPhoneSignInScreenScreen::new);
		MenuScreens.register(ModMenus.CRAZY_PHONE_CONTACTS_SCREEN.get(), CrazyPhoneContactsScreenScreen::new);
		MenuScreens.register(ModMenus.CRAZY_PHONE_CONTACT_INFO_SCREEN.get(), CrazyPhoneContactInfoScreenScreen::new);
		MenuScreens.register(ModMenus.CRAZY_PHONE_CONVERSATION.get(), CrazyPhoneConversationScreen::new);
		MenuScreens.register(ModMenus.CRAZY_PHONE_MAYORS_CANDIDATES_LIST.get(), CrazyPhoneMayorsCandidatesListScreen::new);
		MenuScreens.register(ModMenus.CRAZY_PHONE_MY_PHOTOS_SCREEN.get(), CrazyPhoneMyPhotosScreenScreen::new);
		MenuScreens.register(ModMenus.CRAZY_PHONE_GROUP_SETTINGS_SCREEN.get(), CrazyPhoneGroupSettingsScreenScreen::new);
		MenuScreens.register(ModMenus.CRAZY_PHONE_PHOTO_FRAME_RESIZE.get(), CrazyPhonePhotoFrameResizeScreen::new);
		// Mayor-candidate poster screen stays NeoForge-only, matching the feature itself.
		// TODO: calling/in-call/incoming-call screens wait on a voicechat.CallRegistry Fabric port.
	}
	*///?}

	//? if neoforge {
	public static void handleTextBoxMessage(GuiSyncMessage message) {
		String editbox = message.editbox();
		String value = message.value();
		Screen currentScreen = Minecraft.getInstance()./*$ mc_get_screen {*/screen/*$}*/;
		if (currentScreen instanceof CrazyPhoneDefaultScreenScreen sc) {
			HashMap<String, Object> widgets = sc.getWidgets();
			Object obj = widgets.get("text:" + editbox);
			if (obj instanceof EditBox box) {
				box.setValue(value);
			}
		}
	}
	//?}
}
