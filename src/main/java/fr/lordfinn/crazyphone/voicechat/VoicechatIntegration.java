package fr.lordfinn.crazyphone.voicechat;

import net.neoforged.fml.ModList;

/**
 * The only class outside this package allowed to be referenced when Simple Voice Chat (SVC) may or may not
 * be installed. {@link #isAvailable()} never touches an SVC class, so it's always safe to call. Every other
 * class in this package (SvcCallBridge, CrazyPhoneVoicechatPlugin, ...) imports SVC types directly and must
 * only ever be reached from call sites already guarded by {@link #isAvailable()} - the JVM only resolves a
 * class's field/method signatures against SVC's own classes when that class is actually loaded, so keeping
 * those references confined here and behind this guard is what lets the mod load cleanly whether or not SVC
 * is present, without needing a mixin or a required dependency (contrast with the Camera mod integration).
 */
public final class VoicechatIntegration {
    private static final String VOICECHAT_MOD_ID = "voicechat";
    private static Boolean available;

    private VoicechatIntegration() {
    }

    public static boolean isAvailable() {
        if (available == null) {
            available = ModList.get().isLoaded(VOICECHAT_MOD_ID);
        }
        return available;
    }
}
