package fr.lordfinn.crazyphone.client.livescreen;

//? if neoforge {
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
//?}
//? if legacyforge {
/*import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
*///?}
//? if fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
*///?}

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.network.chat.Component;

import fr.lordfinn.crazyphone.ClientConfig;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Client-only {@code /phonescreen} command for the two client options of the live phone screen:
 * {@code /phonescreen own <true|false>}, {@code /phonescreen others <true|false>}, {@code /phonescreen}
 * (shows both). Saved right away to config/crazyphone-client.properties (see LiveScreenClient).
 * The tree is written once, generic over the command source, and only the registration differs per loader.
 */
//? if neoforge || legacyforge {
@EventBusSubscriber(value = Dist.CLIENT)
//?}
public final class LiveScreenCommand {
    private LiveScreenCommand() {
    }

    static <S> LiteralArgumentBuilder<S> tree(BiConsumer<S, Component> reply) {
        return LiteralArgumentBuilder.<S>literal("phonescreen")
                .executes(ctx -> {
                    show(ctx.getSource(), reply);
                    return 1;
                })
                .then(option("own", v -> ClientConfig.liveScreenOwnPhone = v, reply))
                .then(option("others", v -> ClientConfig.liveScreenOtherPhones = v, reply));
    }

    private static <S> LiteralArgumentBuilder<S> option(String name, Consumer<Boolean> setter, BiConsumer<S, Component> reply) {
        return LiteralArgumentBuilder.<S>literal(name)
                .then(RequiredArgumentBuilder.<S, Boolean>argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            setter.accept(BoolArgumentType.getBool(ctx, "enabled"));
                            LiveScreenClient.saveConfig();
                            LiveScreenMesh.clearCache();
                            show(ctx.getSource(), reply);
                            return 1;
                        }));
    }

    private static <S> void show(S source, BiConsumer<S, Component> reply) {
        reply.accept(source, Component.translatable("command.crazyphone.phonescreen.own", state(ClientConfig.liveScreenOwnPhone)));
        reply.accept(source, Component.translatable("command.crazyphone.phonescreen.others", state(ClientConfig.liveScreenOtherPhones)));
    }

    private static Component state(boolean on) {
        return Component.translatable(on ? "command.crazyphone.phonescreen.on" : "command.crazyphone.phonescreen.off");
    }

    //? if neoforge || legacyforge {
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(LiveScreenCommand.<CommandSourceStack>tree((source, message) -> source.sendSuccess(() -> message, false)));
    }
    //?}
    //? if fabric && >=1.20.5 {
    /*// Called from CrazyphoneFabricClient#onInitializeClient.
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(LiveScreenCommand.<FabricClientCommandSource>tree(FabricClientCommandSource::sendFeedback)));
    }
    *///?}
}
