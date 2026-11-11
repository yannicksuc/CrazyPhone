package fr.lordfinn.crazyphone.client;

/**
 * Dev only client command {@code /phoneui view <photoId>} / {@code /phoneui edit <photoId>}: opens the photo
 * viewer or the photo editor directly, to look at them in a test game without clicking through the phone.
 * Registered only when the game runs with {@code CRAZYPHONE_DEV_COMMANDS=1}, and only on the NeoForge
 * targets that already have client commands (same boundary as CrazyPhoneSelfieCameraDebugCommand); an empty
 * class everywhere else.
 */
//? if neoforge && >=1.20.5 && <1.21.10 {
/*import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import fr.lordfinn.crazyphone.client.gui.CrazyPhonePhotoEditScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyPhonePhotoViewerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.UUID;

@EventBusSubscriber(value = Dist.CLIENT)
public final class PhoneUiDevCommand {
    private PhoneUiDevCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        if (!"1".equals(System.getenv("CRAZYPHONE_DEV_COMMANDS")))
            return;
        event.getDispatcher().register(LiteralArgumentBuilder.<CommandSourceStack>literal("phoneui")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("view")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("photo", StringArgumentType.word())
                                .executes(ctx -> open(ctx, false))))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("perspective")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("view", StringArgumentType.word())
                                .executes(ctx -> {
                                    String view = StringArgumentType.getString(ctx, "view");
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> mc.options.setCameraType(switch (view) {
                                        case "back" -> net.minecraft.client.CameraType.THIRD_PERSON_BACK;
                                        case "front" -> net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
                                        default -> net.minecraft.client.CameraType.FIRST_PERSON;
                                    }));
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("type")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    // "-" alone empties the field.
                                    String raw = StringArgumentType.getString(ctx, "text");
                                    String text = raw.equals("-") ? "" : raw.replace("\\n", "\n");
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> {
                                        if (mc.screen instanceof fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen conversation)
                                            conversation.devTypeMessage(text);
                                    });
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("send")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> {
                                if (mc.screen instanceof fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen conversation)
                                    conversation.devSendMessage();
                            });
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("hover")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .executes(ctx -> {
                                            fr.lordfinn.crazyphone.client.PhoneCursorStabilizer.devMouse = new double[]{
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "x"),
                                                    com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "y")};
                                            return 1;
                                        })))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off")
                                .executes(ctx -> {
                                    fr.lordfinn.crazyphone.client.PhoneCursorStabilizer.devMouse = null;
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("plus")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> {
                                if (mc.screen instanceof fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen conversation)
                                    conversation.devTogglePlus();
                            });
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("unfocus")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> {
                                if (mc.screen instanceof fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen conversation)
                                    conversation.devUnfocus();
                            });
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("emojitab")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("tab", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    int tab = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "tab");
                                    Minecraft mc = Minecraft.getInstance();
                                    mc.execute(() -> {
                                        if (mc.screen instanceof fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen conversation)
                                            conversation.devEmojiTab(tab);
                                    });
                                    return 1;
                                })))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("emoji")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> {
                                if (mc.screen instanceof fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen conversation)
                                    conversation.toggleEmojiDrawer();
                            });
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("close")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            mc.execute(() -> {
                                if (mc.player != null)
                                    mc.player.closeContainer();
                                mc.setScreen(null);
                            });
                            return 1;
                        }))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("edit")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("photo", StringArgumentType.word())
                                .executes(ctx -> open(ctx, true)))));
    }

    private static int open(CommandContext<CommandSourceStack> ctx, boolean edit) {
        UUID photoId = UUID.fromString(StringArgumentType.getString(ctx, "photo"));
        Minecraft mc = Minecraft.getInstance();
        // After the command's own chat screen closes.
        mc.execute(() -> {
            CrazyPhonePhotoViewerScreen viewer = new CrazyPhonePhotoViewerScreen(photoId, CrazyPhonePhotoViewerScreen.Origin.GALLERY, 0xFFFFFF);
            mc.setScreen(edit ? new CrazyPhonePhotoEditScreen(photoId, viewer) : viewer);
        });
        return 1;
    }
}
*///?} else {
public final class PhoneUiDevCommand {
    private PhoneUiDevCommand() {
    }
}
//?}
