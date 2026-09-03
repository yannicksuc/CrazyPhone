package fr.lordfinn.crazyphone.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.PhotoFrameData;
import fr.lordfinn.crazyphone.utils.PhotoItemData;

import java.util.UUID;

/**
 * A photo hung/laid on a block surface - conceptually an item frame, but deliberately NOT a
 * {@code net.minecraft.world.entity.decoration.HangingEntity} subclass: vanilla hanging entities only ever
 * attach to one of the 6 axis directions against a FULL solid block face ({@code HangingEntity#survives()}
 * checks {@code Block#isFaceFull}), which is exactly the restriction this feature is meant to lift ("poser
 * les images sur n'importe quelle surface de bloc... basé sur les géometries du bloc visé" - live request).
 * Plain {@link Entity} instead, with its own placement/attachment/collision logic built from scratch below.
 *
 * Attachment model: {@link #attachPos} + {@link #attachFace} identify the block and face the frame is stuck
 * to (same idea as a hanging entity), but the actual visual/collision offset from that face is computed
 * fresh from the block's own {@code VoxelShape} each time it's needed (render, tick, hitbox) rather than
 * assumed to be a full 0-1 cube - see {@link #computeFaceOffset(Level)}. This is deliberately NOT synced
 * over the network: every client already has the same block state loaded locally (it's what they clicked),
 * so recomputing it client-side from {@link Level#getBlockState} is both correct and free, unlike trying to
 * keep a derived offset in sync.
 */
public class CrazyPhonePhotoFrameEntity extends Entity {
    // 1/8-block granularity - matches PhotoFrameResizeMenu's own step size (see that class's own doc
    // comment for why eighths specifically: fine enough to look smooth, coarse enough that a byte-range
    // synced value comfortably covers the configured max size without needing a wider data type).
    public static final int UNITS_PER_BLOCK = 8;
    public static final int DEFAULT_SIZE_UNITS = UNITS_PER_BLOCK; // 1x1 block
    // Hitbox/visual depth off the attached face, in blocks - "1 pixel" per the live request (1/16 block).
    public static final double DEPTH = 1.0 / 16.0;

    private static final EntityDataAccessor<String> DATA_PHOTO_ID =
            SynchedEntityData.defineId(CrazyPhonePhotoFrameEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_OWNER =
            SynchedEntityData.defineId(CrazyPhonePhotoFrameEntity.class, EntityDataSerializers.STRING);
    // The slot rectangle is stored as 4 independent extents from attachPos along the face's own two in-plane
    // axes ("U" = the width-axis, "V" = the height-axis - same axes the old symmetric width/height used),
    // rather than a single width+height centered on attachPos - "the selection should not always have the
    // blue square in the middle... I should be able to trace a rectangle with the blue square on the bottom
    // left corner for example" (live request). All 4 are >=0; e.g. a 3-block-wide slot with the anchor at
    // its own left edge is negU=0, posU=24.
    private static final EntityDataAccessor<Integer> DATA_NEG_U =
            SynchedEntityData.defineId(CrazyPhonePhotoFrameEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_POS_U =
            SynchedEntityData.defineId(CrazyPhonePhotoFrameEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_NEG_V =
            SynchedEntityData.defineId(CrazyPhonePhotoFrameEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_POS_V =
            SynchedEntityData.defineId(CrazyPhonePhotoFrameEntity.class, EntityDataSerializers.INT);
    // 0-5, Direction#get3DDataValue ordering - which face of attachPos this frame is stuck to. A byte-sized
    // int is plenty; EntityDataSerializers has no dedicated Direction serializer usable pre-1.20.5, so this
    // stays a plain int synced field rather than reusing Direction's own (version-inconsistent) codec.
    private static final EntityDataAccessor<Integer> DATA_FACE =
            SynchedEntityData.defineId(CrazyPhonePhotoFrameEntity.class, EntityDataSerializers.INT);
    // 0-3 quarter turns, clockwise as viewed from in front of the image - a pure visual spin of the photo
    // within its existing slot rectangle (rotate() below), NOT a swap of which world axis width/height map
    // to (that would cascade into the bounding box, the resize grid GUI, and every save format - out of
    // scope for what "add a rotate button" asked for). For a floor/ceiling frame (no natural "up" the way a
    // wall has via gravity) this also doubles as the "directional placement" the live request asked for:
    // tryPlace seeds it from the placing player's own facing instead of always defaulting to 0 - see
    // tryPlace's own comment.
    private static final EntityDataAccessor<Integer> DATA_ROTATION =
            SynchedEntityData.defineId(CrazyPhonePhotoFrameEntity.class, EntityDataSerializers.INT);

    // Not synced - every client already has this block loaded locally (see class doc comment). Set once in
    // the placement constructor / re-derived from DATA_FACE + this entity's own blockPosition() elsewhere
    // (the entity's tracked position already sits at the attach block, offset only visually/collision-wise).
    public BlockPos attachPos = BlockPos.ZERO;

    // Client-only, never synced or saved - the renderer pushes the REAL aspect-fit displayed size here once
    // the photo's texture has actually resolved (see CrazyPhonePhotoFrameRenderer#updateDisplayBounds calls),
    // so the hitbox can shrink to match the visible picture instead of the full resize-slot rectangle ("seulement
    // la taille de l'image réelle compte... pas la taille max" - live request). Left at -1 (meaning "unknown,
    // fall back to the full slot size") until the first successful render, which covers both the server
    // (never has this - falls back permanently, which is fine: see computeBoundingBox's own comment) and the
    // brief window before a client's own texture finishes loading.
    private float displayWidthBlocks = -1f, displayHeightBlocks = -1f;

    public CrazyPhonePhotoFrameEntity(EntityType<? extends CrazyPhonePhotoFrameEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Server-side placement factory - validates the target face has SOME collision geometry (not
     * necessarily a full cube) before ever constructing the entity, mirroring HangingEntity#survives()'s
     * own role but with the fuller-block requirement deliberately dropped. Returns null if the face can't
     * hold a frame (fully empty shape, e.g. air, or already occupied - see {@link #spaceFree}). */
    public static CrazyPhonePhotoFrameEntity tryPlace(Level level, BlockPos clickedPos, Direction face,
                                                        Direction placerFacing, PhotoItemData photoData, PhotoFrameData frameData) {
        BlockState state = level.getBlockState(clickedPos);
        // Any non-empty collision shape on the clicked block counts as attachable - deliberately broader
        // than vanilla item frames (Block#isFaceFull, full-cube-only). A photo can hang off a slab's top,
        // a stair's riser, a fence post's side, anything with real geometry there - see this file's own
        // class doc comment for why this isn't a HangingEntity subclass.
        if (state.getCollisionShape(level, clickedPos).isEmpty())
            return null;
        CrazyPhonePhotoFrameEntity entity = new CrazyPhonePhotoFrameEntity(fr.lordfinn.crazyphone.init.ModEntities.PHOTO_FRAME.get(), level);
        entity.attachPos = clickedPos.immutable();
        entity.entityData.set(DATA_FACE, face.get3DDataValue());
        // Floor/ceiling faces have no natural "up" the way a wall gets one from gravity - "Image placements
        // when on floor and ceiling should be directional" (live request). Seed the initial rotation from
        // the direction the player was actually facing when they placed it, so the image starts oriented
        // away from them instead of at an arbitrary fixed default; wall placements just start at 0 (rotate()
        // is still available afterward either way).
        //
        // UP and DOWN use DIFFERENT corrections against the naive Direction#get2DDataValue() ordering
        // (S=0,W=1,N=2,E=3), calibrated directly from live testing rather than derived from the renderer's
        // own pose-stack math: UP needed only north/south swapped (west/east already correct) - a mirror,
        // not a rotation, i.e. NOT expressible as a uniform +k offset - while DOWN needed BOTH pairs swapped,
        // which IS a uniform 180 rotation. This asymmetry matches applyFaceTransform's own UP/DOWN cases
        // using opposite-signed 90 rotations about the same X axis (Axis.XP.rotationDegrees(-90) vs (90)),
        // which are mirror images of one another in the resulting local U/V frame, not simple rotations of
        // each other.
        if (face.getAxis() == Direction.Axis.Y) {
            int naive = placerFacing.get2DDataValue();
            int rotationIndex = face == Direction.UP ? Math.floorMod(2 - naive, 4) : Math.floorMod(naive + 2, 4);
            entity.entityData.set(DATA_ROTATION, rotationIndex);
        }
        entity.entityData.set(DATA_PHOTO_ID, photoData.photoId().toString());
        entity.entityData.set(DATA_OWNER, photoData.owner());
        entity.setExtentsRaw(frameData.widthUnits() / 2, frameData.widthUnits() - frameData.widthUnits() / 2,
                frameData.heightUnits() / 2, frameData.heightUnits() - frameData.heightUnits() / 2);
        entity.setPos(clickedPos.getX() + 0.5, clickedPos.getY() + 0.5, clickedPos.getZ() + 0.5);
        entity.refreshDimensions();
        if (!spaceFree(level, entity))
            return null;
        return entity;
    }

    private static boolean spaceFree(Level level, CrazyPhonePhotoFrameEntity candidate) {
        return level.getEntities(candidate, candidate.getBoundingBox(), other -> other instanceof CrazyPhonePhotoFrameEntity).isEmpty();
    }

    public UUID photoId() {
        return UUID.fromString(this.entityData.get(DATA_PHOTO_ID));
    }

    public String owner() {
        return this.entityData.get(DATA_OWNER);
    }

    public Direction attachFace() {
        return Direction.from3DDataValue(this.entityData.get(DATA_FACE));
    }

    public int negUUnits() {
        return this.entityData.get(DATA_NEG_U);
    }

    public int posUUnits() {
        return this.entityData.get(DATA_POS_U);
    }

    public int negVUnits() {
        return this.entityData.get(DATA_NEG_V);
    }

    public int posVUnits() {
        return this.entityData.get(DATA_POS_V);
    }

    public float widthBlocks() {
        return widthUnits() / (float) UNITS_PER_BLOCK;
    }

    public float heightBlocks() {
        return heightUnits() / (float) UNITS_PER_BLOCK;
    }

    public int widthUnits() {
        return negUUnits() + posUUnits();
    }

    public int heightUnits() {
        return negVUnits() + posVUnits();
    }

    /** Sets a SYMMETRIC size centered on attachPos, same meaning the old width/height-only model always had -
     * used by the Silk Touch / paper-duplication restore path, where {@link PhotoFrameData} only ever carries
     * a plain width+height (not an off-center anchor position - a re-placed frame reasonably just re-centers,
     * see that record's own doc comment for why this wasn't extended further). Not used by the resize GUI
     * itself anymore - see {@link #setExtents}. */
    public void setSizeUnits(int widthUnits, int heightUnits) {
        setExtents(widthUnits / 2, widthUnits - widthUnits / 2, heightUnits / 2, heightUnits - heightUnits / 2);
    }

    /** The resize grid GUI's own entry point - an arbitrary, possibly off-center rectangle around attachPos.
     * All 4 values are clamped to [0, configured max] individually, then the U and V pairs are each
     * independently clamped so their SUM doesn't exceed the configured max (keeping the overall slot no
     * bigger than {@code maxPhotoFrameSizeBlocks} in either dimension, same ceiling the old symmetric model
     * enforced - only which SIDE of attachPos that size sits on is now free). */
    public void setExtents(int negU, int posU, int negV, int posV) {
        int maxUnits = fr.lordfinn.crazyphone.Config.maxPhotoFrameSizeBlocks * UNITS_PER_BLOCK;
        negU = Math.clamp(negU, 0, maxUnits);
        posU = Math.clamp(posU, 0, Math.max(0, maxUnits - negU));
        negV = Math.clamp(negV, 0, maxUnits);
        posV = Math.clamp(posV, 0, Math.max(0, maxUnits - negV));
        if (negU + posU == 0)
            posU = Math.min(maxUnits, UNITS_PER_BLOCK / 4);
        if (negV + posV == 0)
            posV = Math.min(maxUnits, UNITS_PER_BLOCK / 4);
        setExtentsRaw(negU, posU, negV, posV);
    }

    // Unclamped - only for tryPlace's own already-validated PhotoFrameData/DEFAULT_SIZE_UNITS values, where
    // re-clamping against the config max would be redundant work (and, for a Silk-Touch-preserved size from
    // BEFORE the server's max was ever lowered, would silently shrink it instead of honoring what the player
    // actually had - a deliberate choice, not an oversight).
    private void setExtentsRaw(int negU, int posU, int negV, int posV) {
        this.entityData.set(DATA_NEG_U, negU);
        this.entityData.set(DATA_POS_U, posU);
        this.entityData.set(DATA_NEG_V, negV);
        this.entityData.set(DATA_POS_V, posV);
        this.refreshDimensions();
    }

    /** 0-3 quarter turns, clockwise as viewed from in front - see {@link #DATA_ROTATION}'s own field
     * comment. */
    public int rotation() {
        return this.entityData.get(DATA_ROTATION);
    }

    public void setRotation(int rotation) {
        this.entityData.set(DATA_ROTATION, Math.floorMod(rotation, 4));
    }

    public void rotate() {
        setRotation(rotation() + 1);
    }

    /** Called client-side only, from the renderer, once a frame's texture has actually resolved - narrows
     * this entity's own hitbox down to the real, aspect-fit displayed picture (see
     * {@link #displayWidthBlocks}'s own field comment) instead of the full resize-slot rectangle. A no-op
     * (skips the box rebuild) when the size hasn't actually changed, since this is called every render
     * frame. */
    public void updateDisplayBounds(float displayWidthBlocks, float displayHeightBlocks) {
        if (this.displayWidthBlocks == displayWidthBlocks && this.displayHeightBlocks == displayHeightBlocks)
            return;
        this.displayWidthBlocks = displayWidthBlocks;
        this.displayHeightBlocks = displayHeightBlocks;
        this.refreshDimensions();
    }

    // True for the two horizontal-face cases (floor/ceiling) - the ground-placed "1px deep, brown border
    // and background" visual treatment from the live request applies to these, not to wall-mounted frames.
    public boolean isFloorOrCeiling() {
        return attachFace().getAxis() == Direction.Axis.Y;
    }

    /** Outward-facing offset from attachPos's own block-space origin (0,0,0 corner) to where this frame's
     * OWN visible face should sit, computed from the clicked block's real shape in the attach direction -
     * e.g. a slab's top face sits at y=0.5, not y=1.0, so a frame placed on TOP of a bottom slab sits flush
     * against it instead of floating half a block above. Falls back to a full 0/1 cube bound (vanilla
     * item-frame-equivalent) for any block whose shape can't be read in that direction (e.g. air itself,
     * though tryPlace already rejects that case before an entity ever exists). */
    public double computeFaceOffset(Level level) {
        BlockState state = level.getBlockState(attachPos);
        Direction face = attachFace();
        // getCollisionShape(), not getShape() (the render/pick outline) - an earlier attempt at fixing a
        // punch-through-to-the-block-behind bug switched this to getShape() on the theory that it'd match
        // the engine's own block-ray more closely, but live testing showed it made the hitbox land wildly
        // off-position on ordinary blocks instead (wall AND ceiling placements both "way off", not just the
        // narrow outline-vs-collision-shape mismatch it was meant to fix) - reverted back to the
        // known-good collision shape pending a more careful, non-live-blocking investigation of the
        // original (much narrower) bug. The [0,1] clamp below is a defensive floor/ceiling against any
        // single block's shape producing a wild bounds() value, regardless of which shape source is used.
        VoxelShape shape = state.getCollisionShape(level, attachPos);
        if (shape.isEmpty())
            return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
        AABB bounds = shape.bounds();
        double raw = switch (face) {
            case DOWN -> bounds.minY;
            case UP -> bounds.maxY;
            case NORTH -> bounds.minZ;
            case SOUTH -> bounds.maxZ;
            case WEST -> bounds.minX;
            case EAST -> bounds.maxX;
        };
        return Math.clamp(raw, 0.0, 1.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PHOTO_ID, "");
        builder.define(DATA_OWNER, "");
        builder.define(DATA_NEG_U, DEFAULT_SIZE_UNITS / 2);
        builder.define(DATA_POS_U, DEFAULT_SIZE_UNITS - DEFAULT_SIZE_UNITS / 2);
        builder.define(DATA_NEG_V, DEFAULT_SIZE_UNITS / 2);
        builder.define(DATA_POS_V, DEFAULT_SIZE_UNITS - DEFAULT_SIZE_UNITS / 2);
        builder.define(DATA_FACE, Direction.NORTH.get3DDataValue());
        builder.define(DATA_ROTATION, 0);
    }

    @Override
    public void tick() {
        // Wall/ceiling/floor decor, not a physics object - no gravity, no drift, matches HangingEntity's
        // own tick() (empty on the server side beyond the "still attached" check vanilla does; this mirrors
        // that check with the broader non-full-face allowance instead of vanilla's own isFaceFull one).
        if (!this.level().isClientSide() && !stillAttached()) {
            killAndDrop(dropStack());
            playBreakSound();
        }
    }

    // Entity#kill()/spawnAtLocation(ItemStack) both gained an explicit ServerLevel parameter on >=26 (see
    // this method's own two version-specific bodies below) - this.level() is always the same ServerLevel
    // instance at every call site that reaches here (both are only ever invoked after an isClientSide()
    // guard), so the cast is safe.
    //? if <26 {
    private void killAndDrop(ItemStack stack) {
        this.kill();
        this.spawnAtLocation(stack);
    }
    //?}
    //? if >=26 {
    /*private void killAndDrop(ItemStack stack) {
        net.minecraft.server.level.ServerLevel serverLevel = (net.minecraft.server.level.ServerLevel) this.level();
        this.kill(serverLevel);
        this.spawnAtLocation(serverLevel, stack);
    }
    *///?}

    private boolean stillAttached() {
        return !this.level().getBlockState(attachPos).getCollisionShape(this.level(), attachPos).isEmpty();
    }

    // Entity#getBoundingBox() is final on every version this mod targets - can't override it directly the
    // way an entirely custom hitbox would ideally want. setBoundingBox(...) (called from tryPlace() right
    // after construction, and from refreshDimensions() on every resize) is the actual mechanism instead;
    // getBoundingBox() itself just returns whatever was last set via that, same as any other entity.
    private AABB computeBoundingBox() {
        Level lvl = this.level();
        Direction face = attachFace();
        double faceOffset = lvl != null ? computeFaceOffset(lvl) : (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0);
        double negU = negUUnits() / (double) UNITS_PER_BLOCK, posU = posUUnits() / (double) UNITS_PER_BLOCK;
        double negV = negVUnits() / (double) UNITS_PER_BLOCK, posV = posVUnits() / (double) UNITS_PER_BLOCK;
        // How far the slot rectangle's own geometric center sits from attachPos along each in-plane axis -
        // zero for the old-style symmetric size, nonzero once the anchor sits off-center within the
        // rectangle (see setExtents's own doc comment).
        double centerU = (posU - negU) / 2.0, centerV = (posV - negV) / 2.0;
        // displayWidthBlocks/Height are only ever populated client-side once a texture has actually resolved
        // (see that field's own comment) - the server (and a client before its first render) always falls
        // back to the full slot size below, which is a strict superset of the real picture, so a break-punch
        // there still lands (just possibly slightly more forgiving near the letterboxed margin) rather than
        // ever rejecting a legitimate hit. Aspect-fit letterboxing is always centered WITHIN the slot (never
        // biased toward one edge), so the refined display size shares the exact same centerU/centerV the
        // full slot uses - only the half-extent shrinks, the center never moves.
        double halfU = (displayWidthBlocks >= 0 ? displayWidthBlocks : (negU + posU)) / 2.0;
        double halfV = (displayHeightBlocks >= 0 ? displayHeightBlocks : (negV + posV)) / 2.0;
        double cx = attachPos.getX() + 0.5, cy = attachPos.getY() + 0.5, cz = attachPos.getZ() + 0.5;
        double u0 = centerU - halfU, u1 = centerU + halfU, v0 = centerV - halfV, v1 = centerV + halfV;
        return switch (face) {
            case DOWN -> new AABB(cx + u0, attachPos.getY() + faceOffset - DEPTH, cz + v0, cx + u1, attachPos.getY() + faceOffset, cz + v1);
            case UP -> new AABB(cx + u0, attachPos.getY() + faceOffset, cz + v0, cx + u1, attachPos.getY() + faceOffset + DEPTH, cz + v1);
            case NORTH -> new AABB(cx + u0, cy + v0, attachPos.getZ() + faceOffset - DEPTH, cx + u1, cy + v1, attachPos.getZ() + faceOffset);
            case SOUTH -> new AABB(cx + u0, cy + v0, attachPos.getZ() + faceOffset, cx + u1, cy + v1, attachPos.getZ() + faceOffset + DEPTH);
            case WEST -> new AABB(attachPos.getX() + faceOffset - DEPTH, cy + v0, cz + u0, attachPos.getX() + faceOffset, cy + v1, cz + u1);
            case EAST -> new AABB(attachPos.getX() + faceOffset, cy + v0, cz + u0, attachPos.getX() + faceOffset + DEPTH, cy + v1, cz + u1);
        };
    }

    @Override
    public void refreshDimensions() {
        // Entity's own default refreshDimensions() recenters the bounding box on the entity's tracked
        // position using EntityDimensions - not meaningful for a face-attached, non-cube-shaped hitbox like
        // this one, so this overrides it to just recompute directly from attachPos/face/size instead.
        this.setBoundingBox(computeBoundingBox());
    }

    // Silk Touch check shared by both hurt()/hurtServer() bodies below - not itself version-split, only its
    // TWO call sites' own method signatures differ (Entity#hurt is final and hurtServer(ServerLevel, ...)
    // is the real abstract override point on >=26 - confirmed via the real decompiled Entity.java, not
    // guessed).
    private boolean isSilkTouch(DamageSource source) {
        if (!(source.getEntity() instanceof Player player))
            return false;
        ItemStack tool = player.getItemInHand(InteractionHand.MAIN_HAND);
        // ItemStack#getEnchantmentLevel wants a Holder.Reference specifically on this loader's own
        // mappings (a stricter type than the plain Holder registry lookup below returns) -
        // EnchantmentHelper.getItemEnchantmentLevel takes a plain Holder instead, already the exact
        // pattern SoulboundHandler.java's own Silk Touch check uses (see that class).
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(this.level().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), tool) > 0;
    }

    //? if <26 {
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoved())
            return true;
        boolean silkTouch = isSilkTouch(source);
        killAndDrop(silkTouch ? dropStackWithFrameData() : dropStack());
        playBreakSound();
        return true;
    }

    /** Right-click - opens the resize menu server-side. Actual menu open lives in ScreenMenuUtils
     * (mirrors every other phone screen's own open call), kept out of this class since Entity subclasses in
     * this codebase don't otherwise reach into world/inventory/ package concerns directly. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide())
            fr.lordfinn.crazyphone.utils.ScreenMenuUtils.openPhotoFrameResizeMenu(player, this);
        return InteractionResult.SUCCESS;
    }
    //?}
    //? if >=26 {
    /*@Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level, DamageSource source, float amount) {
        if (this.isRemoved())
            return true;
        boolean silkTouch = isSilkTouch(source);
        killAndDrop(silkTouch ? dropStackWithFrameData() : dropStack());
        playBreakSound();
        return true;
    }

    // interact() gained an explicit Vec3 (the precise clicked point, relative to the entity) parameter on
    // >=26 - confirmed against the real decompiled Entity.java. Not needed for this resize-dialog use case,
    // deliberately unused here.
    @Override
    public InteractionResult interact(Player player, InteractionHand hand, net.minecraft.world.phys.Vec3 location) {
        if (!this.level().isClientSide())
            fr.lordfinn.crazyphone.utils.ScreenMenuUtils.openPhotoFrameResizeMenu(player, this);
        return InteractionResult.SUCCESS;
    }
    *///?}

    private ItemStack dropStack() {
        ItemStack stack = new ItemStack(ModItems.CRAZY_PHONE_PHOTO.get());
        new PhotoItemData(photoId(), owner(), 0).writeTo(stack);
        return stack;
    }

    private ItemStack dropStackWithFrameData() {
        ItemStack stack = dropStack();
        new PhotoFrameData(widthUnits(), heightUnits()).writeTo(stack);
        return stack;
    }

    private void playBreakSound() {
        this.level().playSound(null, this.blockPosition(), SoundEvents.ITEM_FRAME_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    public void playPlaceSound() {
        this.level().playSound(null, this.blockPosition(), SoundEvents.ITEM_FRAME_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    // Entity's save/load NBT hooks moved from plain CompoundTag to a new ValueInput/ValueOutput abstraction
    // on >=26 (confirmed against the real decompiled Entity.java: readAdditionalSaveData(ValueInput) /
    // addAdditionalSaveData(ValueOutput), both now abstract) - ValueOutput#putInt/putString and
    // ValueInput#getIntOr/getStringOr are the same names as CompoundTag's own (and NbtCompat's own
    // wrappers), just a different interface entirely, so the >=26 branch below talks to it directly
    // instead of through NbtCompat (which is CompoundTag-specific).
    //? if <26 {
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        attachPos = new BlockPos(
                fr.lordfinn.crazyphone.utils.NbtCompat.getInt(tag, "AttachX"),
                fr.lordfinn.crazyphone.utils.NbtCompat.getInt(tag, "AttachY"),
                fr.lordfinn.crazyphone.utils.NbtCompat.getInt(tag, "AttachZ"));
        this.entityData.set(DATA_FACE, fr.lordfinn.crazyphone.utils.NbtCompat.getInt(tag, "Face"));
        this.entityData.set(DATA_PHOTO_ID, fr.lordfinn.crazyphone.utils.NbtCompat.getString(tag, "PhotoId"));
        this.entityData.set(DATA_OWNER, fr.lordfinn.crazyphone.utils.NbtCompat.getString(tag, "Owner"));
        this.entityData.set(DATA_NEG_U, fr.lordfinn.crazyphone.utils.NbtCompat.getInt(tag, "NegU", DEFAULT_SIZE_UNITS / 2));
        this.entityData.set(DATA_POS_U, fr.lordfinn.crazyphone.utils.NbtCompat.getInt(tag, "PosU", DEFAULT_SIZE_UNITS / 2));
        this.entityData.set(DATA_NEG_V, fr.lordfinn.crazyphone.utils.NbtCompat.getInt(tag, "NegV", DEFAULT_SIZE_UNITS / 2));
        this.entityData.set(DATA_POS_V, fr.lordfinn.crazyphone.utils.NbtCompat.getInt(tag, "PosV", DEFAULT_SIZE_UNITS / 2));
        this.entityData.set(DATA_ROTATION, fr.lordfinn.crazyphone.utils.NbtCompat.getInt(tag, "Rotation", 0));
        this.refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("AttachX", attachPos.getX());
        tag.putInt("AttachY", attachPos.getY());
        tag.putInt("AttachZ", attachPos.getZ());
        tag.putInt("Face", this.entityData.get(DATA_FACE));
        tag.putString("PhotoId", this.entityData.get(DATA_PHOTO_ID));
        tag.putString("Owner", this.entityData.get(DATA_OWNER));
        tag.putInt("NegU", this.entityData.get(DATA_NEG_U));
        tag.putInt("PosU", this.entityData.get(DATA_POS_U));
        tag.putInt("NegV", this.entityData.get(DATA_NEG_V));
        tag.putInt("PosV", this.entityData.get(DATA_POS_V));
        tag.putInt("Rotation", this.entityData.get(DATA_ROTATION));
    }
    //?}
    //? if >=26 {
    /*@Override
    protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        attachPos = new BlockPos(input.getIntOr("AttachX", 0), input.getIntOr("AttachY", 0), input.getIntOr("AttachZ", 0));
        this.entityData.set(DATA_FACE, input.getIntOr("Face", Direction.NORTH.get3DDataValue()));
        this.entityData.set(DATA_PHOTO_ID, input.getStringOr("PhotoId", ""));
        this.entityData.set(DATA_OWNER, input.getStringOr("Owner", ""));
        this.entityData.set(DATA_NEG_U, input.getIntOr("NegU", DEFAULT_SIZE_UNITS / 2));
        this.entityData.set(DATA_POS_U, input.getIntOr("PosU", DEFAULT_SIZE_UNITS / 2));
        this.entityData.set(DATA_NEG_V, input.getIntOr("NegV", DEFAULT_SIZE_UNITS / 2));
        this.entityData.set(DATA_POS_V, input.getIntOr("PosV", DEFAULT_SIZE_UNITS / 2));
        this.entityData.set(DATA_ROTATION, input.getIntOr("Rotation", 0));
        this.refreshDimensions();
    }

    @Override
    protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        output.putInt("AttachX", attachPos.getX());
        output.putInt("AttachY", attachPos.getY());
        output.putInt("AttachZ", attachPos.getZ());
        output.putInt("Face", this.entityData.get(DATA_FACE));
        output.putString("PhotoId", this.entityData.get(DATA_PHOTO_ID));
        output.putString("Owner", this.entityData.get(DATA_OWNER));
        output.putInt("NegU", this.entityData.get(DATA_NEG_U));
        output.putInt("PosU", this.entityData.get(DATA_POS_U));
        output.putInt("NegV", this.entityData.get(DATA_NEG_V));
        output.putInt("PosV", this.entityData.get(DATA_POS_V));
        output.putInt("Rotation", this.entityData.get(DATA_ROTATION));
    }
    *///?}

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
