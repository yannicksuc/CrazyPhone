package fr.lordfinn.crazyphone.client.gui;

import com.mojang.authlib.GameProfile;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;

import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.FakePlayerPreview;
import fr.lordfinn.crazyphone.client.MojangProfileLookup;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Active-call screen. Escape (handled by the base class's keyPressed - closes the container) deliberately
 * does NOT end the call: call state lives in the server-side CallRegistry, entirely decoupled from this
 * menu's lifecycle, so closing this screen just stops looking at it.
 *
 * Renders every OTHER participant (never the local player - the server already excludes them, see
 * CrazyPhoneInCallScreenMenu) as a cropped-square 3D bust, laid out in a grid that grows with the
 * participant count. Each bust is a synthetic RemotePlayer built from the participant's GameProfile via
 * SkinManager - same technique CrazyPhoneContactInfoScreenScreen uses for its full-body preview - rather
 * than the real world entity, since a call partner is very often out of render distance or in a different
 * dimension entirely (that's the whole point of a phone call). A yellow border (the same amber used for
 * photo-album selection) appears around a bust exactly while SvcCallBridge.isTalking() reports that player
 * as currently speaking, and disappears the instant they stop.
 */
public class CrazyPhoneInCallScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneInCallScreenMenu> {
    private static final int TALKING_BORDER_COLOR = 0xFFFFC107; // same amber used for photo-album selection
    private static final int CELL_BACKGROUND_COLOR = 0xFF2B2B2B;
    private static final int BORDER_INSET = 2;
    private static final int GRID_LEFT = 8;
    private static final int GRID_WIDTH = 106;
    private static final int GRID_TOP = 44;
    private static final int GRID_BOTTOM = 154;
    private static final int CELL_GAP = 3;
    private static final int MAX_CELL_SIZE = 48;
    private static final int MIN_CELL_SIZE = 16;

    private final Consumer<CrazyPhoneCallStateSyncPacket> callStateListener = this::onCallStateChanged;
    private final Map<UUID, Player> fakePlayers = new ConcurrentHashMap<>();
    private List<CrazyPhoneInCallScreenMenu.CallParticipant> participants = List.of();
    private Button button_hangup;
    /** Guards the walk-animation drive in {@link #renderBust} so each participant's bust advances exactly
     * once per game tick regardless of how many times render is called within that tick (uncapped framerate
     * would otherwise replay LivingEntity.walkAnimation.update() many times per tick and make legs swing
     * unnaturally fast) - keyed per participant since the grid renders several busts per frame and each has
     * its own fake entity/animation state. */
    private final Map<UUID, Integer> lastAnimatedGameTick = new java.util.HashMap<>();

    public CrazyPhoneInCallScreenScreen(CrazyPhoneInCallScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public java.util.HashMap<String, Object> getWidgets() {
        return CrazyPhoneInCallScreenMenu.guistate;
    }

    @Override
    public void init() {
        super.init();
        setBackButtonActive(false);
        setHomeButtonActive(false);
        setLockButtonActive(false);
        ClientCallState.setListener(callStateListener);
        updateParticipants(menu.getParticipants());

        button_hangup = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_in_call_screen.button_hangup"), e -> {
            PacketDistributor.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
        }).bounds(this.leftPos + 8, this.topPos + 158, 106, 14).build();
        this.addRenderableWidget(button_hangup);
    }

    @Override
    public void onClose() {
        super.onClose();
        ClientCallState.clearListener(callStateListener);
        for (Player fake : fakePlayers.values())
            fake.discard();
        fakePlayers.clear();
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
            List<CrazyPhoneInCallScreenMenu.CallParticipant> updated = new ArrayList<>();
            for (int i = 0; i < packet.participantIds().size(); i++)
                updated.add(new CrazyPhoneInCallScreenMenu.CallParticipant(packet.participantIds().get(i), packet.participantNames().get(i)));
            updateParticipants(updated);
        }
    }

    /** Diffs the new participant list against the cached fake-player entities: discards whoever left,
     * kicks off an async skin load (SkinManager) for any newly-seen UUID. */
    private void updateParticipants(List<CrazyPhoneInCallScreenMenu.CallParticipant> updated) {
        participants = updated;
        Set<UUID> currentIds = updated.stream().map(CrazyPhoneInCallScreenMenu.CallParticipant::id).collect(Collectors.toSet());
        fakePlayers.keySet().removeIf(id -> {
            if (currentIds.contains(id))
                return false;
            Player stale = fakePlayers.get(id);
            if (stale != null)
                stale.discard();
            return true;
        });
        for (CrazyPhoneInCallScreenMenu.CallParticipant p : updated)
            ensureFakePlayer(p.id(), p.name());
    }

    /** Looks up the participant's REAL Mojang profile by name first (works with no login at all - see
     * MojangProfileLookup) so the bust shows their actual skin even when the local connection itself is to
     * an offline/cracked dev server; falls back to a synthetic profile (default Steve/Alex skin) if that
     * name isn't a real account or Mojang's API can't be reached. */
    private void ensureFakePlayer(UUID id, String name) {
        if (fakePlayers.containsKey(id) || name.isEmpty())
            return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null)
            return;
        MojangProfileLookup.lookup(name).thenAccept(realProfile -> {
            GameProfile profile = realProfile != null ? realProfile : new GameProfile(id, name);
            mc.getSkinManager().getOrLoad(profile).thenAccept(skin -> {
                RemotePlayer fake = new RemotePlayer(level, profile);
                fake.refreshDisplayName();
                FakePlayerPreview.showAllSkinLayers(fake);
                level.addFreshEntity(fake);
                fakePlayers.put(id, fake);
            });
        });
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_in_call_screen.title"));
        renderParticipantGrid(guiGraphics);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /** Adaptive grid: columns = ceil(sqrt(n)), rows = ceil(n/columns) - 1 participant fills a single big
     * square, 2 sit side by side, 4 form a 2x2 grid, and so on, always centered in the band between the
     * header and the hangup button. Falls back to the old plain combined-name text if the participant list
     * is momentarily empty (e.g. the brief window before the first sync packet arrives). */
    private void renderParticipantGrid(GuiGraphics guiGraphics) {
        int n = participants.size();
        if (n == 0) {
            guiGraphics.drawCenteredString(this.font, Component.literal(menu.getDisplayTitle())
                            .withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                    this.leftPos + 61, this.topPos + 95, 0xFFFFFF);
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
            Player fake = fakePlayers.get(participant.id());
            if (fake instanceof LivingEntity livingEntity)
                renderBust(guiGraphics, livingEntity, participant.id(), cellX + inset, cellY + inset, cellSize - inset * 2);
        }
    }

    /** Player model is ~1.8 blocks tall; InventoryScreen.renderEntityInInventory's "scale" param is pixels
     * per block, anchored at the model's feet. Earlier versions cropped to a head/shoulders "bust" - once
     * live sneak/swim/sprint/walk-animation state got mirrored (see renderBust), that crop hid the very
     * poses it was meant to show, so this now frames the FULL body instead: scale keeps 1.8 blocks within
     * 90% of the cell height, anchored near the cell's bottom edge (5% margin) so there's a small matching
     * headroom margin above the head. */
    private static final float BUST_SCALE_FACTOR = 0.50f;
    private static final float BUST_ANCHOR_OFFSET_FACTOR = 0.95f;
    /** How far the bust's head is allowed to turn away from its fixed forward-facing body before being
     * clamped - the body deliberately always faces the camera (a design choice, not a bug: see
     * CallHeadRotationSync's javadoc), so an unclamped 1:1 head-yaw mirror could swing the head almost
     * all the way around on a small portrait, which reads as broken rather than lifelike. */
    private static final float MAX_HEAD_YAW_DELTA = 50f;
    private static final float MAX_HEAD_PITCH = 50f;

    /** Cropped-square, full-body live preview of a call participant: the same
     * InventoryScreen.renderEntityInInventory technique CrazyPhoneContactInfoScreenScreen uses for its
     * full-body preview, sized to fit the whole model in the cell (see the factors above) and clipped to a
     * scissor square. The body always faces the camera; the head turns to mirror the real player's live
     * head-vs-body deviation (clamped so it can't look unnaturally far around on such a small portrait), and
     * the pose/sneak/sprint/swim/walk-animation state mirrors the real player's live actions - see
     * ClientCallState#getLiveState / CallHeadRotationSync. */
    private void renderBust(GuiGraphics guiGraphics, LivingEntity entity, UUID participantId, int cellX, int cellY, int cellSize) {
        guiGraphics.enableScissor(cellX, cellY, cellX + cellSize, cellY + cellSize);
        int anchorX = cellX + cellSize / 2;
        int anchorY = cellY + Math.round(cellSize * BUST_ANCHOR_OFFSET_FACTOR);
        int scale = Math.round(cellSize * BUST_SCALE_FACTOR);

        ClientCallState.LiveState live = ClientCallState.getLiveState(participantId);
        float headYawDelta = live != null ? Mth.clamp(live.headYawDelta(), -MAX_HEAD_YAW_DELTA, MAX_HEAD_YAW_DELTA) : 0f;
        float pitch = live != null ? Mth.clamp(live.pitch(), -MAX_HEAD_PITCH, MAX_HEAD_PITCH) : 0f;

        if (live != null) {
            entity.setPose(Pose.values()[live.poseOrdinal()]);
            entity.setShiftKeyDown(live.crouching());
            entity.setSprinting(live.sprinting());
            entity.setSwimming(live.swimming());
        }
        // Advance the walk-animation once per game tick per participant (not per render call - see
        // lastAnimatedGameTick) so legs/arms actually swing while the real player is walking/running/swimming
        // instead of standing rigid.
        if (this.minecraft != null && this.minecraft.level != null) {
            int currentGameTick = (int) this.minecraft.level.getGameTime();
            Integer previousTick = lastAnimatedGameTick.put(participantId, currentGameTick);
            if (previousTick == null || previousTick != currentGameTick) {
                float walkSpeed = live != null ? live.walkAnimationSpeed() : 0f;
                entity.walkAnimation.update(walkSpeed, 0.4f);
            }
        }

        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        float prevBodyRot = entity.yBodyRot, prevYRot = entity.getYRot(), prevXRot = entity.getXRot();
        float prevHeadRotO = entity.yHeadRotO, prevHeadRot = entity.yHeadRot;
        entity.yBodyRot = 180.0F;
        entity.setYRot(180.0F);
        entity.setXRot(pitch);
        entity.yHeadRot = 180.0F + headYawDelta;
        entity.yHeadRotO = entity.yHeadRot;
        InventoryScreen.renderEntityInInventory(guiGraphics, anchorX, anchorY, scale, new Vector3f(0, 0, 0), pose, new Quaternionf(), entity);
        entity.yBodyRot = prevBodyRot;
        entity.setYRot(prevYRot);
        entity.setXRot(prevXRot);
        entity.yHeadRotO = prevHeadRotO;
        entity.yHeadRot = prevHeadRot;

        guiGraphics.disableScissor();
    }
}
