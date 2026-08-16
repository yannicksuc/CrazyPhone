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
//? if fabric && >=1.20.5 {
/*import fr.lordfinn.crazyphone.procedures.CrazyPhoneOnUseProcedure;
*///?}

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

    //? if <1.21.10 {
    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player entity, InteractionHand hand) {
        InteractionResultHolder<ItemStack> ar = super.use(world, entity, hand);
        if (hand == InteractionHand.MAIN_HAND) {
            //? if neoforge {
            CrazyPhoneOnUseProcedure.execute(world, entity.getX(), entity.getY(), entity.getZ(), entity);
            //?}
            //? if fabric && >=1.20.5 {
            /*CrazyPhoneOnUseProcedure.execute(world, entity.getX(), entity.getY(), entity.getZ(), entity);
            *///?}
            // No call on fabric && <1.20.5 (1.20.1-fabric): CrazyPhoneOnUseProcedure's dependency graph
            // (ScreenMenuUtils, PhoneAttachmentTypes, ...) is >=1.20.5-only there - see build.fabric.gradle.kts.
        }
        return ar;
    }
    //? } else {
    /*@Override
    public InteractionResult use(Level world, Player entity, InteractionHand hand) {
        InteractionResult ar = super.use(world, entity, hand);
        if (hand == InteractionHand.MAIN_HAND) {
            //? if neoforge {
            /^CrazyPhoneOnUseProcedure.execute(world, entity.getX(), entity.getY(), entity.getZ(), entity);
            ^///?}
            //? if fabric && >=1.20.5 {
            /^CrazyPhoneOnUseProcedure.execute(world, entity.getX(), entity.getY(), entity.getZ(), entity);
            ^///?}
        }
        return ar;
    }
    *///?}
}
