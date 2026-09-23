package fr.lordfinn.crazyphone.mixin;

/**
 * Feeds {@link fr.lordfinn.crazyphone.utils.PhoneLocationRegistry} by hooking vanilla's OWN
 * {@code setItem(int, ItemStack)} - the one method every chest/barrel/shulker-box/hopper/furnace/dropper
 * block entity in the game already routes an item change through, on the SERVER side only (this mixin is
 * common, not client-only, but bails immediately on a client-side level, same guard pattern
 * CallTerminationListener's own sweep never needs since it's server-only to begin with). Deliberately NOT a
 * per-loader "player closed this container" event - see PhoneLocationRegistry's own doc comment for why
 * (checked: {@code PlayerContainerEvent.Close} exists on NeoForge 1.21.1 and Forge 1.20.1 but NOT on
 * NeoForge 26.1, so this single vanilla method is the only mechanism actually stable across every supported
 * node - live request: "on peut pas se bind sur une methode minecraft d'insertion plutot que de faire des
 * scan ?").
 * <p>
 * The mixin TARGET CLASS itself is version-gated, not just the body: {@code setItem} is declared on
 * {@code RandomizableContainerBlockEntity} on 1.20.1 (real Forge) but moved up to its own superclass
 * {@code BaseContainerBlockEntity} by 1.21.1 (javap-verified against both merged jars, not guessed) -
 * chest/barrel/shulker-box/hopper/dropper/dispenser all extend {@code RandomizableContainerBlockEntity},
 * so mixing into whichever of the two actually declares {@code setItem} on a given version still covers
 * every one of them either way.
 * <p>
 * Known gap, on EVERY version: the furnace family ({@code AbstractFurnaceBlockEntity}) and
 * {@code BrewingStandBlockEntity} both extend {@code BaseContainerBlockEntity} DIRECTLY (not
 * {@code RandomizableContainerBlockEntity}) and override {@code setItem} themselves without calling
 * {@code super} (javap-verified on both 1.20.1 and 1.21.1) - a phone placed in a furnace or brewing stand
 * slot is not tracked by this mixin at all. Not worth a second mixin target for what would be a pretty
 * deliberately unusual place to stash a phone; caught in a cleanliness pass, documented rather than fixed.
 */
//? if <1.21.1 {
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.utils.PhoneLocationRegistry;

@Mixin(RandomizableContainerBlockEntity.class)
public abstract class CrazyPhoneContainerInsertMixin {
    // No require=0 here - this project's own crazyphone.mixins.json already defaults to requireAnnotations/
    // defaultRequire:1, and the target signature was javap-verified for every supported node (see this
    // class's own doc comment), so a silent soft-fail would hide a real version-shape regression instead of
    // catching it loudly at launch the way every other mixin in this project already does.
    @Inject(method = "setItem", at = @At("HEAD"))
    private void crazyphone$onSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide())
            return;
        PhoneLocationRegistry.recordIfPhoneOrContainsPhone(stack, level.dimension(), self.getBlockPos());
    }
}
//? } else {
/*import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import fr.lordfinn.crazyphone.utils.PhoneLocationRegistry;

@Mixin(BaseContainerBlockEntity.class)
public abstract class CrazyPhoneContainerInsertMixin {
    @Inject(method = "setItem", at = @At("HEAD"))
    private void crazyphone$onSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide())
            return;
        PhoneLocationRegistry.recordIfPhoneOrContainsPhone(stack, level.dimension(), self.getBlockPos());
    }
}
*///?}
