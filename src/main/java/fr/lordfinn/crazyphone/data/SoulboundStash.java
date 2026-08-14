package fr.lordfinn.crazyphone.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
//? if <1.21.10 {
import net.neoforged.neoforge.common.util.INBTSerializable;
//? } else {
/*import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
*///?}
//? if >=1.20.5 {
/*import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
*///?}

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Items carrying the Soulbound enchantment, pulled off a dying player's death drops (see SoulboundHandler)
 * and held here only for the brief window between death and respawn, where they're reinserted into the
 * player's inventory and this is cleared. Empty the rest of the time. */
//? if <1.21.10 {
public class SoulboundStash implements INBTSerializable<CompoundTag> {
//? } else {
/*public class SoulboundStash implements ValueIOSerializable {
*///?}
    public List<ItemStack> items = new ArrayList<>();

    //? if >=1.21.10 {
    /*@Override
    public void serialize(ValueOutput output) {
        ValueOutput.TypedOutputList<ItemStack> list = output.list("items", ItemStack.CODEC);
        items.forEach(list::add);
    }

    @Override
    public void deserialize(ValueInput input) {
        items = new ArrayList<>();
        input.listOrEmpty("items", ItemStack.CODEC).forEach(items::add);
    }
    *///? } else {
    //? if >=1.20.5 {
    /*@Override
    public CompoundTag serializeNBT(HolderLookup.@NotNull Provider lookupProvider) {
        RegistryOps<Tag> ops = lookupProvider.createSerializationContext(NbtOps.INSTANCE);
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(list::add);
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("items", list);
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider lookupProvider, CompoundTag nbt) {
        RegistryOps<Tag> ops = lookupProvider.createSerializationContext(NbtOps.INSTANCE);
        items = new ArrayList<>();
        for (Tag entry : nbt.getList("items", CompoundTag.TAG_COMPOUND)) {
            ItemStack.CODEC.parse(ops, entry).result().ifPresent(items::add);
        }
    }
    *///? } else {
    @Override
    public CompoundTag serializeNBT() {
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            list.add(stack.save(new CompoundTag()));
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("items", list);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        items = new ArrayList<>();
        for (Tag entry : nbt.getList("items", CompoundTag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag compound)
                items.add(ItemStack.of(compound));
        }
    }
    //?}
    //?}
}
