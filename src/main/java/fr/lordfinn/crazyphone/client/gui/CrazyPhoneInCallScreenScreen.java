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
import fr.lordfinn.crazyphone.client.gui.components.CrazyPhoneColors;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.voicechat.SvcCallBridge;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneInCallScreenMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Active-call screen. Escape (handled by the base class's keyPressed - closes the container), and now also
 * the Back/Home buttons (left enabled here, unlike most other CrazyPhoneDefaultScreenScreen subclasses),
 * deliberately do NOT end the call: call state lives in the server-side CallRegistry, entirely decoupled
 * from this menu's lifecycle, so navigating away just stops looking at it - the call keeps running and the
 * conversation screen's call icon (see CrazyPhoneConversationScreen) is how the player gets back to this
 * screen. Only the Lock button stays disabled, since locking mid-call has no meaningful effect here.
 *
 * Renders every OTHER participant (never the local player - the server already excludes them, see
 * CrazyPhoneInCallScreenMenu) as a live full-body bust (see CallBustPreview), laid out in a grid that grows
 * with the participant count. A yellow border (the same amber used for photo-album selection) appears
 * around a bust exactly while SvcCallBridge.isTalking() reports that player as currently speaking, and
 * disappears the instant they stop.
 */
public class CrazyPhoneInCallScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneInCallScreenMenu> {
    private static final int TALKING_BORDER_COLOR = CrazyPhoneColors.ACCENT_YELLOW;
    private static final int CELL_BACKGROUND_COLOR = 0xFF2B2B2B;
    private static final int BORDER_INSET = 2;
    private static final int GRID_LEFT = 8;
    private static final int GRID_WIDTH = 106;
    private static final int GRID_TOP = 44;
    private static final int GRID_BOTTOM = 154;
    private static final int CELL_GAP = 3;
    private static final int MAX_CELL_SIZE = 48;
    private static final int MIN_CELL_SIZE = 16;
    /** The empty strip between the header banner (ends at topPos+27, see CrazyPhoneDefaultScreenScreen's
     * HEADER_HEIGHT) and the participant grid (GRID_TOP=44) - free real estate for the live call timer and
     * mute warning below, neither of which overlaps the bust grid or the hangup button. */
    private static final int STATUS_ROW_Y = 34;
    private static final int TIMER_CENTER_X = 50;
    private static final int MUTE_ICON_CENTER_X = 105;

    // A "muted speaker" emoji (U+1F507, ":mute:") from the bundled Pixel Twemoji font (assets/minecraft/
    // font/default.json) - same technique as CrazyPhonePhotoFrameResizeScreen.ROTATE_ICON. Simple Voice
    // Chat's own mod ships a real mute HUD icon, but its texture lives in the full SVC client mod jar, not
    // in the voicechat-api dependency this project actually compiles against (a compile-only API jar with
    // no bundled assets - confirmed empty of any assets/ entries) - and the real SVC mod isn't present in
    // this project to pull the genuine texture from. This emoji is a stand-in until a real SVC asset can be
    // located and swapped in.
    private static final Component MUTE_ICON = Component.literal("🔇");

    private final Consumer<CrazyPhoneCallStateSyncPacket> callStateListener = this::onCallStateChanged;
    private final CallBustPreview bustPreview = new CallBustPreview();
    private List<CrazyPhoneInCallScreenMenu.CallParticipant> participants = List.of();
    private Button button_hangup;

    public CrazyPhoneInCallScreenScreen(CrazyPhoneInCallScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public java.util.HashMap<String, Object> getWidgets() {
        return CrazyPhoneInCallScreenMenu.guistate;
    }

    @Override
    public void init() {
        super.init();
        setLockButtonActive(false);
        ClientCallState.setListener(callStateListener);
        updateParticipants(menu.getParticipants());

        button_hangup = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_in_call_screen.button_hangup"), e -> {
            //? if >=1.20.5 {
            /*NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
            *///? } else {
            NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
            //?}
        }).bounds(this.leftPos + 8, this.topPos + 158, 106, 14).build();
        this.addRenderableWidget(button_hangup);
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
        if (packet.state() == CrazyPhoneCallStateSyncPacket.State.ENDED) {
            if (this.minecraft != null && this.minecraft.player != null)
                this.minecraft.player.closeContainer();
            return;
        }
        if (packet.state() == CrazyPhoneCallStateSyncPacket.State.ACTIVE) {
            // This resync doesn't carry armor (see CallParticipant's javadoc - it's a one-time snapshot, not
            // continuously re-synced) - carry over whatever was already known for a participant we've seen
            // before rather than losing their armor on every resync; a genuinely new participant just starts
            // bare until the next time they reopen a screen that re-snapshots them.
            Map<UUID, CrazyPhoneInCallScreenMenu.CallParticipant> previouslyKnown = participants.stream()
                    .collect(Collectors.toMap(CrazyPhoneInCallScreenMenu.CallParticipant::id, p -> p));
            List<CrazyPhoneInCallScreenMenu.CallParticipant> updated = new ArrayList<>();
            for (int i = 0; i < packet.participantIds().size(); i++) {
                UUID id = packet.participantIds().get(i);
                String name = packet.participantNames().get(i);
                CrazyPhoneInCallScreenMenu.CallParticipant known = previouslyKnown.get(id);
                updated.add(known != null
                        ? new CrazyPhoneInCallScreenMenu.CallParticipant(id, name, known.helmet(), known.chestplate(), known.leggings(), known.boots())
                        : new CrazyPhoneInCallScreenMenu.CallParticipant(id, name, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
            }
            updateParticipants(updated);
        }
    }

    /** Diffs the new participant list against the cached fake-player entities: discards whoever left,
     * kicks off an async skin load (SkinManager) for any newly-seen UUID. */
    private void updateParticipants(List<CrazyPhoneInCallScreenMenu.CallParticipant> updated) {
        participants = updated;
        Set<UUID> currentIds = updated.stream().map(CrazyPhoneInCallScreenMenu.CallParticipant::id).collect(Collectors.toSet());
        bustPreview.discardStale(currentIds);
        for (CrazyPhoneInCallScreenMenu.CallParticipant p : updated)
            bustPreview.ensure(p.id(), p.name(), p.helmet(), p.chestplate(), p.leggings(), p.boots());
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_in_call_screen.title"));
        renderElapsedTimer(guiGraphics);
        renderMuteWarning(guiGraphics);
        renderParticipantGrid(guiGraphics);
        this.extractTooltip(guiGraphics, mouseX, mouseY);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_in_call_screen.title"));
        renderElapsedTimer(guiGraphics);
        renderMuteWarning(guiGraphics);
        renderParticipantGrid(guiGraphics);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
    //?}

    /** Adaptive grid: columns = ceil(sqrt(n)), rows = ceil(n/columns) - 1 participant fills a single big
     * square, 2 sit side by side, 4 form a 2x2 grid, and so on, always centered in the band between the
     * header and the hangup button. Falls back to the old plain combined-name text if the participant list
     * is momentarily empty (e.g. the brief window before the first sync packet arrives). */
    private void renderParticipantGrid(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        int n = participants.size();
        if (n == 0) {
            guiGraphics./*$ gui_draw_centered_string {*/drawCenteredString/*$}*/(this.font, Component.literal(menu.getDisplayTitle())
                            .withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                    this.leftPos + 61, this.topPos + 95, 0xFFFFFFFF);
            return;
        }

        int columns = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil((double) n / columns);
        int availHeight = GRID_BOTTOM - GRID_TOP;
        int cellSize = Math.min(
                (GRID_WIDTH - (columns - 1) * CELL_GAP) / columns,
                (availHeight - (rows - 1) * CELL_GAP) / rows
        );
        cellSize = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, cellSize));
        int gridWidth = columns * cellSize + (columns - 1) * CELL_GAP;
        int gridHeight = rows * cellSize + (rows - 1) * CELL_GAP;
        int startX = this.leftPos + GRID_LEFT + Math.max(0, (GRID_WIDTH - gridWidth) / 2);
        int startY = this.topPos + GRID_TOP + Math.max(0, (availHeight - gridHeight) / 2);

        for (int i = 0; i < n; i++) {
            CrazyPhoneInCallScreenMenu.CallParticipant participant = participants.get(i);
            int cellX = startX + (i % columns) * (cellSize + CELL_GAP);
            int cellY = startY + (i / columns) * (cellSize + CELL_GAP);

            boolean talking = SvcCallBridge.isTalking(participant.id());
            if (talking) {
                guiGraphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, TALKING_BORDER_COLOR);
                guiGraphics.fill(cellX + BORDER_INSET, cellY + BORDER_INSET, cellX + cellSize - BORDER_INSET, cellY + cellSize - BORDER_INSET, CELL_BACKGROUND_COLOR);
            } else {
                guiGraphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, CELL_BACKGROUND_COLOR);
            }

            int inset = talking ? BORDER_INSET : 0;
            bustPreview.render(guiGraphics, participant.id(), cellX + inset, cellY + inset, cellSize - inset * 2,
                    CallBustPreview.CropMode.FULL_BODY, true);
        }
    }

    /** Live mm:ss chronometer for how long the call has actually been connected - ticks from
     * {@link ClientCallState#getActiveSinceMillis()}, which starts counting the moment THIS client first saw
     * the call reach ACTIVE, never from when it started ringing. Recomputed fresh every frame straight from
     * wall-clock time (no local tick/animation state to maintain), same technique as MessageWidget's own
     * live call-duration text in the chat feed. Renders nothing before the call is actually answered, or if
     * this screen is somehow open for a call that isn't this client's own current one. */
    private void renderElapsedTimer(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        if (!menu.getConversationId().equals(ClientCallState.getConversationId()))
            return;
        long activeSinceMillis = ClientCallState.getActiveSinceMillis();
        if (activeSinceMillis < 0)
            return;
        long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - activeSinceMillis) / 1000);
        String text = String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
        guiGraphics./*$ gui_draw_centered_string {*/drawCenteredString/*$}*/(this.font, Component.literal(text),
                this.leftPos + TIMER_CENTER_X, this.topPos + STATUS_ROW_Y, 0xFFFFFFFF);
    }

    /** Small warning icon shown while the LOCAL player has muted their own microphone in Simple Voice Chat
     * (see {@link SvcCallBridge#isMicMuted()} and {@link #MUTE_ICON}'s own javadoc for why this is a plain
     * emoji glyph rather than a real SVC texture) - purely informational, nothing to click here. */
    private void renderMuteWarning(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        if (!SvcCallBridge.isMicMuted())
            return;
        guiGraphics./*$ gui_draw_centered_string {*/drawCenteredString/*$}*/(this.font, MUTE_ICON,
                this.leftPos + MUTE_ICON_CENTER_X, this.topPos + STATUS_ROW_Y, 0xFFFFFFFF);
    }
}
