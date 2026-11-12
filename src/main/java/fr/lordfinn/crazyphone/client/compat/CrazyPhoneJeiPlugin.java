package fr.lordfinn.crazyphone.client.compat;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.gui.CrazyPhoneDefaultScreenScreen;

/**
 * Keeps JEI's overlay (ingredient list, bookmarks) off the phone screens. JEI is optional: this class is only
 * loaded by JEI itself (the @JeiPlugin annotation on NeoForge / Forge, the "jei_mod_plugin" entrypoint on
 * Fabric), the mod never touches it otherwise.
 *
 * Why: the phone is drawn bigger than the vanilla GUI scale (PhoneScale), by laying the screen out in a smaller
 * "virtual" window and zooming the drawing. JEI places its list beside the screen's area in those virtual
 * coordinates but draws it at the real scale, so it landed on top of the phone. JEI has nothing to offer on a
 * phone screen anyway: the screen is reported as covering the whole window, which leaves JEI no room on either
 * side, so it shows nothing there (returning no properties is not enough: JEI then falls back to its own
 * handler for container screens).
 */
@JeiPlugin
public class CrazyPhoneJeiPlugin implements IModPlugin {
    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ getPluginUid() {
        return Crazyphone.parseId("crazyphone:jei");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler((Class) CrazyPhoneDefaultScreenScreen.class, screen -> wholeWindow((Screen) screen));
    }

    private static IGuiProperties wholeWindow(Screen screen) {
        // Both spellings of JEI's getters: the record style names since JEI 19 (Minecraft 1.21), the getX ones
        // before (1.20.x). No @Override, so each JEI version simply calls the ones it knows.
        return new IGuiProperties() {
            public Class<? extends Screen> screenClass() { return screen.getClass(); }
            public int guiLeft() { return 0; }
            public int guiTop() { return 0; }
            public int guiXSize() { return screen.width; }
            public int guiYSize() { return screen.height; }
            public int screenWidth() { return screen.width; }
            public int screenHeight() { return screen.height; }

            public Class<? extends Screen> getScreenClass() { return screen.getClass(); }
            public int getGuiLeft() { return 0; }
            public int getGuiTop() { return 0; }
            public int getGuiXSize() { return screen.width; }
            public int getGuiYSize() { return screen.height; }
            public int getScreenWidth() { return screen.width; }
            public int getScreenHeight() { return screen.height; }
        };
    }
}
