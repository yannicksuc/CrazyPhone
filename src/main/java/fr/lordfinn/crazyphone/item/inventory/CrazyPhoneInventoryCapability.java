package fr.lordfinn.crazyphone.item.inventory;

//? if >=1.20.5 {
import net.neoforged.neoforge.items.ComponentItemHandler;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
//? } else {
/*import net.neoforged.neoforge.items.ItemStackHandler;
*///?}
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
//? if >=1.20.5 {
import net.neoforged.fml.common.EventBusSubscriber;
//? } else {
/*import net.neoforged.fml.common.Mod.EventBusSubscriber;
*///?}
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
//? }
import net.minecraft.client.Minecraft;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.client.gui.PhoneScreen;
//? if <1.20.5 {
/*import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
*///?}

import javax.annotation.Nonnull;

@EventBusSubscriber(value = Dist.CLIENT)
//? if >=1.20.5 {
public class CrazyPhoneInventoryCapability extends ComponentItemHandler {
//? } else {
/*public class CrazyPhoneInventoryCapability extends ItemStackHandler {
    private final ItemStack phoneStack;
*///?}
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onItemDropped(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() == ModItems.CRAZY_PHONE.get()) {
            if (Minecraft.getInstance().screen instanceof PhoneScreen) {
                Minecraft.getInstance().player.closeContainer();
            }
        }
    }

    //? if >=1.20.5 {
    public CrazyPhoneInventoryCapability(MutableDataComponentHolder parent) {
        super(parent, DataComponents.CONTAINER, 97);
    }
    //? } else {
    /*public CrazyPhoneInventoryCapability(ItemStack parent) {
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
    *///?}

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
}
