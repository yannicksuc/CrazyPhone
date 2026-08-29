package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.Crazyphone;

//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
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

        // Default positions
        int photoX = this.leftPos + 12;
        int albumsX = this.leftPos + 61;
        int contactsX = this.leftPos + 34;

        boolean isElection = PhoneRegistrySavedData
            .get(entity.level()).isMayorElectionOn;

        if (isElection) {
            addImageButton("imagebutton_elections", 3, "crazyphone-elections-icon", this.leftPos + 67, this.topPos + 96, 44, 62);
            contactsX -= 26;
        }

        // Photo: purely client-side, opens the same capture overlay the conversation camera icon and
        // punch-to-shoot use - bypasses addImageButton's generic send-packet-then-handleButtonAction
        // machinery entirely since there's nothing server-authoritative about framing a shot.
        net.minecraft.client.gui.components.ImageButton photoButton = new net.minecraft.client.gui.components.ImageButton(
                photoX, this.topPos + 28, 46, 62,
                new net.minecraft.client.gui.components.WidgetSprites(
                        Crazyphone.parseId("crazyphone:textures/screens/crazyphone-photo-icon.png"),
                        Crazyphone.parseId("crazyphone:textures/screens/crazyphone-photo-icon-hover.png")),
                e -> fr.lordfinn.crazyphone.client.CrazyPhoneCaptureMode.enter("")) {
            @Override
            public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
                fr.lordfinn.crazyphone.utils.GuiCompat.blit(guiGraphics, sprites.get(isActive(), isHoveredOrFocused()), getX(), getY(), 0, width, height);
            }
        };
        guistate.put("button:imagebutton_photo", photoButton);
        this.addRenderableWidget(photoButton);

        addImageButton("imagebutton_albums", 1, "crazyphone-album-icon", albumsX, this.topPos + 28, 52, 62);
        addImageButton("imagebutton_contacts", 2, "crazyphone-contacts-icon", contactsX, this.topPos + 92, 53, 66);
    }

    // No tooltips on these - the home screen's 4 icon buttons are meant to be read at a glance, not hovered.
    private void addImageButton(String key, int buttonId, String baseIconName, int x, int y, int width, int height) {
        /*$ res_loc {*/ResourceLocation/*$}*/ normal = Crazyphone.parseId("crazyphone:textures/screens/" + baseIconName + ".png");
        /*$ res_loc {*/ResourceLocation/*$}*/ hover = Crazyphone.parseId("crazyphone:textures/screens/" + baseIconName + "-hover.png");

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
