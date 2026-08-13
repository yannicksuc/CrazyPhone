package fr.lordfinn.crazyphone.client.gui;

import java.util.List;
import java.util.UUID;

import de.maxhenkel.camera.gui.AlbumScreen;
import fr.lordfinn.crazyphone.client.gui.components.MessageWidget;
import fr.lordfinn.crazyphone.network.CrazyPhoneAlbumClosedMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneImageActionMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneImageActionMessage.ImageActionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public class CrazyPhoneAlbumScreen extends AlbumScreen implements PhoneScreen {

    private final List<ItemStack> imageStacks;

    public CrazyPhoneAlbumScreen(List<UUID> images, List<ItemStack> imageStacks) {
        super(images);
        this.imageStacks = imageStacks;
    }

    @Override
    protected void init() {
        super.init();

        int buttonWidth = 90;
        int buttonHeight = 20;
        int spacing = 5;
        int totalWidth = buttonWidth * 2 + spacing;
        int startX = (this.width - totalWidth) / 2;
        int y = this.height - 30 + 2;

        Button button_back = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.button_retour"), e -> this.onClose())
                .bounds(startX, y, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_back")))
                .build();

        Button button_take = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.button_take"), e -> {
            if (this.images != null && !this.images.isEmpty() && this.index < imageStacks.size()) {
                ItemStack stack = imageStacks.get(this.index);
                if (!stack.isEmpty()) {
                    //? if >=1.20.5 {
                    PacketDistributor.sendToServer(new CrazyPhoneImageActionMessage(stack.copy(), ImageActionType.GIVE_PLAYER));
                    //? } else {
                    /*PacketDistributor.SERVER.noArg().send(new CrazyPhoneImageActionMessage(stack.copy(), ImageActionType.GIVE_PLAYER));
                    *///?}
                    Minecraft.getInstance().player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);
                }
            }
            this.onClose();
        }).bounds(startX + buttonWidth + spacing, y, buttonWidth, buttonHeight)
                .tooltip(Tooltip.create(Component.translatable("gui.crazyphone.crazy_phone_pictures_screen.tooltip_take")))
                .build();

        this.addRenderableWidget(button_back);
        this.addRenderableWidget(button_take);
    }

    /**
     * The camera mod's AlbumScreen#render draws the current image via the same ImageScreen#drawImage
     * call that CrazyPhoneImageScreen's renderBg override already had to work around - it uses an
     * 80%-of-window box anchored at (0,0) instead of centered on the window. Since Java has no way to
     * call a grandparent class's render() while skipping AlbumScreen's own override in between, this
     * replicates the safe parts of that render pass (background + widgets + tooltip, same as vanilla
     * AbstractContainerScreen#render for a screen with no visible slots) and draws the image itself with
     * MessageWidget's already-correct drawImage instead.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        // Image drawn BEFORE the renderables (Retour/Prendre buttons) so the buttons paint on top of it -
        // they previously rendered first and were then fully covered by the image drawn after them.
        drawCenteredImage(guiGraphics);
        for (Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void drawCenteredImage(GuiGraphics guiGraphics) {
        if (this.images == null || this.images.isEmpty())
            return;
        UUID imageID = this.images.get(this.index);
        if (imageID == null)
            return;

        int boxWidth = (int) (this.width * 0.8f);
        int boxHeight = (int) (this.height * 0.8f);
        int x = (this.width - boxWidth) / 2;
        int y = (this.height - boxHeight) / 2;

        MessageWidget.drawImage(guiGraphics, Minecraft.getInstance(), x, y, boxWidth, boxHeight, 100f, imageID);
    }

    @Override
    public void onClose() {
        super.onClose(); // Call the original onClose to handle cleanup
        //? if >=1.20.5 {
        PacketDistributor.sendToServer(new CrazyPhoneAlbumClosedMessage());
        //? } else {
        /*PacketDistributor.SERVER.noArg().send(new CrazyPhoneAlbumClosedMessage());
        *///?}
    }
}
