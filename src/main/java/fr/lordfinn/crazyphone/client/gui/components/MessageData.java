package fr.lordfinn.crazyphone.client.gui.components;

import net.minecraft.world.item.ItemStack;

public class MessageData {
    private final int timecode;
    private final String message;
    private final String sender;
    private final ItemStack image;

    public MessageData(int timecode, String message, String sender) {
        this.timecode = timecode;
        this.message = message;
        this.sender = sender;
        this.image = ItemStack.EMPTY;
    }

    public MessageData(int timecode, String message, String sender, ItemStack stack) {
        this.timecode = timecode;
        this.message = message;
        this.sender = sender;
        this.image = stack;
    }

    public int getTimecode() {
        return timecode;
    }

    public String getMessage() {
        return message;
    }

    public String getSender() {
        return sender;
    }

    public ItemStack getImage() {
        return image;
    }
}
