package fr.lordfinn.crazyphone;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

//? if neoforge {
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
//?}

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The optional, independently toggleable features of the phone: calls, voice messages, sending images,
 * inserting camera photos, and the mayor election. Each has two independent gates that both have to pass -
 * see {@link #isEnabledFor(ServerPlayer)}:
 * <ul>
 *     <li>a global on/off in {@link Config}, changeable at runtime via {@code /crazyphone feature}</li>
 *     <li>a permission node ({@code crazyphone.feature.<id>}), for restricting it to specific players/groups
 *     via a permission plugin (LuckPerms etc.) - defaults to allowed for everyone if no such plugin is
 *     installed, matching NeoForge's own default permission handler behavior</li>
 * </ul>
 * Neither replaces the other: the global switch is the simple "off for the whole server" case: turning a
 * feature off entirely; the permission node is for servers that want finer-grained control over who gets to
 * use an enabled feature.
 */
public enum FeatureFlag {
    //? if neoforge {
    CALLS("calls", "Voice calls", () -> Config.callsFeatureEnabled, Config::setCallsFeatureEnabled),
    VOICE_MESSAGES("voice_messages", "Voice messages", () -> Config.voiceMessagesFeatureEnabled, Config::setVoiceMessagesFeatureEnabled),
    IMAGES("images", "Sending images", () -> Config.imagesFeatureEnabled, Config::setImagesFeatureEnabled),
    CAMERA("camera", "Camera photo insertion", () -> Config.cameraFeatureEnabled, Config::setCameraFeatureEnabled),
    MAYOR_VOTING("mayor_voting", "Mayor election/voting", () -> Config.mayorElectionFeatureEnabled, Config::setMayorElectionFeatureEnabled);
    //?}
    //? if fabric {
    /*// TODO(#164 follow-up): Config.java is entirely built on NeoForge's ModConfigSpec - Fabric has no
    // built-in equivalent, so this needs its own config system (a JSON file, or a library like Cloth
    // Config) before these can persist. In-memory defaults-only placeholder for now, always enabled,
    // matching Config's own shipped defaults.
    CALLS("calls", "Voice calls", () -> true, v -> {}),
    VOICE_MESSAGES("voice_messages", "Voice messages", () -> true, v -> {}),
    IMAGES("images", "Sending images", () -> true, v -> {}),
    CAMERA("camera", "Camera photo insertion", () -> true, v -> {}),
    MAYOR_VOTING("mayor_voting", "Mayor election/voting", () -> true, v -> {});
    *///?}

    public final String id;
    public final String displayName;
    private final BooleanSupplier globalEnabledGetter;
    private final Consumer<Boolean> globalEnabledSetter;
    //? if neoforge {
    public final PermissionNode<Boolean> permission;
    //?}

    FeatureFlag(String id, String displayName, BooleanSupplier globalEnabledGetter, Consumer<Boolean> globalEnabledSetter) {
        this.id = id;
        this.displayName = displayName;
        this.globalEnabledGetter = globalEnabledGetter;
        this.globalEnabledSetter = globalEnabledSetter;
        //? if neoforge {
        // Allowed by default for everyone - the permission node is an opt-in RESTRICTION for servers running
        // a permission plugin, not an opt-in requirement; a server with no such plugin installed (the common
        // case) should see every feature work exactly as if this system didn't exist, gated only by the
        // simpler global switch above.
        this.permission = new PermissionNode<>(Crazyphone.MODID, "feature." + id, PermissionTypes.BOOLEAN,
                (player, playerUUID, context) -> true);
        //?}
    }

    public boolean isGloballyEnabled() {
        return globalEnabledGetter.getAsBoolean();
    }

    public void setGloballyEnabled(boolean enabled) {
        globalEnabledSetter.accept(enabled);
    }

    /** The one check every feature's actual entry point (packet handler, procedure, etc.) should gate on. */
    public boolean isEnabledFor(ServerPlayer player) {
        //? if neoforge {
        return isGloballyEnabled() && PermissionAPI.getPermission(player, permission);
        //?}
        //? if fabric {
        /*// TODO(#162): per-player permission-node gating (LuckPerms Fabric API or similar) - global switch
        // only for now, matching NeoForge's own "no permission plugin installed" default behavior.
        return isGloballyEnabled();
        *///?}
    }

    public Component displayNameComponent() {
        return Component.literal(displayName);
    }

    public static @Nullable FeatureFlag byId(String id) {
        for (FeatureFlag flag : values()) {
            if (flag.id.equals(id))
                return flag;
        }
        return null;
    }
}
