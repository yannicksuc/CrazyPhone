package fr.lordfinn.crazyphone.loot;

//? if >=1.20.5 {
/*import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import fr.lordfinn.crazyphone.enchantment.ModEnchantments;
import fr.lordfinn.crazyphone.init.ModLootModifiers;

// Adds an enchanted book with the Soulbound enchantment to whichever loot table this instance's JSON
// condition targets (Ancient City chests - see resources/data/crazyphone/loot_modifier). This is the ONLY
// way the enchantment ever enters the world: it's deliberately not a member of any vanilla enchanting-table
// or villager-trade enchantment tag (see resources/data/crazyphone/enchantment/soulbound.json), so this
// modifier - not the table, not trading - is the sole source of the first book.
public class SoulboundLootModifier extends LootModifier {
    public static final MapCodec<SoulboundLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            codecStart(inst)
                    .and(Codec.floatRange(0f, 1f).fieldOf("chance").forGetter(m -> m.chance))
                    .apply(inst, SoulboundLootModifier::new));

    private final float chance;

    // 26.x's LootModifier gained a priority field, threaded through codecStart(...)'s own returned group (2
    // fields now: conditions + priority, instead of just conditions) - so the generated codec now feeds this
    // constructor an extra Integer, and the super constructor itself takes it too (confirmed against the
    // real NeoForge 26.1.2.100 sources).
    //? if >=26 {
    /^public SoulboundLootModifier(LootItemCondition[] conditions, int priority, float chance) {
        super(conditions, priority);
        this.chance = chance;
    }^/
    //? } else {
    public SoulboundLootModifier(LootItemCondition[] conditions, float chance) {
        super(conditions);
        this.chance = chance;
    }
    //?}

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (context.getRandom().nextFloat() >= chance)
            return generatedLoot;

        Holder<Enchantment> soulbound = ModEnchantments.resolve(context.getLevel());
        if (soulbound != null) {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(soulbound, 1);
            generatedLoot.add(book);
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return ModLootModifiers.SOULBOUND_ANCIENT_CITY.get();
    }
}
*///?}
