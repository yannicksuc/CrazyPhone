package fr.lordfinn.crazyphone.client.gui;

//? if legacyforge {
import net.minecraft.resources.ResourceLocation;

/**
 * Real, original Forge 1.20.1 predates vanilla's own WidgetSprites class (added alongside the sprite-based
 * button rendering rework in 1.20.4) - this same-package shim reproduces just the one two-argument
 * constructor and get(boolean,boolean) accessor this mod's buttons actually use, since every one of their
 * ImageButton subclasses already overrides renderWidget with its own manual blit (see
 * fr.lordfinn.crazyphone.utils.GuiCompat#blit) rather than relying on ImageButton's own built-in sprite
 * rendering - the base ImageButton constructor call itself still needs its own legacyforge-specific
 * old-style (x, y, w, h, u, v, yDiffTex, texture, onPress) overload, since that constructor shape (not just
 * this WidgetSprites argument) doesn't exist pre-1.20.4 either.
 */
public final class WidgetSprites {
    private final ResourceLocation enabled;
    private final ResourceLocation hovered;

    public WidgetSprites(ResourceLocation enabled, ResourceLocation hovered) {
        this.enabled = enabled;
        this.hovered = hovered;
    }

    public ResourceLocation get(boolean active, boolean hoveredOrFocused) {
        return hoveredOrFocused ? hovered : enabled;
    }
}
//?}
