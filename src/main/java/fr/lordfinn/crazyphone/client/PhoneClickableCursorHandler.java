package fr.lordfinn.crazyphone.client;

import fr.lordfinn.crazyphone.client.gui.PhoneScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Shows a pointer/hand cursor over every standard clickable widget (buttons, image buttons, etc.) on any
 * phone screen - Minecraft has no built-in per-widget hover-cursor concept, so this checks each widget's
 * already-computed hover state (set during the screen's own render pass) once per frame.
 *
 * This is the single place {@link CursorEffects#endFrame()} gets called. It runs in
 * ScreenEvent.Render.Post, which fires strictly after the screen's entire render() has returned - so any
 * screen-specific hover requests made earlier in the frame (e.g. CrazyPhoneConversationScreen's image/head
 * hover checks, or CrazyPhoneContactsScreenScreen's head-slot hover check) have already been recorded via
 * CursorEffects.requestPointerCursor() by the time this aggregates and applies the final state. Calling
 * endFrame() from more than one place per frame would cause it to reset before those later checks run.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class PhoneClickableCursorHandler {

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof PhoneScreen))
            return;

        for (GuiEventListener child : event.getScreen().children()) {
            if (child instanceof AbstractWidget widget && widget.active && widget.visible && widget.isHovered()) {
                CursorEffects.requestPointerCursor();
                break;
            }
        }

        CursorEffects.endFrame();
    }
}
