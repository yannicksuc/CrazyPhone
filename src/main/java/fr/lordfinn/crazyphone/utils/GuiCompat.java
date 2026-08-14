package fr.lordfinn.crazyphone.utils;

import net.minecraft.client.gui.GuiGraphics;

/** Single choke point for the 2D GUI matrix-stack calls that changed shape when Mojang split GuiGraphics's
 *  {@code pose()} from a full 3D {@code PoseStack} to a 2D-only {@code Matrix3x2fStack} as of 1.21.10:
 *  {@code pushPose/popPose} were renamed {@code pushMatrix/popMatrix}, and {@code scale/translate} dropped
 *  their third (Z) argument since there's no Z axis left to translate/scale on. Every screen in this mod goes
 *  through this instead of calling {@code guiGraphics.pose()} directly for these four operations, so porting
 *  to a version with yet another rendering-stack shape only means rewriting this one file. */
public final class GuiCompat {
    private GuiCompat() {
    }

    public static void pushPose(GuiGraphics guiGraphics) {
        //? if <1.21.10 {
        guiGraphics.pose().pushPose();
        //? } else {
        /*guiGraphics.pose().pushMatrix();
        *///?}
    }

    public static void popPose(GuiGraphics guiGraphics) {
        //? if <1.21.10 {
        guiGraphics.pose().popPose();
        //? } else {
        /*guiGraphics.pose().popMatrix();
        *///?}
    }

    public static void translate(GuiGraphics guiGraphics, float x, float y) {
        //? if <1.21.10 {
        guiGraphics.pose().translate(x, y, 0);
        //? } else {
        /*guiGraphics.pose().translate(x, y);
        *///?}
    }

    public static void scale(GuiGraphics guiGraphics, float x, float y) {
        //? if <1.21.10 {
        guiGraphics.pose().scale(x, y, 1.0f);
        //? } else {
        /*guiGraphics.pose().scale(x, y);
        *///?}
    }
}
