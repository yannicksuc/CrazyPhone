package fr.lordfinn.crazyphone.init;

//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if fabric {
/*import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
*///?}

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;

import fr.lordfinn.crazyphone.Crazyphone;

//? if neoforge {
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
//?}
//? if fabric {
/*// No DeferredRegister equivalent on Fabric - a plain Registry.register call, same as ModItems. Must run
// after ModItems.register() (needs ModItems.CRAZY_PHONE populated for the icon/displayItems callbacks).
// FabricItemGroup.Builder doesn't expose withSearchBar() (a NeoForge-only builder addition) - dropped here,
// purely cosmetic.
public class ModTabs {
    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Crazyphone.resource("crazy_phone_tab"),
                FabricItemGroup.builder()
                        .title(Component.translatable("item_group.crazyphone.crazy_phone_tab"))
                        .icon(() -> ModItems.CRAZY_PHONE.get().getDefaultInstance())
                        .displayItems((parameters, tabData) -> tabData.accept(ModItems.CRAZY_PHONE.get()))
                        .build());
    }
}
*///?}
