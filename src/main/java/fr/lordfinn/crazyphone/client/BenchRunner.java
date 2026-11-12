package fr.lordfinn.crazyphone.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import fr.lordfinn.crazyphone.client.gui.CrazyPhoneConversationScreen;
import fr.lordfinn.crazyphone.client.gui.CrazyphoneHomeScreenScreen;
import fr.lordfinn.crazyphone.client.gui.PhoneScale;

/**
 * Dev only UI test bench (scripts/ui-bench): when the dev client is started with the CRAZYPHONE_BENCH environment
 * variable (and the dev server with CRAZYPHONE_DEV_COMMANDS=1), plays one fixed scenario once in the world: a fresh
 * registered phone with test contacts, the home screen, a conversation in each state of its send bar, the emoji
 * drawer, a few hovers (fake mouse, the real one is never moved), a coloured chat line with emojis and the phone in
 * hand, taking a screenshot after each step (screenshots/bench_<step>.png). It then writes screenshots/bench_report.txt,
 * which says for each fragile hook (the ones that quietly do nothing when their target is not found) whether it ran.
 *
 * Inert without the variable: tick() returns at once. Driven from LiveScreenClient's client tick, which every loader has.
 */
public final class BenchRunner {
    private static final boolean ENABLED = System.getenv("CRAZYPHONE_BENCH") != null;
    private static final int STEP_TICKS = 20;

    private record Step(String name, Runnable action, String shot) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static int index = -1;
    private static int wait;
    private static boolean done;
    private static String number;

    /** Set by the hooks themselves the first time they run (EmojiColorTextMixin, PhoneScaleGuiGraphicsMixin,
     * MouseHandlerCursorMixin). */
    public static volatile boolean emojiColourHookRan, guiItemHookRan, devMouseHookRan;
    /** Dev only fake mouse position in window pixels (BenchMouseMixin), also set by /phoneui hover. Null = off. */
    public static volatile double[] devMouse;

    private BenchRunner() {
    }

    public static void tick() {
        if (!ENABLED || done)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;
        try {
            if (index < 0) {
                buildScenario();
                index = 0;
                wait = 60; // let the (flat, tiny) bench world settle after joining
                ClientPrefs.markLaunchSplashShown();
                return;
            }
            if (--wait > 0)
                return;
            if (index >= STEPS.size()) {
                finish(mc);
                return;
            }
            Step step = STEPS.get(index);
            if (step.shot() != null) {
                // The previous step's action has had its ticks: capture now, then run this step's action.
                shot(mc, step.shot());
            }
            if (step.action() != null)
                step.action().run();
            // The "chat messages can't be verified" toast of an offline server (and any other) would cover the
            // top of the next screenshot.
            CrazyPhoneCaptureMode.clearToasts(mc);
            REPORT.add("step " + step.name() + " ok");
            index++;
            wait = STEP_TICKS;
        } catch (Throwable t) {
            REPORT.add("step " + (index >= 0 && index < STEPS.size() ? STEPS.get(index).name() : "?") + " FAILED: " + t);
            index++;
            wait = STEP_TICKS;
        }
    }

    private static void buildScenario() {
        number = Integer.toString(500 + (int) (Math.random() * 499));
        String conversation = fr.lordfinn.crazyphone.utils.CrazyPhoneHelper.getConversationNumber("201", number);
        hoverOff();
        // Each step: an action, then (on the next step) the screenshot of what it led to.
        add("setup", () -> {
            command("item replace entity @s weapon.mainhand with crazyphone:crazy_phone");
        }, null);
        add("register", () -> command("crazyphone register " + number + " Bench x"), null);
        add("seed", () -> command("crazyphone seed"), null);
        add("home", () -> command("crazyphone open crazyphone_home_screen"), null);
        add("home_hover", () -> hoverHomeMagnifier(), "home");
        add("conversation", () -> {
            hoverOff();
            command("crazyphone open crazy_phone_conversation " + conversation);
        }, "home_hover");
        add("rest", () -> conversation(c -> c.devUnfocus()), null);
        add("rest_hover", () -> conversation(c -> hover(c.devBarIconCenter(2))), "conversation_rest");
        add("typing", () -> {
            hoverOff();
            conversation(c -> c.devTypeMessage("Salut 🌲 test\nDeuxième ligne"));
        }, "conversation_rest_hover");
        add("plus", () -> conversation(c -> c.devTogglePlus()), "conversation_typing");
        add("drawer", () -> conversation(c -> {
            c.devTogglePlus();
            c.toggleEmojiDrawer();
            c.devEmojiTab(1);
        }), "conversation_plus");
        add("flags", () -> conversation(c -> c.devEmojiTab(9)), "drawer_smileys");
        add("send", () -> conversation(c -> {
            c.toggleEmojiDrawer();
            c.devSendMessage();
        }), "drawer_flags");
        add("header_hover", () -> conversation(c -> hover(c.devHeaderSlotCenter(0))), "conversation_sent");
        add("chat", () -> {
            hoverOff();
            Minecraft.getInstance()./*$ mc_set_screen {*/setScreen/*$}*/(null);
            command("tellraw @s {\"text\":\"Test 🌲📞 doré\",\"color\":\"gold\"}");
        }, "conversation_header_hover");
        add("held", null, "chat_and_held_phone");
    }

    private static void add(String name, Runnable action, String shotOfPrevious) {
        STEPS.add(new Step(name, action, shotOfPrevious));
    }

    private static void command(String command) {
        Minecraft.getInstance().player.connection.sendCommand(command);
    }

    private static void conversation(java.util.function.Consumer<CrazyPhoneConversationScreen> action) {
        Screen screen = Minecraft.getInstance()./*$ mc_get_screen {*/screen/*$}*/;
        if (!(screen instanceof CrazyPhoneConversationScreen conversation))
            throw new IllegalStateException("not on a conversation screen: " + (screen == null ? "none" : screen.getClass().getSimpleName()));
        action.accept(conversation);
    }

    private static void hoverHomeMagnifier() {
        Screen screen = Minecraft.getInstance()./*$ mc_get_screen {*/screen/*$}*/;
        if (!(screen instanceof CrazyphoneHomeScreenScreen home))
            throw new IllegalStateException("not on the home screen");
        hover(home.devSizeToggleCenter());
    }

    /** The fake mouse on a point given in the current screen's own coordinates. */
    private static void hover(int[] point) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale() * PhoneScale.factorOf(mc./*$ mc_get_screen {*/screen/*$}*/);
        devMouse = new double[]{(point[0] + 0.5) * scale, (point[1] + 0.5) * scale};
    }

    /** The fake mouse parked in the window's corner, away from the phone: never null during the scenario, or the
     * real mouse (wherever the player left it over the window) would add its own hovers to the screenshots. */
    private static void hoverOff() {
        devMouse = new double[]{2, 2};
    }

    private static void shot(Minecraft mc, String name) {
        String file = "bench_" + name + ".png";
        //? if <1.21.10 {
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, file, mc./*$ mc_main_render_target {*/getMainRenderTarget/*$}*/(), message -> {
        });
        //?} else {
        /*net.minecraft.client.Screenshot.grab(mc.gameDirectory, file, mc./^$ mc_main_render_target {^/getMainRenderTarget/^$}^/(), 1, message -> {
        });
        *///?}
        REPORT.add("shot " + file);
    }

    private static void finish(Minecraft mc) {
        done = true;
        devMouse = null;
        REPORT.add("hook emoji colours: " + (emojiColourHookRan ? "ran" : "DID NOT RUN"));
        //? if <1.21.10 {
        REPORT.add("hook gui item icon: " + (guiItemHookRan ? "ran" : "DID NOT RUN"));
        //?} else {
        /*REPORT.add("hook gui item icon: not used on this version (item models get the display context)");
        *///?}
        REPORT.add("hook fake mouse: " + (devMouseHookRan ? "ran" : "DID NOT RUN"));
        REPORT.add("done");
        try {
            Path dir = mc.gameDirectory.toPath().resolve("screenshots");
            Files.createDirectories(dir);
            Files.write(dir.resolve("bench_report.txt"), REPORT, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }
}
