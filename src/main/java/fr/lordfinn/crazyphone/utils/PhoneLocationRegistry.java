package fr.lordfinn.crazyphone.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.init.ModItems;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side, in-memory only (never SavedData - same "session-only, revalidated on next use, no stale
 * persistence" reasoning as {@link fr.lordfinn.crazyphone.voicechat.CallRegistry}) last-known-container-
 * location per phone NUMBER (not per item instance - a phone's number is its stable identity, see
 * PhoneRegistrySavedData's own doc comment). Fed by {@link fr.lordfinn.crazyphone.mixin.CrazyPhoneContainerInsertMixin},
 * which hooks vanilla's OWN {@code BaseContainerBlockEntity#setItem} - the single method every chest/barrel/
 * shulker-box/hopper/furnace/dropper block entity in the game already routes an item change through - rather
 * than a per-loader "container closed" event (checked: {@code PlayerContainerEvent.Close} exists on
 * NeoForge 1.21.1 and Forge 1.20.1 but NOT on NeoForge 26.1, so chasing that event across every loader/
 * version would have meant three different, occasionally-missing mechanisms instead of one stable vanilla
 * method) - live request: "on peut pas se bind sur une methode minecraft d'insertion plutot que de faire
 * des scan ?".
 * <p>
 * Deliberately just a HINT, never trusted blindly: {@link fr.lordfinn.crazyphone.voicechat.CallTerminationListener}'s
 * own periodic sweep re-reads whatever's actually at a recorded position before ringing there, so a phone
 * that's since been taken back out (or the container broken) simply falls through to "not found" rather
 * than ringing at a stale, now-wrong location - the class never needs an explicit "remove" call for that
 * same reason ("revalide a l'appel suivant, pas de cache perime" - live request).
 * <p>
 * Recurses (bounded by {@link #MAX_RECURSION_DEPTH}, so a shulker-in-a-shulker-in-a-bag chain still
 * terminates) into a vanilla shulker box or bundle item sitting in a container slot (a phone tucked inside
 * one of those, itself placed in a chest, still gets found) - AND, GENERICALLY, into
 * anything registering NeoForge/Forge's own shared "item handler" capability (Sophisticated Backpacks
 * included - live request: "je veux que sophisticated backpack soit prit en compte"), with zero dependency
 * on that specific mod's own code (see {@link #genericCapabilityItemsOf}'s own doc comment for exactly what
 * that does and doesn't cover, notably NOT Fabric or NeoForge 26+, neither of which has an equivalent
 * single, shared convention to query generically).
 */
public final class PhoneLocationRegistry {
    private static final int MAX_RECURSION_DEPTH = 4;

    public record Location(ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static final Map<String, Location> LAST_KNOWN = new ConcurrentHashMap<>();

    private PhoneLocationRegistry() {
    }

    public static Optional<Location> get(String number) {
        return Optional.ofNullable(LAST_KNOWN.get(number));
    }

    private static void record(String number, ResourceKey<Level> dimension, BlockPos pos) {
        if (number == null || number.isEmpty())
            return;
        LAST_KNOWN.put(number, new Location(dimension, pos.immutable()));
    }

    /** Called from the container-insertion mixin with whatever stack just got set into a slot - records a
     * direct phone match, or recurses into a vanilla shulker-box/bundle stack's own contents looking for
     * one nested inside. */
    public static void recordIfPhoneOrContainsPhone(ItemStack stack, ResourceKey<Level> dimension, BlockPos pos) {
        recordRecursive(stack, dimension, pos, 0);
    }

    private static void recordRecursive(ItemStack stack, ResourceKey<Level> dimension, BlockPos pos, int depth) {
        if (stack.isEmpty() || depth > MAX_RECURSION_DEPTH)
            return;
        if (stack.getItem() == ModItems.CRAZY_PHONE.get()) {
            String number = NbtCompat.getString(PhoneTagAccess.getTag(stack), "number");
            record(number, dimension, pos);
            return;
        }
        for (ItemStack nested : nestedItemsOf(stack))
            recordRecursive(nested, dimension, pos, depth + 1);
    }

    /** Revalidates a recorded location before anyone actually rings there - a phone hint is only ever a
     * HINT (see this class's own doc comment), so this re-reads whatever's really at {@code pos} right now
     * rather than trusting the cache blindly. Returns false for anything that isn't (or no longer is) a
     * real Container at that position, same as a phone that's since been taken back out. */
    public static boolean containerStillHasPhone(String number, Level level, BlockPos pos) {
        if (number == null || number.isEmpty())
            return false;
        if (!(level.getBlockEntity(pos) instanceof net.minecraft.world.Container container))
            return false;
        for (int i = 0; i < container.getContainerSize(); i++)
            if (containsNumberRecursive(container.getItem(i), number, 0))
                return true;
        return false;
    }

    private static boolean containsNumberRecursive(ItemStack stack, String number, int depth) {
        if (stack.isEmpty() || depth > MAX_RECURSION_DEPTH)
            return false;
        if (stack.getItem() == ModItems.CRAZY_PHONE.get())
            return number.equals(NbtCompat.getString(PhoneTagAccess.getTag(stack), "number"));
        for (ItemStack nested : nestedItemsOf(stack))
            if (containsNumberRecursive(nested, number, depth + 1))
                return true;
        return false;
    }

    /** Every nested item a stack might be carrying, from BOTH sources combined - a vanilla shulker box/
     * bundle AND, generically, anything exposing a modded item-handler capability. */
    private static Iterable<ItemStack> nestedItemsOf(ItemStack stack) {
        List<ItemStack> combined = new java.util.ArrayList<>();
        for (ItemStack item : vanillaNestedItemsOf(stack))
            combined.add(item);
        for (ItemStack item : genericCapabilityItemsOf(stack))
            combined.add(item);
        return combined;
    }

    /** A vanilla shulker box or bundle stack's own contained items. */
    private static Iterable<ItemStack> vanillaNestedItemsOf(ItemStack stack) {
        // 1.21.10+ reworked ItemContainerContents/BundleContents to hand back ItemStackTemplate (a
        // count/component TEMPLATE, not a real ItemStack) from their old nonEmptyItems()/items() accessors -
        // javap-verified against the real 26.1 jar, not guessed (nonEmptyItems() there returns
        // Iterable<ItemStackTemplate>, a straight compile error against this method's Iterable<ItemStack>
        // return type). nonEmptyItemCopyStream()/itemCopyStream() are that version's own real-ItemStack
        // equivalents instead.
        //? if >=26 {
        /*net.minecraft.world.item.component.ItemContainerContents container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (container != null)
            return container.nonEmptyItemCopyStream().toList();
        net.minecraft.world.item.component.BundleContents bundle = stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        return bundle != null ? bundle.itemCopyStream().toList() : List.of();
        *///?}
        //? if >=1.20.5 <26 {
        /*net.minecraft.world.item.component.ItemContainerContents container = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (container != null)
            return container.nonEmptyItems();
        net.minecraft.world.item.component.BundleContents bundle = stack.get(net.minecraft.core.component.DataComponents.BUNDLE_CONTENTS);
        return bundle != null ? bundle.items() : List.of();
        *///?}
        //? if <1.20.5 {
        CompoundTag blockEntityTag = stack.getTagElement("BlockEntityTag");
        if (blockEntityTag == null)
            return List.of();
        NonNullList<ItemStack> items = NonNullList.create();
        net.minecraft.world.ContainerHelper.loadAllItems(blockEntityTag, items);
        return items;
        //?}
    }

    /** ANY item's own contents, read through NeoForge/Forge's own shared "item handler" capability - with
     * zero knowledge of which mod actually implements it, this is what picks up Sophisticated Backpacks (or
     * any other storage-item mod on those two loaders) without a hard dependency on its code.
     * <p>
     * Empty on FABRIC: unlike NeoForge/Forge's one blessed, shared capability every storage-item mod
     * registers against, Fabric's own Transfer API has no equivalent single convention for "does this
     * ITEM (not block) expose nested storage" - each mod publishes its own {@code ItemApiLookup} field, and
     * querying it generically would mean a hard dependency on that specific mod's own API after all
     * (checked: {@code fabric-transfer-api-v1} itself only ships {@code ItemStorage.SIDED}, a BLOCK lookup,
     * no item-level equivalent). Also empty on NeoForge 26+: that version replaced {@code IItemHandler}
     * with a whole new {@code ResourceHandler}/{@code ItemAccess} transfer API (javap-verified against the
     * real 26.1 jar - {@code Capabilities.ItemHandler} itself is gone, renamed/reshaped into
     * {@code Capabilities.Item}) - a separate, not-yet-attempted piece of work, and no backpack mod
     * realistically targets this bleeding-edge node yet regardless. */
    private static Iterable<ItemStack> genericCapabilityItemsOf(ItemStack stack) {
        //? if legacyforge {
        net.minecraftforge.items.IItemHandler handler = stack.getCapability(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        return itemsFromLegacyHandler(handler);
        //? } else if neoforge && <26 {
        /*net.neoforged.neoforge.items.IItemHandler handler = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.ITEM.getCapability(stack, null);
        return itemsFromNeoForgeHandler(handler);
        *///? } else {
        /*return List.of();
        *///?}
    }

    //? if legacyforge {
    private static Iterable<ItemStack> itemsFromLegacyHandler(net.minecraftforge.items.IItemHandler handler) {
        if (handler == null)
            return List.of();
        List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slotStack = handler.getStackInSlot(i);
            if (!slotStack.isEmpty())
                items.add(slotStack);
        }
        return items;
    }
    //?}
    //? if neoforge && <26 {
    /*private static Iterable<ItemStack> itemsFromNeoForgeHandler(net.neoforged.neoforge.items.IItemHandler handler) {
        if (handler == null)
            return List.of();
        List<ItemStack> items = new java.util.ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slotStack = handler.getStackInSlot(i);
            if (!slotStack.isEmpty())
                items.add(slotStack);
        }
        return items;
    }
    *///?}
}
