package fr.lordfinn.crazyphone.client;

/**
 * Client-side {@code /presentdebug} command - live-edits CrazyPhonePresentDebug's fields so the first-person
 * presenting card's position/size/rotation can be tuned directly from chat instead of a recompile/relaunch
 * round trip each time. Fabric-only for now (Fabric API's client command registration has no NeoForge
 * equivalent used elsewhere in this project yet); NeoForge testing of this same feature still goes through
 * the normal edit-compile-relaunch loop.
 *
 * Usage: {@code /presentdebug <y|z|scale|yawsign|pitchsign|yawoffset|pitchoffset> <value>},
 * {@code /presentdebug flip <true|false>}, {@code /presentdebug show}.
 */
//? if fabric && >=1.20.5 {
/*import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

public final class CrazyPhonePresentDebugCommand {
    private CrazyPhonePresentDebugCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("presentdebug")
                        .then(floatField("y", () -> CrazyPhonePresentDebug.y, v -> CrazyPhonePresentDebug.y = v))
                        .then(floatField("z", () -> CrazyPhonePresentDebug.z, v -> CrazyPhonePresentDebug.z = v))
                        .then(floatField("scale", () -> CrazyPhonePresentDebug.scale, v -> CrazyPhonePresentDebug.scale = v))
                        .then(floatField("yawsign", () -> CrazyPhonePresentDebug.yawSign, v -> CrazyPhonePresentDebug.yawSign = v))
                        .then(floatField("pitchsign", () -> CrazyPhonePresentDebug.pitchSign, v -> CrazyPhonePresentDebug.pitchSign = v))
                        .then(floatField("yawoffset", () -> CrazyPhonePresentDebug.yawOffset, v -> CrazyPhonePresentDebug.yawOffset = v))
                        .then(floatField("pitchoffset", () -> CrazyPhonePresentDebug.pitchOffset, v -> CrazyPhonePresentDebug.pitchOffset = v))
                        .then(floatField("handx", () -> CrazyPhonePresentDebug.handX, v -> CrazyPhonePresentDebug.handX = v))
                        .then(floatField("handy", () -> CrazyPhonePresentDebug.handY, v -> CrazyPhonePresentDebug.handY = v))
                        .then(floatField("handz", () -> CrazyPhonePresentDebug.handZ, v -> CrazyPhonePresentDebug.handZ = v))
                        .then(ClientCommandManager.literal("flip")
                                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            CrazyPhonePresentDebug.flipFrontBack = BoolArgumentType.getBool(ctx, "value");
                                            feedback(ctx);
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("show")
                                .executes(CrazyPhonePresentDebugCommand::feedback))
        ));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> floatField(
            String name, java.util.function.Supplier<Float> getter, java.util.function.Consumer<Float> setter) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> {
                            setter.accept(FloatArgumentType.getFloat(ctx, "value"));
                            feedback(ctx);
                            return 1;
                        }));
    }

    private static int feedback(com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.literal(CrazyPhonePresentDebug.describe()));
        return 1;
    }
}
*///?}
