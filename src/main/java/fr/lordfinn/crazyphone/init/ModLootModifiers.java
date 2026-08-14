package fr.lordfinn.crazyphone.init;

//? if >=1.20.5 {
/*import com.mojang.serialization.MapCodec;

import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.loot.SoulboundLootModifier;

import java.util.function.Supplier;

public class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Crazyphone.MODID);

    public static final Supplier<MapCodec<SoulboundLootModifier>> SOULBOUND_ANCIENT_CITY =
            LOOT_MODIFIER_SERIALIZERS.register("soulbound_ancient_city", () -> SoulboundLootModifier.CODEC);
}
*///?}
