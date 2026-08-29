package fr.lordfinn.crazyphone.client.gui.components;

import com.mojang.authlib.GameProfile;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.client.FakePlayerPreview;
import fr.lordfinn.crazyphone.client.MojangProfileLookup;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live preview of one or more call participants, shared between CrazyPhoneCallingScreenScreen (a still
 * caller-side preview of who's being called), CrazyPhoneIncomingCallScreenScreen (a still bust of the
 * caller), and CrazyPhoneInCallScreenScreen (an animated full-body grid) - so all three stay visually and
 * behaviorally consistent rather than drifting apart as separate copies.
 *
 * Each preview is a synthetic RemotePlayer built from the participant's real Mojang GameProfile (works with
 * no authenticated session at all - see MojangProfileLookup) rather than the real world entity, since a call
 * partner is very often out of render distance or in a different dimension entirely, wearing its real worn
 * armor (a one-time snapshot taken when the participant list arrives - see ScreenMenuUtils#populateCallScreenBuffer -
 * not continuously re-synced, matching every other field here except the animated case's live pose/rotation).
 * The body always faces the camera; when {@code animated}, the head turns to mirror the real player's live
 * head-vs-body deviation (clamped so it can't look unnaturally far around on such a small portrait) and
 * pose/sneak/sprint/swim/walk-animation state mirrors the real player's live actions - see
 * ClientCallState#getLiveState / CallHeadRotationSync. A non-animated preview just shows the model standing
 * neutrally, facing the camera, in whatever pose it was created with.
 */
public final class CallBustPreview {
    public enum CropMode {
        /** Shows the whole model (used by the active, animated InCall grid - the live pose is the point). */
        FULL_BODY(0.50f, 0.95f),
        /** Head + shoulders only, with a little headroom above the head - used by the still calling/incoming
         * call previews, where a full-body pose isn't meaningful since nothing is actually moving yet. */
        BUST(0.75f, 1.70f);

        private final float scaleFactor;
        private final float anchorOffsetFactor;

        CropMode(float scaleFactor, float anchorOffsetFactor) {
            this.scaleFactor = scaleFactor;
            this.anchorOffsetFactor = anchorOffsetFactor;
        }
    }

    /** How far the bust's head is allowed to turn away from its fixed forward-facing body before being
     * clamped - the body deliberately always faces the camera (a design choice, not a bug: see
     * CallHeadRotationSync's javadoc), so an unclamped 1:1 head-yaw mirror could swing the head almost
     * all the way around on a small portrait, which reads as broken rather than lifelike. */
    private static final float MAX_HEAD_YAW_DELTA = 50f;
    private static final float MAX_HEAD_PITCH = 50f;
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final Map<UUID, Player> fakePlayers = new ConcurrentHashMap<>();
    /** Guards the walk-animation drive in {@link #render} so each participant's bust advances exactly once
     * per game tick regardless of how many times render is called within that tick (uncapped framerate
     * would otherwise replay LivingEntity.walkAnimation.update() many times per tick and make legs swing
     * unnaturally fast) - keyed per participant since a grid renders several busts per frame, each with its
     * own fake entity/animation state. Unused for non-animated previews. */
    private final Map<UUID, Integer> lastAnimatedGameTick = new HashMap<>();

    /** Looks up the participant's REAL Mojang profile by name first (works with no login at all - see
     * MojangProfileLookup) so the preview shows their actual skin even when the local connection itself is
     * to an offline/cracked dev server; falls back to a synthetic profile (default Steve/Alex skin) if that
     * name isn't a real account or Mojang's API can't be reached. Also applies the given armor snapshot
     * (helmet/chestplate/leggings/boots, any of which may be {@link ItemStack#EMPTY}) so the preview shows
     * the participant as they actually appear in-game, not just their bare skin. No-op if already cached or
     * name is empty. */
    public void ensure(UUID id, String name, ItemStack helmet, ItemStack chestplate, ItemStack leggings, ItemStack boots) {
        if (fakePlayers.containsKey(id) || name.isEmpty())
            return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null)
            return;
        MojangProfileLookup.lookup(name).thenAccept(realProfile -> {
            GameProfile profile = realProfile != null ? realProfile : new GameProfile(id, name);
            //? if <1.21.10 {
            mc.getSkinManager().getOrLoad(profile).thenAccept(skin -> {
            //? } else {
            /*mc.getSkinManager().get(profile).thenAccept(skin -> {
            *///?}
                RemotePlayer fake = new RemotePlayer(level, profile);
                //? if neoforge {
                fake.refreshDisplayName();
                //?}
                FakePlayerPreview.showAllSkinLayers(fake);
                fake.setItemSlot(EquipmentSlot.HEAD, helmet);
                fake.setItemSlot(EquipmentSlot.CHEST, chestplate);
                fake.setItemSlot(EquipmentSlot.LEGS, leggings);
                fake.setItemSlot(EquipmentSlot.FEET, boots);
                level.addFreshEntity(fake);
                fakePlayers.put(id, fake);
            });
        });
    }

    /** Discards whichever cached fake entities aren't in {@code currentIds} anymore. */
    public void discardStale(Set<UUID> currentIds) {
        fakePlayers.keySet().removeIf(id -> {
            if (currentIds.contains(id))
                return false;
            Player stale = fakePlayers.get(id);
            if (stale != null)
                stale.discard();
            return true;
        });
    }

    /** Discards every cached fake entity - call from the owning screen's onClose. */
    public void discardAll() {
        for (Player fake : fakePlayers.values())
            fake.discard();
        fakePlayers.clear();
    }

    public void render(GuiGraphics guiGraphics, UUID participantId, int cellX, int cellY, int cellSize, CropMode cropMode, boolean animated) {
        Player fake = fakePlayers.get(participantId);
        if (fake == null)
            return;
        LivingEntity entity = fake;

        guiGraphics.enableScissor(cellX, cellY, cellX + cellSize, cellY + cellSize);
        int anchorX = cellX + cellSize / 2;
        int anchorY = cellY + Math.round(cellSize * cropMode.anchorOffsetFactor);
        int scale = Math.round(cellSize * cropMode.scaleFactor);

        ClientCallState.LiveState live = animated ? ClientCallState.getLiveState(participantId) : null;
        float headYawDelta = live != null ? Mth.clamp(live.headYawDelta(), -MAX_HEAD_YAW_DELTA, MAX_HEAD_YAW_DELTA) : 0f;
        float pitch = live != null ? Mth.clamp(live.pitch(), -MAX_HEAD_PITCH, MAX_HEAD_PITCH) : 0f;

        if (live != null) {
            entity.setPose(Pose.values()[live.poseOrdinal()]);
            entity.setShiftKeyDown(live.crouching());
            entity.setSprinting(live.sprinting());
            entity.setSwimming(live.swimming());
        }
        // Advance the walk-animation once per game tick per participant (not per render call) so legs/arms
        // actually swing while the real player is walking/running/swimming instead of standing rigid. Only
        // for animated previews - a still preview should stay perfectly still, not idle-bob.
        if (animated) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                int currentGameTick = (int) mc.level.getGameTime();
                Integer previousTick = lastAnimatedGameTick.put(participantId, currentGameTick);
                if (previousTick == null || previousTick != currentGameTick) {
                    float walkSpeed = live != null ? live.walkAnimationSpeed() : 0f;
                    //? if <1.21.10 {
                    entity.walkAnimation.update(walkSpeed, 0.4f);
                    //? } else {
                    /*entity.walkAnimation.update(walkSpeed, 0.4f, 1.0f);
                    *///?}
                }
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
        fr.lordfinn.crazyphone.utils.GuiCompat.renderEntityInInventory(guiGraphics, anchorX, anchorY, scale, new Vector3f(0, 0, 0), pose, new Quaternionf(), entity);
        entity.yBodyRot = prevBodyRot;
        entity.setYRot(prevYRot);
        entity.setXRot(prevXRot);
        entity.yHeadRotO = prevHeadRotO;
        entity.yHeadRot = prevHeadRot;

        guiGraphics.disableScissor();
    }
}
