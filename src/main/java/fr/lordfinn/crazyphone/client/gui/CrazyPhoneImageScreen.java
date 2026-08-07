package fr.lordfinn.crazyphone.client.gui;

import de.maxhenkel.camera.ImageData;
import de.maxhenkel.camera.gui.ImageScreen;
import fr.lordfinn.crazyphone.client.gui.components.MessageWidget;
import fr.lordfinn.crazyphone.network.CrazyPhoneAlbumClosedMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneImageActionMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneImageActionMessage.ImageActionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class CrazyPhoneImageScreen extends ImageScreen implements PhoneScreen {

    private final ItemStack image;
    private final Screen previousScreen;
    private Button button_take;

    public CrazyPhoneImageScreen(ItemStack image) {
        super(image);
        this.image = image;
        // Opened via a direct setScreen() replacement (not a pushed GUI layer), so ImageScreen's
        // inherited onClose() chain (AbstractContainerScreen -> closeContainer() -> popGuiLayer()) isn't
        // guaranteed to actually dismiss it - closing explicitly restores whatever screen (the
        // conversation) was open before this one, instead of dropping the player out of the phone
        // entirely back to plain gameplay.
        this.previousScreen = Minecraft.getInstance().screen;
    }

    /**
     * The camera mod's own ImageScreen#renderBg computes an 80%-of-window box for the picture but never
     * actually centers that box on the window (it draws from the (0,0) origin), so the image renders
     * shifted toward the top-left instead of centered. Overriding renderBg here bypasses that
     * third-party positioning bug entirely, reusing MessageWidget's already-correct drawImage (same
     * fit-to-width logic used for images in the conversation feed) with an explicit centering offset.
     */
    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        this.renderTransparentBackground(guiGraphics);
        if (image.isEmpty())
            return;

        ImageData imageData = ImageData.fromStack(image);
        if (imageData == null)
            return;
        UUID imageID = imageData.getId();
        if (imageID == null)
            return;

        int boxWidth = (int) (this.width * 0.8f);
        int boxHeight = (int) (this.height * 0.8f);
        int x = (this.width - boxWidth) / 2;
        int y = (this.height - boxHeight) / 2;

        MessageWidget.drawImage(guiGraphics, Minecraft.getInstance(), x, y, boxWidth, boxHeight, 100f, imageID);
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 90;
        int buttonHeight = 20;
        int spacing = 5;
        int totalWidth = buttonWidth * 3 + spacing * 2;
        int startX = (this.width - totalWidth) / 2;
        int y = this.height - 30 + 2;

        Button button_back = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.button_retour"), e -> this.onClose())
                .bounds(startX, y, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_back")))
                .build();

        // Bouton 2 : Prendre
        button_take = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.button_take"), e -> {
            if (!image.isEmpty()) {
                PacketDistributor.sendToServer(new CrazyPhoneImageActionMessage(image.copy(), ImageActionType.GIVE_PLAYER));
                Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);
            }
            this.onClose();
        }).bounds(startX + buttonWidth + spacing, y, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_take")))
                .build();

        // Bouton 3 : Ajouter dans mes albums
        Button button_addToAlbum = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.button_add_album"), e -> {
            if (!image.isEmpty()) {
                PacketDistributor.sendToServer(new CrazyPhoneImageActionMessage(image.copy(), ImageActionType.GIVE_ALBUM));
                Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.BOOK_PUT, 1.0F, 1.0F);
            }
            this.onClose();
        }).bounds(startX + (buttonWidth + spacing) * 2, y, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_add_album")))
                .build();

        this.addRenderableWidget(button_back);
        this.addRenderableWidget(button_take);
        this.addRenderableWidget(button_addToAlbum);
    }

    @Override
    public void onClose() {
        super.onClose();
        PacketDistributor.sendToServer(new CrazyPhoneAlbumClosedMessage());
        Minecraft.getInstance().setScreen(previousScreen);
    }
}
