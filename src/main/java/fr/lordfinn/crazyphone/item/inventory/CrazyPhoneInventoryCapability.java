package fr.lordfinn.crazyphone.item.inventory;

//? if neoforge {
// NeoForge 26.3 deleted the whole deprecated net.neoforged.neoforge.items package (ComponentItemHandler,
// IItemHandler, ItemStackHandler...): from 26.3 on this class stores its slots in the CONTAINER component itself.
//? if >=1.20.5 <26.3 {
/*import net.neoforged.neoforge.items.ComponentItemHandler;
*///?}
//? if >=1.20.5 {
/*import net.neoforged.neoforge.common.MutableDataComponentHolder;
*///? } else {
import net.neoforged.neoforge.items.ItemStackHandler;
//?}
//? if >=1.21.10 {
/*import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
*///?}
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
//?}
// Real, original Forge 1.20.1 - net.minecraftforge.items.ItemStackHandler is the exact same class NeoForge's
// own <1.20.5 ItemStackHandler was forked from (javap-verified), and Dist/OnlyIn are resolvable here too -
// ModDevGradle's legacyforge plugin pulls in the mergetool artifact (the actual origin of that annotation
// pair) as a compile-time-only dependency for exactly this reason. Since the body below (the "<1.20.5"
// branch) was never loader-gated to begin with, only these imports need a legacyforge counterpart.
//? if legacyforge {
/*import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.api.distmarker.Dist;
*///?}

import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
/*import net.minecraft.core.component.DataComponents;
*///? }
import net.minecraft.client.Minecraft;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.client.gui.PhoneScreen;
//? if <1.20.5 {
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
//?}

import javax.annotation.Nonnull;

@EventBusSubscriber(value = Dist.CLIENT)
//? if >=1.21.10 <26.3 {
/*public class CrazyPhoneInventoryCapability extends ComponentItemHandler implements ResourceHandler<ItemResource> {
*///?}
//? if >=26.3 {
/*public class CrazyPhoneInventoryCapability implements ResourceHandler<ItemResource> {
    private static final int SLOTS = 97;
    private final MutableDataComponentHolder parent;
*///?}
//? if >=1.20.5 <1.21.10 {
/*public class CrazyPhoneInventoryCapability extends ComponentItemHandler {
*///?}
//? if <1.20.5 {
public class CrazyPhoneInventoryCapability extends ItemStackHandler {
    private final ItemStack phoneStack;
//?}
    // NeoForge-only: forces an immediate screen close the instant the phone is dropped, rather than waiting
    // for the next container tick. No Fabric port needed - every phone menu (CrazyPhoneDefaultScreenMenu and
    // its subclasses) already binds stillValid() to a boundItemMatcher checking the held item is still the
    // same instance, so dropping the phone makes stillValid() return false on the very next tick regardless
    // of loader; this override only trims that one-tick delay.
    @SubscribeEvent
    //? if <1.21.10 {
    @OnlyIn(Dist.CLIENT)
    //?}
    public static void onItemDropped(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() == ModItems.CRAZY_PHONE.get()) {
            if (Minecraft.getInstance()./*$ mc_get_screen {*/screen/*$}*/ instanceof PhoneScreen) {
                Minecraft.getInstance().player.closeContainer();
            }
        }
    }

    //? if >=26.3 {
    /*public CrazyPhoneInventoryCapability(MutableDataComponentHolder parent) {
        this.parent = parent;
    }

    // Same behavior as NeoForge's old ComponentItemHandler (removed in 26.3), which the older branches extend:
    // the slots live in the parent's CONTAINER component, every write stores a fresh ItemContainerContents.
    public int getSlots() {
        return SLOTS;
    }

    public int getSlotLimit(int slot) {
        return 1;
    }

    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return stack.getItem() != ModItems.CRAZY_PHONE.get();
    }

    public ItemStack getStackInSlot(int slot) {
        return stackFrom(contents(), slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        checkSlot(slot);
        if (!isItemValid(slot, stack))
            throw new RuntimeException("Invalid stack " + stack + " for slot " + slot);
        net.minecraft.world.item.component.ItemContainerContents contents = contents();
        if (!ItemStack.matches(stack, stackFrom(contents, slot)))
            write(contents, stack, slot);
    }

    public ItemStack insertItem(int slot, ItemStack toInsert, boolean simulate) {
        checkSlot(slot);
        if (toInsert.isEmpty())
            return ItemStack.EMPTY;
        if (!isItemValid(slot, toInsert))
            return toInsert;
        net.minecraft.world.item.component.ItemContainerContents contents = contents();
        ItemStack existing = stackFrom(contents, slot);
        int limit = Math.min(getSlotLimit(slot), toInsert.getMaxStackSize());
        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameComponents(toInsert, existing))
                return toInsert;
            limit -= existing.getCount();
        }
        if (limit <= 0)
            return toInsert;
        int inserted = Math.min(limit, toInsert.getCount());
        if (!simulate)
            write(contents, toInsert.copyWithCount(existing.getCount() + inserted), slot);
        return toInsert.copyWithCount(toInsert.getCount() - inserted);
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        checkSlot(slot);
        if (amount == 0)
            return ItemStack.EMPTY;
        net.minecraft.world.item.component.ItemContainerContents contents = contents();
        ItemStack existing = stackFrom(contents, slot);
        if (existing.isEmpty())
            return ItemStack.EMPTY;
        int toExtract = Math.min(amount, existing.getCount());
        if (!simulate)
            write(contents, existing.copyWithCount(existing.getCount() - toExtract), slot);
        return existing.copyWithCount(toExtract);
    }

    private net.minecraft.world.item.component.ItemContainerContents contents() {
        return parent.getOrDefault(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.EMPTY);
    }

    private ItemStack stackFrom(net.minecraft.world.item.component.ItemContainerContents contents, int slot) {
        checkSlot(slot);
        if (slot >= contents.getSlots())
            return ItemStack.EMPTY;
        return contents.getTemplateInSlot(slot).map(net.minecraft.world.item.ItemStackTemplate::create).orElse(ItemStack.EMPTY);
    }

    private void write(net.minecraft.world.item.component.ItemContainerContents contents, ItemStack stack, int slot) {
        net.minecraft.core.NonNullList<ItemStack> list = net.minecraft.core.NonNullList.withSize(Math.max(contents.getSlots(), SLOTS), ItemStack.EMPTY);
        contents.copyInto(list);
        list.set(slot, stack);
        parent.set(DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(list));
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOTS)
            throw new RuntimeException("Slot " + slot + " not in valid range - [0," + SLOTS + ")");
    }
    *///? } else if >=1.20.5 {
    /*public CrazyPhoneInventoryCapability(MutableDataComponentHolder parent) {
        super(parent, DataComponents.CONTAINER, 97);
    }
    *///? } else {
    public CrazyPhoneInventoryCapability(ItemStack parent) {
        super(97);
        this.phoneStack = parent;
        net.minecraft.nbt.CompoundTag inventoryTag = PhoneTagAccess.getTag(parent).getCompound("Inventory");
        if (!inventoryTag.isEmpty())
            deserializeNBT(inventoryTag);
    }

    @Override
    protected void onContentsChanged(int slot) {
        PhoneTagAccess.updateTag(phoneStack, tag -> tag.put("Inventory", serializeNBT()));
    }
    //?}

    //? if <26.3 {
    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return stack.getItem() != ModItems.CRAZY_PHONE.get();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return super.getStackInSlot(slot).copy();
    }
    //?}

    //? if >=1.21.10 {
    /*@Override
    public int size() {
        return getSlots();
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.of(getStackInSlot(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return getStackInSlot(index).getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return getSlotLimit(index);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return isItemValid(index, resource.toStack());
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        ItemStack toInsert = resource.toStack(amount);
        ItemStack remainder = insertItem(index, toInsert, true);
        int accepted = amount - remainder.getCount();
        if (accepted <= 0)
            return 0;
        snapshotForRollback(index, transaction);
        insertItem(index, toInsert, false);
        return accepted;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        ItemStack simulated = extractItem(index, amount, true);
        if (simulated.isEmpty())
            return 0;
        snapshotForRollback(index, transaction);
        return extractItem(index, amount, false).getCount();
    }

    // Bridges ComponentItemHandler's immediate, non-transactional writes into the transaction system: a
    // fresh per-call journal snapshots the slot right before the mutation, so a caller that opens a
    // transaction and never commits (the new API's replacement for the old boolean "simulate" flag) still
    // sees the slot reverted, matching how automation such as hoppers expects insert/extract to behave.
    private void snapshotForRollback(int index, TransactionContext transaction) {
        new SnapshotJournal<ItemStack>() {
            @Override
            protected ItemStack createSnapshot() {
                return getStackInSlot(index);
            }

            @Override
            protected void revertToSnapshot(ItemStack snapshot) {
                setStackInSlot(index, snapshot);
            }
        }.updateSnapshots(transaction);
    }
    *///?}
}
