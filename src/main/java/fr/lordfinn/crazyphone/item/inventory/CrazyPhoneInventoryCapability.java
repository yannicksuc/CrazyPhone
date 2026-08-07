package fr.lordfinn.crazyphone.item.inventory;

import net.neoforged.neoforge.items.ComponentItemHandler;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.common.MutableDataComponentHolder;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.Minecraft;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.client.gui.PhoneScreen;

import javax.annotation.Nonnull;

@EventBusSubscriber(value = Dist.CLIENT)
public class CrazyPhoneInventoryCapability extends ComponentItemHandler {
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onItemDropped(ItemTossEvent event) {
        if (event.getEntity().getItem().getItem() == ModItems.CRAZY_PHONE.get()) {
            if (Minecraft.getInstance().screen instanceof PhoneScreen) {
                Minecraft.getInstance().player.closeContainer();
            }
        }
    }

    public CrazyPhoneInventoryCapability(MutableDataComponentHolder parent) {
        super(parent, DataComponents.CONTAINER, 97);
    }

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
