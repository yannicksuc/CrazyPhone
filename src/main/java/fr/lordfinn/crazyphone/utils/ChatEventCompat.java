package fr.lordfinn.crazyphone.utils;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Single choke point for the HoverEvent/ClickEvent constructions this mod actually uses - both became
 *  sealed interfaces of records in 1.21.10 (HoverEvent.ShowText, ClickEvent.RunCommand,
 *  ClickEvent.CopyToClipboard) instead of a plain class taking an Action enum + payload. Only covers the
 *  three variants this codebase touches (show-text hover, run-command click, copy-to-clipboard click) -
 *  add more here if a new variant is ever needed, rather than constructing HoverEvent/ClickEvent directly
 *  at the call site. */
public final class ChatEventCompat {
    private ChatEventCompat() {
    }

    public static HoverEvent showText(Component text) {
        //? if <1.21.10 {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        //? } else {
        /*return new HoverEvent.ShowText(text);
        *///?}
    }

    public static ClickEvent runCommand(String command) {
        //? if <1.21.10 {
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        //? } else {
        /*return new ClickEvent.RunCommand(command);
        *///?}
    }

    public static ClickEvent copyToClipboard(String value) {
        //? if <1.21.10 {
        return new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, value);
        //? } else {
        /*return new ClickEvent.CopyToClipboard(value);
        *///?}
    }
}
