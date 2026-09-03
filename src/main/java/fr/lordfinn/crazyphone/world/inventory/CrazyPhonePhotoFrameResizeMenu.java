package fr.lordfinn.crazyphone.world.inventory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.entity.CrazyPhonePhotoFrameEntity;
import fr.lordfinn.crazyphone.init.ModMenus;

/**
 * Resize/rotate dialog opened by right-clicking a placed {@link CrazyPhonePhotoFrameEntity}. No custom
 * networking at all - {@link ContainerData} (5 ints: the slot's own negU/posU/negV/posV extents in
 * {@link CrazyPhonePhotoFrameEntity#UNITS_PER_BLOCK} units - see {@link CrazyPhonePhotoFrameEntity#setExtents}
 * for what those mean - plus rotation) is vanilla's own built-in "sync a few numbers to an open menu"
 * mechanism (what furnaces/enchanting tables use), and every action rides vanilla's own
 * {@code ServerboundContainerButtonClickPacket} / {@link #clickMenuButton} - the same RPC an enchanting
 * table's slot-click uses. This is why registering this menu needs no extra "opening data" payload the way
 * every other phone screen in this codebase does (see ModMenus.java's own PASSTHROUGH_CODEC comment) - the
 * server-side constructor gets the target entity straight from a lambda closure in
 * ScreenMenuUtils#openPhotoFrameResizeMenu, never over the network at all.
 *
 * The resize GUI is a drag-select grid (CrazyPhonePhotoFrameResizeScreen), not a pair of +/- steppers, so a
 * resize action needs to carry arbitrary target extents in one shot rather than a single fixed step -
 * {@code ServerboundContainerButtonClickPacket} only ever carries one plain int button id, no extra payload,
 * so {@link #encodeAxisU}/{@link #encodeAxisV} each pack ONE axis's pair of extents into a single button id
 * (four values don't fit comfortably packed into one int the way the old symmetric width+height pair did) -
 * the screen sends both axis clicks back-to-back on drag release rather than reaching for a whole new packet
 * class for this.
 */
public class CrazyPhonePhotoFrameResizeMenu extends AbstractContainerMenu {
    public static final int ROTATE_BUTTON_ID = 4;
    // Comfortably separated from each other and from ROTATE_BUTTON_ID, so decoding a button id is an
    // unambiguous single range check.
    private static final int AXIS_U_BASE = 1_000_000;
    private static final int AXIS_V_BASE = 2_000_000;

    // Null on the client - it only ever sees the mirrored ContainerData values, never the entity itself
    // (which the network layer has no reason to send: the client's own local world already renders the
    // entity, this menu is purely a numeric control panel for it).
    private final CrazyPhonePhotoFrameEntity entity;
    private final ContainerData data;

    /** Server-side - constructed directly (not via the registered network factory) from
     * ScreenMenuUtils#openPhotoFrameResizeMenu's own MenuProvider#createMenu lambda. */
    public CrazyPhonePhotoFrameResizeMenu(int id, Inventory inventory, CrazyPhonePhotoFrameEntity entity) {
        super(ModMenus.CRAZY_PHONE_PHOTO_FRAME_RESIZE.get(), id);
        this.entity = entity;
        this.data = new SimpleContainerData(5);
        this.data.set(0, entity.negUUnits());
        this.data.set(1, entity.posUUnits());
        this.data.set(2, entity.negVUnits());
        this.data.set(3, entity.posVUnits());
        this.data.set(4, entity.rotation());
        addDataSlots(data);
    }

    /** Client-side - the registered MenuType factory signature every menu in this codebase uses
     * uniformly (see ModMenus.java), even though this one has no actual payload to read from {@code buf}. */
    public CrazyPhonePhotoFrameResizeMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(ModMenus.CRAZY_PHONE_PHOTO_FRAME_RESIZE.get(), id);
        this.entity = null;
        this.data = new SimpleContainerData(5);
        addDataSlots(data);
    }

    public int negUUnits() {
        return data.get(0);
    }

    public int posUUnits() {
        return data.get(1);
    }

    public int negVUnits() {
        return data.get(2);
    }

    public int posVUnits() {
        return data.get(3);
    }

    public int rotation() {
        return data.get(4);
    }

    public int maxUnits() {
        return Config.maxPhotoFrameSizeBlocks * CrazyPhonePhotoFrameEntity.UNITS_PER_BLOCK;
    }

    /** See this class's own doc comment - the screen sends one of these per axis on drag release. */
    public static int encodeAxisU(int negU, int posU) {
        return AXIS_U_BASE + negU * 1000 + posU;
    }

    public static int encodeAxisV(int negV, int posV) {
        return AXIS_V_BASE + negV * 1000 + posV;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (entity == null)
            return false;
        int maxUnits = maxUnits();
        if (id == ROTATE_BUTTON_ID) {
            entity.rotate();
            data.set(4, entity.rotation());
            return true;
        }
        if (id >= AXIS_V_BASE) {
            int packed = id - AXIS_V_BASE;
            int negV = Math.clamp(packed / 1000, 0, maxUnits);
            int posV = Math.clamp(packed % 1000, 0, maxUnits);
            data.set(2, negV);
            data.set(3, posV);
            entity.setExtents(data.get(0), data.get(1), negV, posV);
            data.set(0, entity.negUUnits());
            data.set(1, entity.posUUnits());
            data.set(2, entity.negVUnits());
            data.set(3, entity.posVUnits());
            return true;
        }
        if (id >= AXIS_U_BASE) {
            int packed = id - AXIS_U_BASE;
            int negU = Math.clamp(packed / 1000, 0, maxUnits);
            int posU = Math.clamp(packed % 1000, 0, maxUnits);
            entity.setExtents(negU, posU, data.get(2), data.get(3));
            data.set(0, entity.negUUnits());
            data.set(1, entity.posUUnits());
            data.set(2, entity.negVUnits());
            data.set(3, entity.posVUnits());
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return entity == null || (entity.isAlive() && !entity.isRemoved());
    }
}
