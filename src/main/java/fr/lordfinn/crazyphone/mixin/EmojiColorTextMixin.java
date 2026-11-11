package fr.lordfinn.crazyphone.mixin;

import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import fr.lordfinn.crazyphone.client.EmojiGlyphs;

/**
 * Emojis keep their own colours everywhere text is drawn (message field, bubbles, names, chat, tooltips...).
 * The Pixel Twemoji glyphs are colour bitmaps, but the game multiplies every glyph by the text colour, so a
 * grey, black or yellow text turned its emojis grey, black or yellow. Each emoji character is drawn white
 * (untinted) instead, whatever the colour of the text around it. Plain characters are left untouched.
 *
 * The one place every glyph goes through: Font's per-character sink (StringRenderOutput before 1.21.10,
 * PreparedTextBuilder since).
 */
//? if <1.21.10 {
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
//?} else {
/*@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
*///?}
public abstract class EmojiColorTextMixin {
    @ModifyVariable(method = "accept(ILnet/minecraft/network/chat/Style;I)Z", at = @At("HEAD"), argsOnly = true, require = 0)
    private Style crazyphone$emojiKeepsItsColours(Style style, int position, Style original, int codepoint) {
        return EmojiGlyphs.isEmoji(codepoint) ? style.withColor(0xFFFFFF) : style;
    }
}
