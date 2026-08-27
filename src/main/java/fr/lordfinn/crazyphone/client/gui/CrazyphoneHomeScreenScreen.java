package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.Crazyphone;

//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.network.CrazyphoneHomeScreenButtonMessage;
import fr.lordfinn.crazyphone.world.inventory.CrazyphoneHomeScreenMenu;

import java.util.HashMap;

public class CrazyphoneHomeScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyphoneHomeScreenMenu> {
    private final HashMap<String, Object> guistate = CrazyphoneHomeScreenMenu.guistate;

    public CrazyphoneHomeScreenScreen(CrazyphoneHomeScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public static HashMap<String, String> getEditBoxAndCheckBoxValues() {
        return new HashMap<>();
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Optional: add static labels if necessary
    }

    @Override
    public void init() {
        super.init();

        // Default positions. The photo/albums icons that used to sit here were the Camera-mod-backed
        // standalone capture/gallery entry points (no target conversation) - removed along with Camera mod
        // itself, since the native pipeline always ties a photo to the conversation it's taken in (see the
        // camera icon inside CrazyPhoneConversationScreen instead). Left as a TODO for whoever picks the
        // home screen's next layout pass: contacts/elections currently just keep their original coordinates,
        // leaving open space where those two icons used to be.
        int contactsX = this.leftPos + 34;

        boolean isElection = PhoneRegistrySavedData
            .get(entity.level()).isMayorElectionOn;

        if (isElection) {
            addImageButton("imagebutton_elections", 3, "crazyphone-elections-icon", this.leftPos + 67, this.topPos + 96, 44, 62);
            contactsX -= 26;
        }

        addImageButton("imagebutton_contacts", 2, "crazyphone-contacts-icon", contactsX, this.topPos + 92, 53, 66);
    }

    // No tooltips on these - the home screen's 4 icon buttons are meant to be read at a glance, not hovered.
    private void addImageButton(String key, int buttonId, String baseIconName, int x, int y, int width, int height) {
        ResourceLocation normal = Crazyphone.parseId("crazyphone:textures/screens/" + baseIconName + ".png");
        ResourceLocation hover = Crazyphone.parseId("crazyphone:textures/screens/" + baseIconName + "-hover.png");

        ImageButton button = new ImageButton(x, y, width, height, new net.minecraft.client.gui.components.WidgetSprites(normal, hover), e -> {
            var values = getEditBoxAndCheckBoxValues();
            //? if >=1.20.5 {
            /*NetworkAccess.sendToServer(new CrazyphoneHomeScreenButtonMessage(buttonId, this.x, this.y, this.z, values));
            *///? } else {
            PacketDistributor.SERVER.noArg().send(new CrazyphoneHomeScreenButtonMessage(buttonId, this.x, this.y, this.z, values));
            //?}
            CrazyphoneHomeScreenButtonMessage.handleButtonAction(this.entity, buttonId, this.x, this.y, this.z, values);
        }) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
                fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
            }
        };

        guistate.put("button:" + key, button);
        this.addRenderableWidget(button);
    }

    @Override
    public HashMap<String, Object> getWidgets() {
        return guistate;
    }
}
