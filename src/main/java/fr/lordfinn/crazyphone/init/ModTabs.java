package fr.lordfinn.crazyphone.init;

//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if fabric && <26 {
/*import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
*///?}
//? if fabric {
/*import net.minecraft.core.Registry;
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
//? if fabric && <26 {
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
//? if fabric && >=26 {
/*// fabric-item-group-api-v1 isn't even a transitive fabric-api dependency anymore on 26.x (confirmed
// against the real resolved dependency tree) - FabricItemGroup was only ever a thin CreativeModeTab.Builder
// wrapper minus withSearchBar() (see the <26 branch's own comment above), so plain vanilla
// CreativeModeTab.builder(...) replaces it directly here. Unlike the neoforge branch (compiled against
// NeoForge's own patched Minecraft jar, where a no-arg builder() convenience overload exists),
// true/plain-vanilla Minecraft - what Fabric Loom actually compiles against - only ever had the
// (Row, int) overload; Row.TOP/column 0 is an arbitrary placement, this mod has no ordering requirement
// against vanilla's or other mods' tabs.
public class ModTabs {
    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Crazyphone.resource("crazy_phone_tab"),
                CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                        .title(Component.translatable("item_group.crazyphone.crazy_phone_tab"))
                        .icon(() -> ModItems.CRAZY_PHONE.get().getDefaultInstance())
                        .displayItems((parameters, tabData) -> tabData.accept(ModItems.CRAZY_PHONE.get()))
                        .build());
    }
}
*///?}
