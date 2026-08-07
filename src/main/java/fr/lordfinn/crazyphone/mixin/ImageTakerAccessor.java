package fr.lordfinn.crazyphone.mixin;

import de.maxhenkel.camera.ImageTaker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(ImageTaker.class)
public interface ImageTakerAccessor {

    @Accessor("takeScreenshot")
    static boolean getTakeScreenshot() {
        throw new UnsupportedOperationException();
    }

    @Accessor("takeScreenshot")
    static void setTakeScreenshot(boolean value) {
        throw new UnsupportedOperationException();
    }

    @Accessor("uuid")
    static UUID getUuid() {
        throw new UnsupportedOperationException();
    }

    @Accessor("hide")
    static boolean getHide() {
        throw new UnsupportedOperationException();
    }

    @Accessor("hide")
    static void setHide(boolean value) {
        throw new UnsupportedOperationException();
    }
}
