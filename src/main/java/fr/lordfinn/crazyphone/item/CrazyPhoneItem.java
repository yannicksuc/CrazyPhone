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

//? if neoforge {
import fr.lordfinn.crazyphone.procedures.CrazyPhoneOnUseProcedure;
//?}

public class CrazyPhoneItem extends Item {
    public CrazyPhoneItem(Item.Properties properties) {
        //? if >=1.21.10 {
        /*super(properties.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.CONTAINER, true))
                .stacksTo(1).rarity(Rarity.COMMON));
        *///? } else {
        super(properties.stacksTo(1).rarity(Rarity.COMMON));
        //?}
    }

    //? if neoforge {
    // NeoForge-added IItemExtension hook, not real vanilla Item API - no Fabric equivalent wired up yet
    // (Fabric API's ItemStack merge/reequip suppression works differently; low-priority cosmetic nicety).
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return false;
    }
    //?}

    //? if neoforge {
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
    //?}
    //? if fabric {
    /*// TODO(#160-165): wire CrazyPhoneOnUseProcedure once its network/menu/camera dependency graph is
    // ported for Fabric - both current Fabric targets (1.21.1, 1.20.1) predate 1.21.10, so only the
    // InteractionResultHolder-returning shape is needed here for now.
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entity, InteractionHand hand) {
        return super.use(world, entity, hand);
    }
    *///?}
}
