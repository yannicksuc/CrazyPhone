package fr.lordfinn.crazyphone.client.gui;

import net.neoforged.neoforge.network.PacketDistributor;
import fr.lordfinn.crazyphone.utils.NetworkAccess;

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
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneIncomingCallScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneInCallScreenMenu;

import java.util.List;
import java.util.function.Consumer;

/** Callee-side "Incoming call" screen, shown while ringing and not yet answered - distinct from both the
 * caller-side Calling screen and the active-call InCall screen, with its own Accept/Decline choice instead
 * of answering automatically the instant the phone is used. The ringtone itself isn't tied to this screen -
 * see CallRingtoneManager, which plays it as long as the player actually carries the ringing phone, whether
 * or not this screen (or any screen) is open. Shows the caller's live bust preview (see CallBustPreview),
 * same technique as the InCall screen's participant grid, just a single centered bust here. */
public class CrazyPhoneIncomingCallScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneIncomingCallScreenMenu> {
    private static final int BUST_LEFT = 8;
    private static final int BUST_WIDTH = 106;
    private static final int BUST_TOP = 44;
    private static final int BUST_BOTTOM = 138;
    private static final int CELL_BACKGROUND_COLOR = 0xFF2B2B2B;

    private final Consumer<CrazyPhoneCallStateSyncPacket> callStateListener = this::onCallStateChanged;
    private final CallBustPreview bustPreview = new CallBustPreview();
    private Button button_accept;
    private Button button_decline;

    public CrazyPhoneIncomingCallScreenScreen(CrazyPhoneIncomingCallScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public java.util.HashMap<String, Object> getWidgets() {
        return CrazyPhoneIncomingCallScreenMenu.guistate;
    }

    @Override
    public void init() {
        super.init();
        setBackButtonActive(false);
        setHomeButtonActive(false);
        setLockButtonActive(false);
        ClientCallState.setListener(callStateListener);
        for (CrazyPhoneInCallScreenMenu.CallParticipant caller : menu.getParticipants())
            bustPreview.ensure(caller.id(), caller.name(), caller.helmet(), caller.chestplate(), caller.leggings(), caller.boots());

        button_accept = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_incoming_call_screen.button_accept"), e -> {
            //? if >=1.20.5 {
            /*NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.ANSWER, menu.getConversationId()));
            *///? } else {
            PacketDistributor.SERVER.noArg().send(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.ANSWER, menu.getConversationId()));
            //?}
        }).bounds(this.leftPos + 8, this.topPos + 158, 52, 14).build();
        this.addRenderableWidget(button_accept);

        button_decline = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_incoming_call_screen.button_decline"), e -> {
            //? if >=1.20.5 {
            /*NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
            *///? } else {
            PacketDistributor.SERVER.noArg().send(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
            //?}
        }).bounds(this.leftPos + 62, this.topPos + 158, 52, 14).build();
        this.addRenderableWidget(button_decline);
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
        // Missed (caller cancelled, or the ring timeout expired) - nothing left to accept/decline.
        if (packet.state() == CrazyPhoneCallStateSyncPacket.State.ENDED) {
            if (this.minecraft != null && this.minecraft.player != null)
                this.minecraft.player.closeContainer();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_incoming_call_screen.title"));
        renderCallerBust(guiGraphics);
        guiGraphics.drawCenteredString(this.font, Component.literal(menu.getDisplayTitle())
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                this.leftPos + 61, this.topPos + 143, 0xFFFFFF);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /** A single big square bust of the caller, centered in the band between the header and the name/buttons -
     * same live pose/head-tracking preview as the InCall screen's grid, just one cell instead of several. */
    private void renderCallerBust(GuiGraphics guiGraphics) {
        List<CrazyPhoneInCallScreenMenu.CallParticipant> callers = menu.getParticipants();
        if (callers.isEmpty())
            return;

        int cellSize = Math.min(BUST_WIDTH, BUST_BOTTOM - BUST_TOP);
        int cellX = this.leftPos + BUST_LEFT + (BUST_WIDTH - cellSize) / 2;
        int cellY = this.topPos + BUST_TOP;
        guiGraphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, CELL_BACKGROUND_COLOR);
        bustPreview.render(guiGraphics, callers.get(0).id(), cellX, cellY, cellSize, CallBustPreview.CropMode.BUST, false);
    }
}
