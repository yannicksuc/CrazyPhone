package fr.lordfinn.crazyphone.client;

/**
 * Client-side {@code /presentdebug} command - live-edits CrazyPhonePresentDebug's fields so the first-person
 * presenting card's position/size/rotation can be tuned directly from chat instead of a recompile/relaunch
 * round trip each time.
 *
 * Usage: {@code /presentdebug <x|y|z|scale|yawsign|pitchsign|yawoffset|pitchoffset|handx|handy|handz> <value>},
 * {@code /presentdebug flip <true|false>}, {@code /presentdebug show}.
 *
 * NeoForge branch added once the ARM's own grip transform (CrazyPhonePresentHandGripMixin) started needing
 * live tuning too (handX/Y/Z, against the same debug fields the card uses) - RegisterClientCommandsEvent is
 * a stable NeoForge API across every version this project targets (unlike Fabric's own ClientCommandManager
 * convenience wrapper, which needed the version split below), so one branch covers all of them.
 */
//? if fabric && >=1.20.5 <26 {
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
                        .then(floatField("x", () -> CrazyPhonePresentDebug.x, v -> CrazyPhonePresentDebug.x = v))
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
                        .then(floatField("dualx", () -> CrazyPhonePresentDebug.dualX, v -> CrazyPhonePresentDebug.dualX = v))
                        .then(floatField("dualxleft", () -> CrazyPhonePresentDebug.dualXLeft, v -> CrazyPhonePresentDebug.dualXLeft = v))
                        .then(floatField("dualxright", () -> CrazyPhonePresentDebug.dualXRight, v -> CrazyPhonePresentDebug.dualXRight = v))
                        .then(floatField("dualy", () -> CrazyPhonePresentDebug.dualY, v -> CrazyPhonePresentDebug.dualY = v))
                        .then(floatField("dualscale", () -> CrazyPhonePresentDebug.dualScale, v -> CrazyPhonePresentDebug.dualScale = v))
                        .then(floatField("dualleftextra", () -> CrazyPhonePresentDebug.dualLeftExtra, v -> CrazyPhonePresentDebug.dualLeftExtra = v))
                        .then(floatField("dualthirdx", () -> CrazyPhonePresentDebug.dualThirdX, v -> CrazyPhonePresentDebug.dualThirdX = v))
                        .then(floatField("dualthirdy", () -> CrazyPhonePresentDebug.dualThirdY, v -> CrazyPhonePresentDebug.dualThirdY = v))
                        .then(floatField("dualthirdscale", () -> CrazyPhonePresentDebug.dualThirdScale, v -> CrazyPhonePresentDebug.dualThirdScale = v))
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
// fabric-command-api-v2 dropped ClientCommandManager entirely for 26.x (javap-verified against the real
// 26.1.2-resolved jar - ClientCommandRegistrationCallback/FabricClientCommandSource are still there
// unchanged, only the literal(...)/argument(...) convenience wrapper is gone) - its two static methods were
// always thin pass-throughs to brigadier's own LiteralArgumentBuilder.literal(...)/
// RequiredArgumentBuilder.argument(...), used directly here instead.
//? if fabric && >=26 {
/*import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class CrazyPhonePresentDebugCommand {
    private CrazyPhonePresentDebugCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("presentdebug")
                        .then(floatField("x", () -> CrazyPhonePresentDebug.x, v -> CrazyPhonePresentDebug.x = v))
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
                        .then(floatField("dualx", () -> CrazyPhonePresentDebug.dualX, v -> CrazyPhonePresentDebug.dualX = v))
                        .then(floatField("dualxleft", () -> CrazyPhonePresentDebug.dualXLeft, v -> CrazyPhonePresentDebug.dualXLeft = v))
                        .then(floatField("dualxright", () -> CrazyPhonePresentDebug.dualXRight, v -> CrazyPhonePresentDebug.dualXRight = v))
                        .then(floatField("dualy", () -> CrazyPhonePresentDebug.dualY, v -> CrazyPhonePresentDebug.dualY = v))
                        .then(floatField("dualscale", () -> CrazyPhonePresentDebug.dualScale, v -> CrazyPhonePresentDebug.dualScale = v))
                        .then(floatField("dualleftextra", () -> CrazyPhonePresentDebug.dualLeftExtra, v -> CrazyPhonePresentDebug.dualLeftExtra = v))
                        .then(floatField("dualthirdx", () -> CrazyPhonePresentDebug.dualThirdX, v -> CrazyPhonePresentDebug.dualThirdX = v))
                        .then(floatField("dualthirdy", () -> CrazyPhonePresentDebug.dualThirdY, v -> CrazyPhonePresentDebug.dualThirdY = v))
                        .then(floatField("dualthirdscale", () -> CrazyPhonePresentDebug.dualThirdScale, v -> CrazyPhonePresentDebug.dualThirdScale = v))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("flip")
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Boolean>argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            CrazyPhonePresentDebug.flipFrontBack = BoolArgumentType.getBool(ctx, "value");
                                            feedback(ctx);
                                            return 1;
                                        })))
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("show")
                                .executes(CrazyPhonePresentDebugCommand::feedback))
        ));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> floatField(
            String name, java.util.function.Supplier<Float> getter, java.util.function.Consumer<Float> setter) {
        return LiteralArgumentBuilder.<FabricClientCommandSource>literal(name)
                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Float>argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> {
                            setter.accept(FloatArgumentType.getFloat(ctx, "value"));
                            feedback(ctx);
                            return 1;
                        }));
    }

    private static int feedback(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(Component.literal(CrazyPhonePresentDebug.describe()));
        return 1;
    }
}
*///?}
//? if neoforge {
import com.mojang.brigadier.arguments.BoolArgumentType;
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
public final class CrazyPhonePresentDebugCommand {
    private CrazyPhonePresentDebugCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("presentdebug")
                        .then(floatField("x", v -> CrazyPhonePresentDebug.x = v))
                        .then(floatField("y", v -> CrazyPhonePresentDebug.y = v))
                        .then(floatField("z", v -> CrazyPhonePresentDebug.z = v))
                        .then(floatField("scale", v -> CrazyPhonePresentDebug.scale = v))
                        .then(floatField("yawsign", v -> CrazyPhonePresentDebug.yawSign = v))
                        .then(floatField("pitchsign", v -> CrazyPhonePresentDebug.pitchSign = v))
                        .then(floatField("yawoffset", v -> CrazyPhonePresentDebug.yawOffset = v))
                        .then(floatField("pitchoffset", v -> CrazyPhonePresentDebug.pitchOffset = v))
                        .then(floatField("handx", v -> CrazyPhonePresentDebug.handX = v))
                        .then(floatField("handy", v -> CrazyPhonePresentDebug.handY = v))
                        .then(floatField("handz", v -> CrazyPhonePresentDebug.handZ = v))
                        .then(floatField("dualx", v -> CrazyPhonePresentDebug.dualX = v))
                        .then(floatField("dualxleft", v -> CrazyPhonePresentDebug.dualXLeft = v))
                        .then(floatField("dualxright", v -> CrazyPhonePresentDebug.dualXRight = v))
                        .then(floatField("dualy", v -> CrazyPhonePresentDebug.dualY = v))
                        .then(floatField("dualscale", v -> CrazyPhonePresentDebug.dualScale = v))
                        .then(floatField("dualleftextra", v -> CrazyPhonePresentDebug.dualLeftExtra = v))
                        .then(floatField("dualthirdx", v -> CrazyPhonePresentDebug.dualThirdX = v))
                        .then(floatField("dualthirdy", v -> CrazyPhonePresentDebug.dualThirdY = v))
                        .then(floatField("dualthirdscale", v -> CrazyPhonePresentDebug.dualThirdScale = v))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("flip")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            CrazyPhonePresentDebug.flipFrontBack = BoolArgumentType.getBool(ctx, "value");
                                            feedback(ctx);
                                            return 1;
                                        })))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("show")
                                .executes(CrazyPhonePresentDebugCommand::feedback))
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
        ctx.getSource().sendSuccess(() -> Component.literal(CrazyPhonePresentDebug.describe()), false);
        return 1;
    }
}
//?}
