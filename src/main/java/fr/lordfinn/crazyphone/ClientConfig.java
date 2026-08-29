package fr.lordfinn.crazyphone;

/**
 * Purely local rendering preferences - never synced, never gameplay-relevant, so this is a plain static-field
 * holder rather than a real NeoForge ModConfigSpec/ModConfig.Type.CLIENT registration: that route was tried
 * first, but a CLIENT-type config's load lifecycle depends on a real client bootstrap sequence that isn't
 * guaranteed even when FMLLoader reports Dist.CLIENT (the :1.21.1:test JUnit harness does, since its
 * classpath includes client jars, but never actually runs one) - it crashed mod-loading there with "Cannot
 * get config value before config is loaded" regardless of dist-gating the registration call. Since the user
 * only asked for a boolean, not TOML persistence/operator-configurability, a plain field sidesteps the whole
 * problem while still being genuinely client-side.
 *
 * The actual preview pixel size a "pixelated" choice here falls back to is server-side
 * (Config#photoThumbnailPixelHeight) - these only control which resolution (FabricPictureCache's THUMBNAIL
 * vs FULL) each render path asks for, not how coarse THUMBNAIL itself is.
 */
public final class ClientConfig {
    private ClientConfig() {
    }

    public static boolean itemPreviewPixelated = true;
    public static boolean phonePhotoListPixelated = false;
}
