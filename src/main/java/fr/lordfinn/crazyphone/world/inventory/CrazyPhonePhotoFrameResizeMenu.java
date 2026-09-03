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
 * Resize dialog opened by right-clicking a placed {@link CrazyPhonePhotoFrameEntity}. No custom networking
 * at all - {@link ContainerData} (2 ints: width/height in {@link CrazyPhonePhotoFrameEntity#UNITS_PER_BLOCK}
 * units) is vanilla's own built-in "sync a couple of numbers to an open menu" mechanism (what furnaces/
 * enchanting tables use), and the +/-  buttons ride vanilla's own {@code ServerboundContainerButtonClickPacket}
 * / {@link #clickMenuButton} - the same RPC an enchanting table's slot-click uses. This is why registering
 * this menu needs no extra "opening data" payload the way every other phone screen in this codebase does
 * (see ModMenus.java's own PASSTHROUGH_CODEC comment) - the server-side constructor gets the target entity
 * straight from a lambda closure in ScreenMenuUtils#openPhotoFrameResizeMenu, never over the network at all.
 */
public class CrazyPhonePhotoFrameResizeMenu extends AbstractContainerMenu {
    // 1/8-block granularity per click - fine enough to feel smooth, coarse enough to reach the configured
    // max size in a reasonable number of clicks.
    private static final int STEP = 1;
    private static final int MIN_UNITS = CrazyPhonePhotoFrameEntity.UNITS_PER_BLOCK / 4; // 0.25 block

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
        this.data = new SimpleContainerData(2);
        this.data.set(0, entity.widthUnits());
        this.data.set(1, entity.heightUnits());
        addDataSlots(data);
    }

    /** Client-side - the registered MenuType factory signature every menu in this codebase uses
     * uniformly (see ModMenus.java), even though this one has no actual payload to read from {@code buf}. */
    public CrazyPhonePhotoFrameResizeMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(ModMenus.CRAZY_PHONE_PHOTO_FRAME_RESIZE.get(), id);
        this.entity = null;
        this.data = new SimpleContainerData(2);
        addDataSlots(data);
    }

    public int widthUnits() {
        return data.get(0);
    }

    public int heightUnits() {
        return data.get(1);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (entity == null)
            return false;
        int maxUnits = Config.maxPhotoFrameSizeBlocks * CrazyPhonePhotoFrameEntity.UNITS_PER_BLOCK;
        int width = data.get(0);
        int height = data.get(1);
        switch (id) {
            case 0 -> width = Math.max(MIN_UNITS, width - STEP);
            case 1 -> width = Math.min(maxUnits, width + STEP);
            case 2 -> height = Math.max(MIN_UNITS, height - STEP);
            case 3 -> height = Math.min(maxUnits, height + STEP);
            default -> {
                return false;
            }
        }
        data.set(0, width);
        data.set(1, height);
        entity.setSizeUnits(width, height);
        return true;
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
