package fr.lordfinn.crazyphone.item;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
//? if <1.21.10 {
import net.minecraft.world.InteractionResultHolder;
//? } else {
/*import net.minecraft.world.InteractionResult;
*///?}
import net.minecraft.world.InteractionHand;

// Photo item (and everything referencing it, including this class's own click-to-import override below)
// only exists on neoforge and fabric >=1.20.5 - see ModItems.java's own doc comment on 1.20.1-fabric's
// narrower walking-skeleton scope.
//? if neoforge || (fabric && >=1.20.5) {
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
//?}
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

    // Clicking a carried phone onto a Photo item in any inventory screen imports that photo into the
    // phone's own gallery instead of the normal cursor/slot swap - see CrazyPhonePhotoItem's matching
    // override (the reverse click order) and CrazyPhoneHelper#importPhotoIntoPhone for the shared logic
    // and why no menu/screen-specific code is needed on either loader. Also recognizes a Camera-mod
    // (de.maxhenkel.camera) Image/Album item or a Camerapture (me.chrr.camerapture) Picture/Album item the
    // same way, if either is installed - see CrazyPhoneHelper#importForeignPhoto and ForeignPhotoMods'
    // own doc comment on why that needs neither mod as a compile-time dependency. NeoForge-only for now:
    // both foreign mods' items are only reflectively recognized against a real, installed copy of them,
    // untested against Fabric's own (still walking-skeleton) build of this class.
    //? if neoforge || (fabric && >=1.20.5) {
    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        ItemStack slotStack = slot.getItem();
        if (slotStack.getItem() == ModItems.CRAZY_PHONE_PHOTO.get())
            return CrazyPhoneHelper.importPhotoIntoPhone(stack, slotStack, player, () -> slotStack.shrink(1));
        //? if neoforge {
        if (fr.lordfinn.crazyphone.utils.ForeignPhotoMods.isForeignPhotoItem(slotStack) || fr.lordfinn.crazyphone.utils.ForeignPhotoMods.isForeignAlbumItem(slotStack))
            return CrazyPhoneHelper.importForeignPhoto(stack, slotStack, player, () -> slotStack.shrink(1));
        //?}
        return false;
    }
    //?}

    // The reverse drag order from the override above: the phone is already sitting IN a slot and a foreign
    // photo/album item is dragged onto it FROM the cursor - vanilla's own overrideOtherStackedOnMe is called
    // on the SLOT item's class for exactly this case (see AbstractContainerMenu#doClick's own decompiled
    // source), so this needs no mixin into either foreign mod's item class at all, only this one of this
    // mod's own. CrazyPhonePhotoItem doesn't need a matching override here: overrideStackedOnOther already
    // covers "phone on cursor, Photo item in slot", and the actual reverse ("Photo item on cursor, phone in
    // slot") already worked before this - CrazyPhonePhotoItem.overrideStackedOnOther fires on the CURSOR
    // item's class regardless of which side the phone is on.
    //? if neoforge {
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, net.minecraft.world.entity.SlotAccess access) {
        if (fr.lordfinn.crazyphone.utils.ForeignPhotoMods.isForeignPhotoItem(other) || fr.lordfinn.crazyphone.utils.ForeignPhotoMods.isForeignAlbumItem(other))
            return CrazyPhoneHelper.importForeignPhoto(stack, other, player, () -> other.shrink(1));
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
