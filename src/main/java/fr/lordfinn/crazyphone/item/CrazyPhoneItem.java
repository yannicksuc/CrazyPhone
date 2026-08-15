package fr.lordfinn.crazyphone.item;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
//? if <1.21.10 {
import net.minecraft.world.InteractionResultHolder;
//? } else {
/*import net.minecraft.world.InteractionResult;
*///?}
import net.minecraft.world.InteractionHand;
//? if >=1.21.10 {
/*import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.TooltipDisplay;
*///?}

import fr.lordfinn.crazyphone.procedures.CrazyPhoneOnUseProcedure;

public class CrazyPhoneItem extends Item {
    public CrazyPhoneItem(Item.Properties properties) {
        //? if >=1.21.10 {
        /*super(properties.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.CONTAINER, true))
                .stacksTo(1).rarity(Rarity.COMMON));
        *///? } else {
        super(properties.stacksTo(1).rarity(Rarity.COMMON));
        //?}
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }

    //? if <1.21.10 {
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entity, InteractionHand hand) {
        InteractionResultHolder<ItemStack> ar = super.use(world, entity, hand);
        if (hand == InteractionHand.MAIN_HAND)
            CrazyPhoneOnUseProcedure.execute(world, entity.getX(), entity.getY(), entity.getZ(), entity);
        return ar;
    }
    //? } else {
    /*@Override
    public InteractionResult use(Level world, Player entity, InteractionHand hand) {
        InteractionResult ar = super.use(world, entity, hand);
        if (hand == InteractionHand.MAIN_HAND)
            CrazyPhoneOnUseProcedure.execute(world, entity.getX(), entity.getY(), entity.getZ(), entity);
        return ar;
    }
    *///?}
}
