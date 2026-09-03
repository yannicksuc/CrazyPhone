package fr.lordfinn.crazyphone.client;

/**
 * Client-side {@code /selfiecamdebug} command - live-edits {@link CrazyPhoneSelfieCameraDebug}'s fields, plus
 * {@link CrazyPhoneSelfieStickPose}'s own arm rotation limits (minx/maxx/miny/maxy) so both can be tuned
 * from the same place.
 * Usage: {@code /selfiecamdebug <reach|pullback|lateral|shoulderdrop|pitchbend|minx|maxx|miny|maxy> <value>},
 * {@code /selfiecamdebug show}.
 *
 * Fabric branch scoped to {@code >=1.20.5 <26} - mirrors CrazyPhonePresentDebugCommand's own boundary
 * (fabric-command-api-v2's ClientCommandManager convenience wrapper is gone on 26.x, and 1.20.1 predates
 * 1.20.5); 1.20.1-fabric goes without this command the same way it already does for /presentdebug.
 */
//? if fabric && >=1.20.5 <26 {
/*import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

public final class CrazyPhoneSelfieCameraDebugCommand {
    private CrazyPhoneSelfieCameraDebugCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("selfiecamdebug")
                        .then(floatField("reach", v -> CrazyPhoneSelfieCameraDebug.reachDistance = v))
                        .then(floatField("pullback", v -> CrazyPhoneSelfieCameraDebug.cameraPullback = v))
                        .then(floatField("lateral", v -> CrazyPhoneSelfieCameraDebug.lateralOffset = v))
                        .then(floatField("shoulderdrop", v -> CrazyPhoneSelfieCameraDebug.shoulderDrop = v))
                        .then(floatField("pitchbend", v -> CrazyPhoneSelfieCameraDebug.pitchBendDeg = v))
                        .then(floatField("minx", v -> CrazyPhoneSelfieStickPose.minX = v))
                        .then(floatField("maxx", v -> CrazyPhoneSelfieStickPose.maxX = v))
                        .then(floatField("miny", v -> CrazyPhoneSelfieStickPose.minY = v))
                        .then(floatField("maxy", v -> CrazyPhoneSelfieStickPose.maxY = v))
                        .then(ClientCommandManager.literal("show")
                                .executes(CrazyPhoneSelfieCameraDebugCommand::feedback))
        ));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> floatField(
            String name, java.util.function.Consumer<Float> setter) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> {
                            setter.accept(FloatArgumentType.getFloat(ctx, "value"));
                            feedback(ctx);
                            return 1;
                        }));
    }

    private static int feedback(com.mojang.brigadier.context.CommandContext<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.literal(CrazyPhoneSelfieCameraDebug.describe()));
        return 1;
    }
}
*///?}
//? if fabric && (<1.20.5 || >=26) {
/*// No command on this Fabric target - see this class's own doc comment for the version boundary.
public final class CrazyPhoneSelfieCameraDebugCommand {
    private CrazyPhoneSelfieCameraDebugCommand() {
    }

    public static void register() {
    }
}
*///?}
//? if neoforge && <1.21.10 {
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}

@EventBusSubscriber
public final class CrazyPhoneSelfieCameraDebugCommand {
    private CrazyPhoneSelfieCameraDebugCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("selfiecamdebug")
                        .then(floatField("reach", v -> CrazyPhoneSelfieCameraDebug.reachDistance = v))
                        .then(floatField("pullback", v -> CrazyPhoneSelfieCameraDebug.cameraPullback = v))
                        .then(floatField("lateral", v -> CrazyPhoneSelfieCameraDebug.lateralOffset = v))
                        .then(floatField("shoulderdrop", v -> CrazyPhoneSelfieCameraDebug.shoulderDrop = v))
                        .then(floatField("pitchbend", v -> CrazyPhoneSelfieCameraDebug.pitchBendDeg = v))
                        .then(floatField("minx", v -> CrazyPhoneSelfieStickPose.minX = v))
                        .then(floatField("maxx", v -> CrazyPhoneSelfieStickPose.maxX = v))
                        .then(floatField("miny", v -> CrazyPhoneSelfieStickPose.minY = v))
                        .then(floatField("maxy", v -> CrazyPhoneSelfieStickPose.maxY = v))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("show")
                                .executes(CrazyPhoneSelfieCameraDebugCommand::feedback))
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> floatField(String name, java.util.function.Consumer<Float> setter) {
        return LiteralArgumentBuilder.<CommandSourceStack>literal(name)
                .then(RequiredArgumentBuilder.<CommandSourceStack, Float>argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> {
                            setter.accept(FloatArgumentType.getFloat(ctx, "value"));
                            feedback(ctx);
                            return 1;
                        }));
    }

    private static int feedback(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(CrazyPhoneSelfieCameraDebug.describe()), false);
        return 1;
    }
}
//?}
//? if neoforge && >=1.21.10 {
/*// No command on this NeoForge target either - CrazyPhoneSelfieCameraMixin itself is <1.21.10-only (see its
// own doc comment), so there's nothing here for this command to tune.
public final class CrazyPhoneSelfieCameraDebugCommand {
    private CrazyPhoneSelfieCameraDebugCommand() {
    }

    public static void register() {
    }
}
*///?}
