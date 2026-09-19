package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.utils.NetworkAccess;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;

import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.gui.components.CallBustPreview;
import fr.lordfinn.crazyphone.client.gui.components.CallScreenText;
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
    private static final int CELL_GAP = 3;
    private static final int NAME_MAX_WIDTH = 104;

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
            /*NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
            *///? } else {
            NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
            //?}
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
            /*NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.OPEN_CALL_SCREEN, menu.getConversationId()));
            *///? } else {
            NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.OPEN_CALL_SCREEN, menu.getConversationId()));
            //?}
        } else if (packet.state() == CrazyPhoneCallStateSyncPacket.State.ENDED) {
            if (this.minecraft != null && this.minecraft.player != null)
                this.minecraft.player.closeContainer();
        }
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_calling_screen.title"));
        renderCalleeBust(guiGraphics);
        CallScreenText.drawCenteredOrScrolling(guiGraphics, this.font, Component.literal(menu.getDisplayTitle()),
                this.leftPos + 61, this.topPos + 143, NAME_MAX_WIDTH, 0xFFAAAAAA);
        this.extractTooltip(guiGraphics, mouseX, mouseY);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_calling_screen.title"));
        renderCalleeBust(guiGraphics);
        CallScreenText.drawCenteredOrScrolling(guiGraphics, this.font, Component.literal(menu.getDisplayTitle()),
                this.leftPos + 61, this.topPos + 143, NAME_MAX_WIDTH, 0xFFAAAAAA);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
    //?}

    /** A single big square bust of whoever's being called, centered in the band between the header and the
     * name/cancel button - same technique as the Incoming Call screen's caller bust, just not animated. */
    private void renderCalleeBust(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        List<CrazyPhoneInCallScreenMenu.CallParticipant> callees = menu.getParticipants();
        if (callees.isEmpty())
            return;

        // Every callee, not just the first - same adaptive grid as the InCall screen (1 fills a big square,
        // 2 sit side by side, 4 form a 2x2, ...).
        int n = callees.size();
        int columns = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil((double) n / columns);
        int availHeight = BUST_BOTTOM - BUST_TOP;
        int cellSize = Math.min((BUST_WIDTH - (columns - 1) * CELL_GAP) / columns, (availHeight - (rows - 1) * CELL_GAP) / rows);
        cellSize = Math.max(16, Math.min(BUST_WIDTH, cellSize));
        int gridWidth = columns * cellSize + (columns - 1) * CELL_GAP;
        int gridHeight = rows * cellSize + (rows - 1) * CELL_GAP;
        int startX = this.leftPos + BUST_LEFT + Math.max(0, (BUST_WIDTH - gridWidth) / 2);
        int startY = this.topPos + BUST_TOP + Math.max(0, (availHeight - gridHeight) / 2);
        CallBustPreview.CropMode crop = n == 1 ? CallBustPreview.CropMode.BUST : CallBustPreview.CropMode.FULL_BODY;
        for (int i = 0; i < n; i++) {
            int cellX = startX + (i % columns) * (cellSize + CELL_GAP);
            int cellY = startY + (i / columns) * (cellSize + CELL_GAP);
            guiGraphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, CELL_BACKGROUND_COLOR);
            bustPreview.render(guiGraphics, callees.get(i).id(), cellX, cellY, cellSize, crop, false);
        }
    }
}
