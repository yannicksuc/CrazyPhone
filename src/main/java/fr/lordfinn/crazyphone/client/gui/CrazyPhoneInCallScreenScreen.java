package fr.lordfinn.crazyphone.client.gui;

import com.mojang.authlib.GameProfile;

import fr.lordfinn.crazyphone.utils.NetworkAccess;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui./*$ gui_graphics_type {*/GuiGraphics/*$}*/;
//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;

import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.CursorEffects;
import fr.lordfinn.crazyphone.client.MojangProfileLookup;
import fr.lordfinn.crazyphone.client.gui.components.CallBustPreview;
import fr.lordfinn.crazyphone.client.gui.components.CrazyPhoneColors;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.utils.GameProfileCompat;
import fr.lordfinn.crazyphone.voicechat.SvcCallBridge;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneInCallScreenMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
 * Renders every participant - the local player included, as the first tile, so they can see and control
 * what the others see of them - in a grid that grows with the participant count. Each tile is either the
 * participant's "video" (a live full-body 3D bust, see CallBustPreview - the default) or, once they've turned
 * their video off (the 🎥 toggle in each tile's corner, only clickable on the local player's own tile) or when
 * the server has the whole feature off (Config.callVideoEnabled), a plain flat 2D head, the same face icon
 * vanilla's own tab list draws. A yellow border (the mod's usual accent) appears around a tile exactly while
 * SvcCallBridge.isTalking() reports that player as currently speaking, in either mode.
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
     * HEADER_HEIGHT) and the participant grid (GRID_TOP=44) - free real estate for the mute warning below,
     * which doesn't overlap the bust grid or the hangup button. */
    private static final int STATUS_ROW_Y = 34;
    private static final int MUTE_ICON_CENTER_X = 105;

    // Header banner's right edge, matching CrazyPhoneDefaultScreenScreen's own private HEADER_BANNER_RIGHT_X
    // (not exposed to subclasses) - same technique CrazyPhoneMyPhotosScreenScreen's "247/300" counter and
    // CrazyPhoneConversationScreen's call icon use to sit flush against the banner's right edge, on the
    // title's own row, while reserving that space out of the title's available width.
    private static final int HEADER_BANNER_RIGHT_X = 118;
    private static final int HEADER_TITLE_Y = 14;
    // Matches renderHeader's own title color.
    private static final int TIMER_TEXT_COLOR = 0xFF404040;

    // A "muted speaker" emoji (U+1F507, ":mute:") from the bundled Pixel Twemoji font (assets/minecraft/
    // font/default.json) - same technique as CrazyPhonePhotoFrameResizeScreen.ROTATE_ICON. Simple Voice
    // Chat's own mod ships a real mute HUD icon, but its texture lives in the full SVC client mod jar, not
    // in the voicechat-api dependency this project actually compiles against (a compile-only API jar with
    // no bundled assets - confirmed empty of any assets/ entries) - and the real SVC mod isn't present in
    // this project to pull the genuine texture from. This emoji is a stand-in until a real SVC asset can be
    // located and swapped in.
    private static final Component MUTE_ICON = Component.literal("🔇");
    // "movie camera" (U+1F3A5) from the same bundled font - the per-tile video on/off toggle/indicator.
    private static final Component VIDEO_ICON = Component.literal("🎥");
    private static final int VIDEO_ICON_ON_COLOR = 0xFFFFFFFF;
    private static final int VIDEO_ICON_OFF_COLOR = 0xFF9A9A9A;
    private static final int VIDEO_ICON_OFF_BAR_COLOR = 0xFFFF5555;

    /** One tile's on-screen rectangle from the most recent grid layout pass, so mouseClicked and the tooltip
     * hover check can hit-test against exactly what was drawn (the layout depends on the participant count,
     * so it can't be a set of constants). */
    private record CellLayout(UUID id, String name, boolean self, int x, int y, int size) {
    }

    private final Consumer<CrazyPhoneCallStateSyncPacket> callStateListener = this::onCallStateChanged;
    private final CallBustPreview bustPreview = new CallBustPreview();
    /** Backs the flat 2D head of each video-off tile: PlayerInfo resolves the skin itself (default Steve/Alex
     * until loaded) - no fake entity needed, which is the whole point of that mode. */
    private final Map<UUID, PlayerInfo> faceInfos = new ConcurrentHashMap<>();
    private final List<CellLayout> lastLayout = new ArrayList<>();
    /** The OTHER participants, straight from the server (never includes the local player - see
     * ScreenMenuUtils#populateCallScreenBuffer). */
    private List<CrazyPhoneInCallScreenMenu.CallParticipant> participants = List.of();
    /** The local player's own tile, built client-side from the real LocalPlayer - null only if there is no
     * player at all (never in practice while this screen is open). */
    private CrazyPhoneInCallScreenMenu.CallParticipant self;
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
        initSelfTile();
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

    /** The local player as a grid tile: real name, real UUID, a snapshot of what they're wearing right now
     * (same one-time-snapshot semantics as the other participants' armor). Their skin comes from their own
     * live PlayerInfo (already loaded - it's the local player) rather than a Mojang lookup. */
    private void initSelfTile() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            self = null;
            return;
        }
        UUID id = player.getUUID();
        String name = GameProfileCompat.name(player.getGameProfile());
        self = new CrazyPhoneInCallScreenMenu.CallParticipant(id, name,
                player.getItemBySlot(EquipmentSlot.HEAD), player.getItemBySlot(EquipmentSlot.CHEST),
                player.getItemBySlot(EquipmentSlot.LEGS), player.getItemBySlot(EquipmentSlot.FEET));
        PlayerInfo own = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(id) : null;
        faceInfos.put(id, own != null ? own : new PlayerInfo(player.getGameProfile(), false));
        bustPreview.ensure(id, name, self.helmet(), self.chestplate(), self.leggings(), self.boots());
    }

    @Override
    public void onClose() {
        super.onClose();
        ClientCallState.clearListener(callStateListener);
        bustPreview.discardAll();
        faceInfos.clear();
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

    /** Diffs the new participant list against the cached fake-player entities / face lookups: discards
     * whoever left, kicks off an async skin load for any newly-seen UUID (both the 3D bust and the 2D face,
     * so a participant toggling their video mid-call swaps instantly instead of showing nothing while the
     * other representation loads). */
    private void updateParticipants(List<CrazyPhoneInCallScreenMenu.CallParticipant> updated) {
        participants = updated;
        Set<UUID> currentIds = updated.stream().map(CrazyPhoneInCallScreenMenu.CallParticipant::id).collect(Collectors.toSet());
        if (self != null)
            currentIds.add(self.id());
        bustPreview.discardStale(currentIds);
        faceInfos.keySet().retainAll(currentIds);
        for (CrazyPhoneInCallScreenMenu.CallParticipant p : updated) {
            bustPreview.ensure(p.id(), p.name(), p.helmet(), p.chestplate(), p.leggings(), p.boots());
            ensureFace(p.id(), p.name());
        }
    }

    /** Same real-Mojang-profile-first lookup CallBustPreview#ensure does (so the face shows the participant's
     * actual skin even on an offline/cracked dev server), minus the fake entity: a PlayerInfo is all the 2D
     * face needs. No-op if already known or the name is empty. */
    private void ensureFace(UUID id, String name) {
        if (faceInfos.containsKey(id) || name.isEmpty())
            return;
        MojangProfileLookup.lookup(name).thenAccept(realProfile -> {
            GameProfile profile = realProfile != null ? realProfile : new GameProfile(id, name);
            faceInfos.put(id, new PlayerInfo(profile, false));
        });
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_in_call_screen.title"), timerHeaderRightBoundX());
        renderElapsedTimer(guiGraphics);
        renderMuteWarning(guiGraphics);
        renderParticipantGrid(guiGraphics, mouseX, mouseY);
        List<Component> videoTooltip = videoIconTooltipAt(mouseX, mouseY);
        if (videoTooltip != null)
            guiGraphics.setComponentTooltipForNextFrame(this.font, videoTooltip, mouseX, mouseY);
        this.extractTooltip(guiGraphics, mouseX, mouseY);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_in_call_screen.title"), timerHeaderRightBoundX());
        renderElapsedTimer(guiGraphics);
        renderMuteWarning(guiGraphics);
        renderParticipantGrid(guiGraphics, mouseX, mouseY);
        List<Component> videoTooltip = videoIconTooltipAt(mouseX, mouseY);
        if (videoTooltip != null)
            guiGraphics.renderComponentTooltip(this.font, videoTooltip, mouseX, mouseY);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
    //?}

    /** Adaptive grid: columns = ceil(sqrt(n)), rows = ceil(n/columns) - 1 tile fills a single big square, 2
     * sit side by side, 4 form a 2x2 grid, and so on, always centered in the band between the header and the
     * hangup button. The local player is always the first tile. Falls back to the plain combined-name text if
     * there's somehow nothing at all to draw. */
    private void renderParticipantGrid(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int mouseX, int mouseY) {
        List<CrazyPhoneInCallScreenMenu.CallParticipant> tiles = new ArrayList<>();
        if (self != null)
            tiles.add(self);
        tiles.addAll(participants);
        lastLayout.clear();

        int n = tiles.size();
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

        boolean videoFeature = ClientCallState.isVideoFeatureEnabled();
        for (int i = 0; i < n; i++) {
            CrazyPhoneInCallScreenMenu.CallParticipant participant = tiles.get(i);
            boolean isSelf = self != null && i == 0;
            int cellX = startX + (i % columns) * (cellSize + CELL_GAP);
            int cellY = startY + (i / columns) * (cellSize + CELL_GAP);
            lastLayout.add(new CellLayout(participant.id(), participant.name(), isSelf, cellX, cellY, cellSize));

            boolean talking = SvcCallBridge.isTalking(participant.id());
            if (talking) {
                guiGraphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, TALKING_BORDER_COLOR);
                guiGraphics.fill(cellX + BORDER_INSET, cellY + BORDER_INSET, cellX + cellSize - BORDER_INSET, cellY + cellSize - BORDER_INSET, CELL_BACKGROUND_COLOR);
            } else {
                guiGraphics.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, CELL_BACKGROUND_COLOR);
            }

            int inset = talking ? BORDER_INSET : 0;
            boolean video = videoFeature && (isSelf ? ClientCallState.isSelfVideoEnabled() : ClientCallState.isVideoEnabled(participant.id()));
            if (video) {
                if (isSelf)
                    pushLocalLiveState();
                bustPreview.render(guiGraphics, participant.id(), cellX + inset, cellY + inset, cellSize - inset * 2,
                        CallBustPreview.CropMode.FULL_BODY, true);
            } else {
                renderFace(guiGraphics, participant.id(), cellX + inset, cellY + inset, cellSize - inset * 2);
            }
            if (videoFeature)
                renderVideoIcon(guiGraphics, cellX, cellY, cellSize, isSelf, video, mouseX, mouseY);
        }
    }

    /** The local player's own tile animates straight from their own real entity - the server never sends a
     * player their own pose (see CallHeadRotationSync), and it's already right here anyway. Same formula for
     * the walk-animation input as the server side uses for everyone else. */
    private void pushLocalLiveState() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;
        float walkSpeed = Mth.clamp((float) player.getDeltaMovement().horizontalDistance() * 4.0F, 0f, 1f);
        ClientCallState.setLiveState(player.getUUID(), Mth.wrapDegrees(player.getYHeadRot() - player.yBodyRot), player.getXRot(),
                player.getPose().ordinal(), player.isCrouching(), player.isSprinting(), player.isSwimming(), walkSpeed);
    }

    /** The flat "video off" representation: the participant's face (base layer + hat overlay, the same
     * 8x8 skin regions vanilla's tab list draws), centered in the tile with a little breathing room. */
    private void renderFace(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, UUID id, int x, int y, int size) {
        PlayerInfo info = faceInfos.get(id);
        if (info == null)
            return;
        int pad = Math.max(2, size / 8);
        int faceSize = size - pad * 2;
        //? if <1.21.10 {
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(guiGraphics, info.getSkin(), x + pad, y + pad, faceSize);
        //?}
        // >=1.21.10 <26: nothing drawn - a frozen/unmaintained target (see CrazyPhoneCaptureMode's own note),
        // and its vanilla face-drawing helper sits between the two shapes below without being either.
        //? if >=26 {
        /*net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(guiGraphics, info.getSkin(), x + pad, y + pad, faceSize);
        *///?}
    }

    private int videoIconWidth() {
        return this.font.width(VIDEO_ICON);
    }

    private int videoIconX(CellLayout cell) {
        return cell.x() + cell.size() - videoIconWidth() - 2;
    }

    private int videoIconY(CellLayout cell) {
        return cell.y() + 2;
    }

    private boolean isHoveringVideoIcon(CellLayout cell, double mouseX, double mouseY) {
        int iconX = videoIconX(cell);
        int iconY = videoIconY(cell);
        return mouseX >= iconX - 1 && mouseX < iconX + videoIconWidth() + 1
                && mouseY >= iconY - 1 && mouseY < iconY + this.font.lineHeight + 1;
    }

    /** The per-tile 🎥: on the local player's own tile it's the actual toggle (pointer cursor + highlight on
     * hover, click sends TOGGLE_VIDEO); on everyone else's it's a read-only indicator of whether THEIR video is
     * on. Off = greyed out with a red bar through it. */
    private void renderVideoIcon(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics, int cellX, int cellY, int cellSize, boolean isSelf, boolean videoOn, int mouseX, int mouseY) {
        CellLayout cell = lastLayout.get(lastLayout.size() - 1);
        int iconX = videoIconX(cell);
        int iconY = videoIconY(cell);
        int w = videoIconWidth();
        int h = this.font.lineHeight;
        if (isSelf && isHoveringVideoIcon(cell, mouseX, mouseY)) {
            CursorEffects.requestPointerCursor();
            guiGraphics.fill(iconX - 1, iconY - 1, iconX + w + 1, iconY + h + 1, 0x80FFFFFF);
        }
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, VIDEO_ICON, iconX, iconY, videoOn ? VIDEO_ICON_ON_COLOR : VIDEO_ICON_OFF_COLOR, true);
        if (!videoOn)
            guiGraphics.fill(iconX, iconY + h / 2 - 1, iconX + w, iconY + h / 2 + 1, VIDEO_ICON_OFF_BAR_COLOR);
    }

    /** Tooltip for whichever video icon is under the mouse, or null. */
    private List<Component> videoIconTooltipAt(double mouseX, double mouseY) {
        if (!ClientCallState.isVideoFeatureEnabled())
            return null;
        for (CellLayout cell : lastLayout) {
            if (!isHoveringVideoIcon(cell, mouseX, mouseY))
                continue;
            if (cell.self()) {
                boolean on = ClientCallState.isSelfVideoEnabled();
                String titleKey = on ? "gui.crazyphone.crazy_phone_in_call_screen.tooltip_video_on" : "gui.crazyphone.crazy_phone_in_call_screen.tooltip_video_off";
                String loreKey = on ? "gui.crazyphone.crazy_phone_in_call_screen.tooltip_video_on.lore" : "gui.crazyphone.crazy_phone_in_call_screen.tooltip_video_off.lore";
                return List.of(Component.translatable(titleKey), Component.translatable(loreKey).withStyle(ChatFormatting.GRAY));
            }
            if (!ClientCallState.isVideoEnabled(cell.id()))
                return List.of(Component.translatable("gui.crazyphone.crazy_phone_in_call_screen.tooltip_other_video_off", cell.name()));
            return null;
        }
        return null;
    }

    private boolean handleVideoIconClick(double mouseX, double mouseY, int button) {
        if (button != 0 || !ClientCallState.isVideoFeatureEnabled())
            return false;
        for (CellLayout cell : lastLayout) {
            if (cell.self() && isHoveringVideoIcon(cell, mouseX, mouseY)) {
                NetworkAccess.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.TOGGLE_VIDEO, menu.getConversationId()));
                return true;
            }
        }
        return false;
    }

    //? if <26 {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handleVideoIconClick(mouseX, mouseY, button))
            return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
    //?}
    //? if >=26 {
    /*@Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (handleVideoIconClick(event.x(), event.y(), event.button()))
            return true;
        return super.mouseClicked(event, doubleClick);
    }
    *///?}

    /** Live mm:ss chronometer for how long the call has actually been connected - ticks from
     * {@link ClientCallState#getActiveSinceMillis()}, which starts counting the moment THIS client first saw
     * the call reach ACTIVE, never from when it started ringing. Recomputed fresh every frame straight from
     * wall-clock time (no local tick/animation state to maintain), same technique as MessageWidget's own
     * live call-duration text in the chat feed. Renders nothing before the call is actually answered, or if
     * this screen is somehow open for a call that isn't this client's own current one. Sits flush against
     * the header banner's right edge, on the title's own row - same spot as CrazyPhoneMyPhotosScreenScreen's
     * "247/300" counter (live request: "en haut à droite dans la barre jaune") - rather than the old
     * centered spot in the strip below the header. */
    private void renderElapsedTimer(/*$ gui_graphics_type {*/GuiGraphics/*$}*/ guiGraphics) {
        String text = elapsedTimerText();
        if (text == null)
            return;
        Component timerText = Component.literal(text);
        int timerX = this.leftPos + HEADER_BANNER_RIGHT_X - this.font.width(timerText) - 2;
        // Explicit alpha byte (0xFF......) - on >=26, GuiGraphicsExtractor#text silently drops any call
        // whose color has a zero alpha byte instead of treating it as opaque like pre-26's drawString did.
        guiGraphics./*$ gui_draw_string {*/drawString/*$}*/(this.font, timerText, timerX, this.topPos + HEADER_TITLE_Y, TIMER_TEXT_COLOR, false);
    }

    /** Reduces the header's title width to leave room for the elapsed timer, but only once there's actually
     * a timer to make room for - before the call connects the title gets the banner's full width, same as
     * every other screen. */
    private int timerHeaderRightBoundX() {
        String text = elapsedTimerText();
        return text == null ? HEADER_BANNER_RIGHT_X : HEADER_BANNER_RIGHT_X - this.font.width(text) - 4;
    }

    private String elapsedTimerText() {
        if (!menu.getConversationId().equals(ClientCallState.getConversationId()))
            return null;
        long activeSinceMillis = ClientCallState.getActiveSinceMillis();
        if (activeSinceMillis < 0)
            return null;
        long elapsedSeconds = Math.max(0, (System.currentTimeMillis() - activeSinceMillis) / 1000);
        return String.format("%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60);
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
