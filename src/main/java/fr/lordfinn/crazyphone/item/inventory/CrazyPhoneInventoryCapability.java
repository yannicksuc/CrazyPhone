package fr.lordfinn.crazyphone.item.inventory;

//? if >=1.20.5 {
/*import net.neoforged.neoforge.items.ComponentItemHandler;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
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
//? if >=1.21.10 {
/*public class CrazyPhoneInventoryCapability extends ComponentItemHandler implements ResourceHandler<ItemResource> {
*///?}
//? if >=1.20.5 <1.21.10 {
/*public class CrazyPhoneInventoryCapability extends ComponentItemHandler {
*///?}
//? if <1.20.5 {
public class CrazyPhoneInventoryCapability extends ItemStackHandler {
    private final ItemStack phoneStack;
//?}
    @SubscribeEvent
    //? if <1.21.10 {
    @OnlyIn(Dist.CLIENT)
    //?}
    public static void onItemDropped(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() == ModItems.CRAZY_PHONE.get()) {
            if (Minecraft.getInstance().screen instanceof PhoneScreen) {
                Minecraft.getInstance().player.closeContainer();
            }
        }
    }

    //? if >=1.20.5 {
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
