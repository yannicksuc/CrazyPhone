package fr.lordfinn.crazyphone.init;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import fr.lordfinn.crazyphone.Crazyphone;

public class ModTabs {
    public static final DeferredRegister<CreativeModeTab> REGISTRY = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Crazyphone.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CRAZY_PHONE_TAB = REGISTRY.register("crazy_phone_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("item_group.crazyphone.crazy_phone_tab"))
                    .icon(() -> ModItems.CRAZY_PHONE.get().getDefaultInstance())
                    .displayItems((parameters, tabData) -> tabData.accept(ModItems.CRAZY_PHONE.get()))
                    .withSearchBar()
                    .build());
}
