package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
//? if neoforge && <1.21.10 {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
//?}

//? if neoforge && <1.21.10 {
@OnlyIn(Dist.CLIENT)
//?}
public class PasswordEditBox extends EditBox {

    public PasswordEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
    }

    //? if >=26 {
    /*@Override
    public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        String valueSave = this.getValue();
        this.setValue("*".repeat(valueSave.length()));
        super.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
        this.setValue(valueSave);
    }
    *///? } else {
    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        String valueSave = this.getValue();
        this.setValue("*".repeat(valueSave.length()));
        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        this.setValue(valueSave);
    }
    //?}
}
