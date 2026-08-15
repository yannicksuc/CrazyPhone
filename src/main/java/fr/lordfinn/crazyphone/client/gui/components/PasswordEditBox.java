package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

//? if <1.21.10 {
@OnlyIn(Dist.CLIENT)
//?}
public class PasswordEditBox extends EditBox {

    public PasswordEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        String valueSave = this.getValue();
        this.setValue("*".repeat(valueSave.length()));
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        this.setValue(valueSave);
    }
}
