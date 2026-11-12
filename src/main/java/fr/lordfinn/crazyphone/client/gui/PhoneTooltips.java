package fr.lordfinn.crazyphone.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Long tooltip lines cut to the width vanilla gives its own button tooltips. NeoForge wraps every tooltip to the
 * screen by itself, Fabric does not: there a long description (the phone size toggle's...) ran off the window in
 * one line. Each tooltip the phone draws goes through here, so both loaders show the same thing.
 */
public final class PhoneTooltips {
    public static final int MAX_WIDTH = 170;

    private PhoneTooltips() {
    }

    public static List<Component> wrap(Font font, List<? extends Component> lines) {
        List<Component> out = new ArrayList<>();
        for (Component line : lines) {
            if (font.width(line) <= MAX_WIDTH) {
                out.add(line);
                continue;
            }
            for (FormattedText part : font.getSplitter().splitLines(line, MAX_WIDTH, Style.EMPTY)) {
                // Rebuilt piece by piece so every run keeps its own colour and formatting.
                MutableComponent rebuilt = Component.empty();
                part.visit((style, text) -> {
                    rebuilt.append(Component.literal(text).withStyle(style));
                    return Optional.empty();
                }, Style.EMPTY);
                out.add(rebuilt);
            }
        }
        return out;
    }
}
