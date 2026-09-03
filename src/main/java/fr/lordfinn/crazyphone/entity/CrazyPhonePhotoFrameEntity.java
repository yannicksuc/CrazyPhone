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


    public CrazyPhonePhotoFrameEntity(EntityType<? extends CrazyPhonePhotoFrameEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // Two things this entity never actually transmitted to any client, confirmed by reading vanilla's own
    // ClientboundAddEntityPacket/Entity#recreateFromPacket:
    // - attachPos itself: only ever set server-side (tryPlace()) or from disk (readAdditionalSaveData(),
    //   NBT - never sent over the network for a freshly-spawned entity at all). Every client was silently
    //   relying on attachPos still holding its unset BlockPos.ZERO default the whole time this feature has
    //   existed.
    // - negU/posU/negV/posV/face: DO reach the client, but only via a SEPARATE ClientboundSetEntityDataPacket
    //   that lands a moment after the spawn packet, not synchronously with it - onSyncedDataUpdated (see that
    //   override below) corrects the box once it arrives, but the entity briefly exists with default/wrong
    //   geometry in between, which is exactly the kind of gap vanilla's own Painting/ItemFrame close by
    //   embedding their own critical data directly in the spawn packet (getAddEntityPacket/
    //   recreateFromPacket), applied synchronously before the entity is ever added to the world - confirmed
    //   by reading their real decompiled source, not guessed.
    //
    // Packed into the packet's own single "data" int (a VarInt over the wire, but still a real Java int, so
    // 32 bits total): face (3 bits, 0-5), rotation (2 bits, 0-3), then the 4 extents ROUNDED TO WHOLE BLOCKS
    // (6 bits each, 0-63 - the configured max is 32 blocks) rather than their full 1/8-block precision - 4 *
    // 9 bits for full precision wouldn't fit alongside face+rotation in one int. The precise, unrounded
    // values still arrive moments later via the normal entityData sync and correct this automatically
    // (onSyncedDataUpdated fires again); this is only ever the entity's very first, synchronous approximation
    // - and for anything actually placed through the resize grid (whole-block granularity already, see
    // CrazyPhonePhotoFrameResizeScreen's own doc comment) it's not even an approximation, it's exact.
    private static int packSpawnData(Direction face, int rotation, int negUBlocks, int posUBlocks, int negVBlocks, int posVBlocks) {
        return (face.get3DDataValue() & 0b111)
                | ((rotation & 0b11) << 3)
                | ((negUBlocks & 0b111111) << 5)
                | ((posUBlocks & 0b111111) << 11)
                | ((negVBlocks & 0b111111) << 17)
                | ((posVBlocks & 0b111111) << 23);
    }

    private static int roundToBlocks(int units) {
        return Math.round(units / (float) UNITS_PER_BLOCK);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(net.minecraft.server.level.ServerEntity serverEntity) {
        int packed = packSpawnData(attachFace(), rotation(),
                roundToBlocks(negUUnits()), roundToBlocks(posUUnits()), roundToBlocks(negVUnits()), roundToBlocks(posVUnits()));
        return new net.minecraft.network.protocol.game.ClientboundAddEntityPacket(this, serverEntity, packed);
    }

    @Override
    public void recreateFromPacket(net.minecraft.network.protocol.game.ClientboundAddEntityPacket packet) {
        // Sets this entity's own tracked position/rotation/id/uuid as normal first (calls the real,
        // unmodified Entity#setPos() as part of doing so - the same call tryPlace() itself already makes
        // safely, never implicated in any of the rendering breakage documented on tick()'s own comment).
        super.recreateFromPacket(packet);
        // attachPos+0.5 is exactly this entity's own tracked position under the current (still-centered)
        // placement convention, so flooring it back via blockPosition() recovers attachPos exactly, with no
        // need to pack a redundant copy of x/y/z into the data int on top of what the packet already
        // transmits.
        this.attachPos = this.blockPosition();
        int packed = packet.getData();
        this.entityData.set(DATA_FACE, packed & 0b111);
        this.entityData.set(DATA_ROTATION, (packed >> 3) & 0b11);
        this.entityData.set(DATA_NEG_U, ((packed >> 5) & 0b111111) * UNITS_PER_BLOCK);
        this.entityData.set(DATA_POS_U, ((packed >> 11) & 0b111111) * UNITS_PER_BLOCK);
        this.entityData.set(DATA_NEG_V, ((packed >> 17) & 0b111111) * UNITS_PER_BLOCK);
        this.entityData.set(DATA_POS_V, ((packed >> 23) & 0b111111) * UNITS_PER_BLOCK);
        this.refreshDimensions();
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
        // attachPos can be null very early in construction - see computeBoundingBox's own comment.
        BlockPos pos = attachPos != null ? attachPos : BlockPos.containing(this.position());
        BlockState state = level.getBlockState(pos);
        Direction face = attachFace();
        // getCollisionShape(), not getShape() (the render/pick outline) - an earlier attempt at fixing a
        // punch-through-to-the-block-behind bug switched this to getShape() on the theory that it'd match
        // the engine's own block-ray more closely, but live testing showed it made the hitbox land wildly
        // off-position on ordinary blocks instead (wall AND ceiling placements both "way off", not just the
        // narrow outline-vs-collision-shape mismatch it was meant to fix) - reverted back to the
        // known-good collision shape pending a more careful, non-live-blocking investigation of the
        // original (much narrower) bug. The [0,1] clamp below is a defensive floor/ceiling against any
        // single block's shape producing a wild bounds() value, regardless of which shape source is used.
        VoxelShape shape = state.getCollisionShape(level, pos);
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

    // Vanilla's own Entity#onSyncedDataUpdated(EntityDataAccessor) is how Entity itself keeps its bounding
    // box in sync with a changed DATA_POSE (confirmed against the real decompiled Entity.java on both <26
    // and >=26 - same method, same idea) - this mirrors that for every accessor this entity's own bounding
    // box actually depends on. This is what guarantees the box gets recomputed the MOMENT real values
    // arrive, on both sides, regardless of exactly when tryPlace()'s own explicit refreshDimensions() call
    // or the renderer's per-frame one happen to run relative to entityData actually being populated - a
    // client reconstructing this entity from a spawn packet briefly has only DEFAULT synced values
    // (including the EntityType's own raw 1x1 dimensions from ModEntities.java's builder) until the
    // separate entity-data sync packet lands moments later; without this hook the box could stay wrong
    // until something else happened to call refreshDimensions() again.
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (accessor.equals(DATA_NEG_U) || accessor.equals(DATA_POS_U) || accessor.equals(DATA_NEG_V)
                || accessor.equals(DATA_POS_V) || accessor.equals(DATA_FACE))
            this.refreshDimensions();
    }

    @Override
    public void tick() {
        // Wall/ceiling/floor decor, not a physics object - no gravity, no drift, matches HangingEntity's
        // own tick() (empty on the server side beyond the "still attached" check vanilla does; this mirrors
        // that check with the broader non-full-face allowance instead of vanilla's own isFaceFull one).
        //
        // The bounding box can still occasionally revert to the raw EntityType default (a plain 1x1x1 cube -
        // see Entity#setPos's own doc, unrelated to this method) after a network position resync. FIVE
        // separate fixes have been tried and reverted: overriding makeBoundingBox() (twice, including an
        // exception-safe version), overriding setPos() (twice, the second matching vanilla's own proven
        // BlockAttachedEntity pattern exactly), and self-healing the box unconditionally right here in
        // tick() every frame. ALL FIVE broke rendering entirely, live, the same way each time (the entity
        // renders for a single instant then goes permanently invisible with no hitbox) - including two
        // architecturally unrelated approaches (a setPos hook vs. an unconditional per-tick call), which
        // rules out anything specific to setPos() itself and points at something environmental instead (this
        // client runs Sodium, a rendering-optimization mod that heavily rewrites entity culling/tracking
        // internals - a plausible, untested candidate). Leaving this as a known, currently unsolved
        // limitation rather than attempting a sixth blind variant.
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
        // Entity's own vanilla constructor calls refreshDimensions() (hence this) as part of ITS OWN
        // construction, which runs before this subclass's own field initializers do (attachPos's `=
        // BlockPos.ZERO` initializer is ordinary Java bytecode placed at the START of this class's
        // constructor, which only runs AFTER super(type, level) returns) - so attachPos can genuinely still
        // be null the very first time this is ever called. Falling back to the entity's own tracked block
        // position here (already set by Entity's constructor before it calls refreshDimensions) avoids an
        // NPE there; tryPlace() calls refreshDimensions() again right after setting the real attachPos, so
        // this fallback is only ever visible for a single transient construction step, never in practice.
        BlockPos pos = attachPos != null ? attachPos : BlockPos.containing(this.position());
        Level lvl = this.level();
        Direction face = attachFace();
        double faceOffset = lvl != null ? computeFaceOffset(lvl) : (face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0);
        double negU = negUUnits() / (double) UNITS_PER_BLOCK, posU = posUUnits() / (double) UNITS_PER_BLOCK;
        double negV = negVUnits() / (double) UNITS_PER_BLOCK, posV = posVUnits() / (double) UNITS_PER_BLOCK;
        // How far the slot rectangle's own geometric center sits from attachPos along each in-plane axis -
        // zero for the old-style symmetric size, nonzero once the anchor sits off-center within the
        // rectangle (see setExtents's own doc comment).
        double centerU = (posU - negU) / 2.0, centerV = (posV - negV) / 2.0;
        // The hitbox is always the FULL resize slot, not a shrunk-to-the-letterboxed-content rectangle - an
        // earlier version tried the latter (aspect-fit content only, pushed in from the client-side renderer
        // once a texture resolved) and it repeatedly landed wrong across several rounds of live testing.
        // "faut que ça marche comme les peintures" (live request) - vanilla paintings don't have this
        // distinction either: their hitbox is always their full canvas, so this matches that directly.
        double halfU = (negU + posU) / 2.0;
        double halfV = (negV + posV) / 2.0;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        double u0 = centerU - halfU, u1 = centerU + halfU, v0 = centerV - halfV, v1 = centerV + halfV;
        return switch (face) {
            case DOWN -> new AABB(cx + u0, pos.getY() + faceOffset - DEPTH, cz + v0, cx + u1, pos.getY() + faceOffset, cz + v1);
            case UP -> new AABB(cx + u0, pos.getY() + faceOffset, cz + v0, cx + u1, pos.getY() + faceOffset + DEPTH, cz + v1);
            case NORTH -> new AABB(cx + u0, cy + v0, pos.getZ() + faceOffset - DEPTH, cx + u1, cy + v1, pos.getZ() + faceOffset);
            case SOUTH -> new AABB(cx + u0, cy + v0, pos.getZ() + faceOffset, cx + u1, cy + v1, pos.getZ() + faceOffset + DEPTH);
            case WEST -> new AABB(pos.getX() + faceOffset - DEPTH, cy + v0, cz + u0, pos.getX() + faceOffset, cy + v1, cz + u1);
            case EAST -> new AABB(pos.getX() + faceOffset, cy + v0, cz + u0, pos.getX() + faceOffset + DEPTH, cy + v1, cz + u1);
        };
    }

    @Override
    public void refreshDimensions() {
        // Entity's own default refreshDimensions() recenters the bounding box on the entity's tracked
        // position using EntityDimensions - not meaningful for a face-attached, non-cube-shaped hitbox like
        // this one, so this overrides it to just recompute directly from attachPos/face/size instead.
        this.setBoundingBox(computeBoundingBox());
    }

    // Entity#setPos(x,y,z) independently calls this.setBoundingBox(this.makeBoundingBox()), using
    // EntityDimensions to rebuild a generic box, undoing whatever refreshDimensions() had just set - fires
    // routinely whenever a tracked entity's position is (re-)confirmed from the network, not just once at
    // spawn. Re-attempting the exact fix that matches vanilla's own proven BlockAttachedEntity#setPos
    // pattern (setPosRaw instead of super.setPos(), confirmed via the real decompiled Entity.java to carry
    // zero bounding-box logic) - five earlier variants of this all broke rendering entirely, live, for a
    // reason never pinned down, but EVERY one of those ran against attachPos still holding its unset
    // BlockPos.ZERO default the whole time (see getAddEntityPacket/recreateFromPacket's own comment - a
    // separate, real, now-fixed bug: attachPos was never actually transmitted to any client at all). A
    // setPos-triggered box computed from a permanently-wrong reference point is a plausible explanation for
    // whatever broke - this is a materially different attempt, not a blind repeat, now that attachPos is
    // actually correct on the client from the very first frame.
    @Override
    public void setPos(double x, double y, double z) {
        this.setPosRaw(x, y, z);
        // entityData is only null for the single earliest instant of construction, if Entity's own
        // constructor calls setPos() before defineSynchedData() has run (a final field, legally
        // readable-as-null before its own assignment) - skip the box fixup for just that one instant rather
        // than let attachFace() NPE on it; recreateFromPacket()/onSyncedDataUpdated correct it moments later
        // regardless.
        if (this.entityData != null)
            this.setBoundingBox(computeBoundingBox());
    }

    // Entity#getDimensions(Pose) is what feeds this.dimensions, which is what vanilla's OWN unmodified
    // setPos()/makeBoundingBox() falls back to whenever they run un-intercepted (see this class's own tick()
    // comment for the whole story of why nothing intercepts them anymore) - vanilla's own base implementation
    // just returns this.type.getDimensions(), the raw EntityType default (sized(1.0f,1.0f) in
    // ModEntities.java), regardless of this entity's actual configured size. Overriding THIS instead - a
    // plain data-provider method vanilla's own machinery already calls as part of its NORMAL, unmodified
    // flow - is a materially different, lower-risk change than the five reverted attempts: it never touches
    // setPos(), makeBoundingBox(), or tick() at all, so whatever broke rendering each of those five times has
    // nothing to interact with here. Can't capture the full asymmetric/thin/rotated shape this way (vanilla's
    // own EntityDimensions is only ever a symmetric width x height box centered on the tracked position, no
    // per-axis or off-center support) - this is a strictly better FALLBACK for whenever vanilla's own
    // machinery runs unmodified, not a replacement for computeBoundingBox()'s own precise result (which
    // still applies normally via refreshDimensions()/onSyncedDataUpdated whenever those fire).
    @Override
    public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        if (this.entityData == null)
            return super.getDimensions(pose);
        float size = Math.max(0.0625f, Math.max(widthBlocks(), heightBlocks()));
        return net.minecraft.world.entity.EntityDimensions.fixed(size, size);
    }

    // Entity#setPos(x,y,z) independently calls this.setBoundingBox(this.makeBoundingBox()), using
    // EntityDimensions (this.dimensions - the raw EntityType default, sized(1.0f,1.0f) in ModEntities.java,
    // never touched by this class) to rebuild a generic box, undoing whatever refreshDimensions() had just
    // set - a real, still-open issue. THREE different fixes attempted here, including one that exactly
    // matched vanilla's own proven BlockAttachedEntity#setPos/HangingEntity#recalculateBoundingBox pattern
    // (setPosRaw instead of super.setPos(), confirmed via the real decompiled Entity.java to have zero
    // bounding-box logic) - all three broke rendering AND the hitbox entirely, live, for a reason never
    // pinned down (setBoundingBox() itself is trivial, `this.bb = box`, so it isn't recursion either).
    // Stopping here rather than trying a fourth blind variant - touching setPos() at all appears to trigger
    // something specific to this codebase/setup that plain reasoning from the vanilla source hasn't
    // explained. Left as a known, currently-unsolved gap.

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
