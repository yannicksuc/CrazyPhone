package fr.lordfinn.crazyphone.client.gui;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;

import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.gui.components.CallBustPreview;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneCallingScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneInCallScreenMenu;

import java.util.List;
import java.util.function.Consumer;

/** Caller-side "Calling..." screen, shown between starting a call and it being answered. The ringback tone
 * itself isn't tied to this screen - see CallRingtoneManager, which plays it as long as the player actually
 * carries the calling phone, whether or not this screen (or any screen) is open. Shows a still bust preview
 * (see CallBustPreview) of whoever's being called - not animated, since nothing about the call is actually
 * live yet (that starts once answered, on the InCall screen). */
public class CrazyPhoneCallingScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneCallingScreenMenu> {
    private static final int BUST_LEFT = 8;
    private static final int BUST_WIDTH = 106;
    private static final int BUST_TOP = 44;
    private static final int BUST_BOTTOM = 138;
    private static final int CELL_BACKGROUND_COLOR = 0xFF2B2B2B;

    private final Consumer<CrazyPhoneCallStateSyncPacket> callStateListener = this::onCallStateChanged;
    private final CallBustPreview bustPreview = new CallBustPreview();
    private Button button_cancel;

    public CrazyPhoneCallingScreenScreen(CrazyPhoneCallingScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public java.util.HashMap<String, Object> getWidgets() {
        return CrazyPhoneCallingScreenMenu.guistate;
    }

    @Override
    public void init() {
        super.init();
        setBackButtonActive(false);
        setHomeButtonActive(false);
        setLockButtonActive(false);
        ClientCallState.setListener(callStateListener);
        for (CrazyPhoneInCallScreenMenu.CallParticipant callee : menu.getParticipants())
            bustPreview.ensure(callee.id(), callee.name(), callee.helmet(), callee.chestplate(), callee.leggings(), callee.boots());

        button_cancel = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_calling_screen.button_cancel"), e -> {
            //? if >=1.20.5 {
            PacketDistributor.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
            //? } else {
            /*PacketDistributor.SERVER.noArg().send(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
            *///?}
        }).bounds(this.leftPos + 8, this.topPos + 158, 106, 14).build();
        this.addRenderableWidget(button_cancel);
    }

    @Override
    public void onClose() {
        super.onClose();
        ClientCallState.clearListener(callStateListener);
        bustPreview.discardAll();
    }

    private void onCallStateChanged(CrazyPhoneCallStateSyncPacket packet) {
        if (!packet.conversationId().equals(menu.getConversationId()))
            return;
        if (packet.state() == CrazyPhoneCallStateSyncPacket.State.ACTIVE) {
            // Someone answered - hand off to the server to reopen us as the InCall screen.
            //? if >=1.20.5 {
            PacketDistributor.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.OPEN_CALL_SCREEN, menu.getConversationId()));
            //? } else {
            /*PacketDistributor.SERVER.noArg().send(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.OPEN_CALL_SCREEN, menu.getConversationId()));
            *///?}
        } else if (packet.state() == CrazyPhoneCallStateSyncPacket.State.ENDED) {
            if (this.minecraft != null && this.minecraft.player != null)
                this.minecraft.player.closeContainer();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_calling_screen.title"));
        renderCalleeBust(guiGraphics);
        guiGraphics.drawCenteredString(this.font, Component.literal(menu.getDisplayTitle())
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                this.leftPos + 61, this.topPos + 143, 0xFFFFFF);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /** A single big square bust of whoever's being called, centered in the band between the header and the
     * name/cancel button - same technique as the Incoming Call screen's caller bust, just not animated. */
    private void renderCalleeBust(GuiGraphics guiGraphics) {
        List<CrazyPhoneInCallScreenMenu.CallParticipant> callees = menu.getParticipants();
        if (callees.isEmpty())
            return;

        int cellSize = Math.min(BUST_WIDTH, BUST_BOTTOM - BUST_TOP);
        int cellX = this.leftPos + BUST_LEFT + (BUST_WIDTH - cellSize) / 2;
        int cellY = this.topPos + BUST_TOP;
        guiGraphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, CELL_BACKGROUND_COLOR);
        bustPreview.render(guiGraphics, callees.get(0).id(), cellX, cellY, cellSize, CallBustPreview.CropMode.BUST, false);
    }
}
