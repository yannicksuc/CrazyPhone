package fr.lordfinn.crazyphone.init;

import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.Minecraft;

import fr.lordfinn.crazyphone.init.ModMenus.GuiSyncMessage;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneDefaultScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyphoneHomeScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneSignInScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhonePicturesScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhonePictureFoldersScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhonePasswordScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneMayorsCandidatesListScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneMayorCandidateScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneContactsScreenScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneContactInfoScreenScreen;

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
@EventBusSubscriber(value = Dist.CLIENT)
public class ModScreens {
	@SubscribeEvent
	public static void clientLoad(RegisterMenuScreensEvent event) {
		event.register(ModMenus.CRAZYPHONE_HOME_SCREEN.get(), CrazyphoneHomeScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_PASSWORD_SCREEN.get(), CrazyPhonePasswordScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_SIGN_IN_SCREEN.get(), CrazyPhoneSignInScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_PICTURE_FOLDERS_SCREEN.get(), CrazyPhonePictureFoldersScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_PICTURES_SCREEN.get(), CrazyPhonePicturesScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_CONTACTS_SCREEN.get(), CrazyPhoneContactsScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_CONTACT_INFO_SCREEN.get(), CrazyPhoneContactInfoScreenScreen::new);
		event.register(ModMenus.CRAZY_PHONE_CONVERSATION.get(), CrazyPhoneConversationScreen::new);
		event.register(ModMenus.CRAZY_PHONE_MAYORS_CANDIDATES_LIST.get(), CrazyPhoneMayorsCandidatesListScreen::new);
		event.register(ModMenus.CRAZY_PHONE_MAYOR_CANDIDATE_SCREEN.get(), CrazyPhoneMayorCandidateScreenScreen::new);
	}

	public static void handleTextBoxMessage(GuiSyncMessage message) {
		String editbox = message.editbox();
		String value = message.value();
		Screen currentScreen = Minecraft.getInstance().screen;
		if (currentScreen instanceof CrazyPhoneDefaultScreenScreen sc) {
			HashMap<String, Object> widgets = sc.getWidgets();
			Object obj = widgets.get("text:" + editbox);
			if (obj instanceof EditBox box) {
				box.setValue(value);
			}
		}
	}
}
