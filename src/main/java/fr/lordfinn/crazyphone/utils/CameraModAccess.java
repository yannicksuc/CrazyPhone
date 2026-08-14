package fr.lordfinn.crazyphone.utils;

import de.maxhenkel.camera.items.CameraItem;
import net.minecraft.world.item.Item;

/** Single choke point for reaching Camera mod's top-level static registry class, which was renamed from
 *  {@code Main} to {@code CameraMod} between the 1.21.1 build (1.0.21) and the 1.21.10 build (1.1.8) - every
 *  call site in the mod goes through this instead of referencing {@code Main}/{@code CameraMod} directly, so
 *  porting to a version with yet another rename only means rewriting this one file. */
public final class CameraModAccess {
    private CameraModAccess() {
    }

    public static CameraItem cameraItem() {
        //? if <1.21.10 {
        return (CameraItem) de.maxhenkel.camera.Main.CAMERA.get();
        //? } else {
        /*return (CameraItem) de.maxhenkel.camera.CameraMod.CAMERA.get();
        *///?}
    }

    public static Item imageItem() {
        //? if <1.21.10 {
        return de.maxhenkel.camera.Main.IMAGE.get();
        //? } else {
        /*return de.maxhenkel.camera.CameraMod.IMAGE.get();
        *///?}
    }

    public static Item albumItem() {
        //? if <1.21.10 {
        return de.maxhenkel.camera.Main.ALBUM.get();
        //? } else {
        /*return de.maxhenkel.camera.CameraMod.ALBUM.get();
        *///?}
    }

    // shaderDataComponent()/imageDataComponent() return a DeferredHolder (not the unwrapped DataComponentType -
    // ItemStack#set/has/get all accept a Supplier<DataComponentType<T>> directly, which DeferredHolder already
    // implements), and DeferredHolder doesn't exist pre-1.20.5 - both are only ever called from already
    // >=1.20.5-guarded call sites, so they're simply absent on 1.20.4.
    //? if >=1.20.5 <1.21.10 {
    /*public static net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<String>> shaderDataComponent() {
        return de.maxhenkel.camera.Main.SHADER_DATA_COMPONENT;
    }

    public static net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<de.maxhenkel.camera.ImageData>> imageDataComponent() {
        return de.maxhenkel.camera.Main.IMAGE_DATA_COMPONENT;
    }
    *///?}
    //? if >=1.21.10 {
    /*public static net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<String>> shaderDataComponent() {
        return de.maxhenkel.camera.CameraMod.SHADER_DATA_COMPONENT;
    }

    public static net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.core.component.DataComponentType<?>, net.minecraft.core.component.DataComponentType<de.maxhenkel.camera.ImageData>> imageDataComponent() {
        return de.maxhenkel.camera.CameraMod.IMAGE_DATA_COMPONENT;
    }
    *///?}

    public static de.maxhenkel.camera.net.PacketManager packetManager() {
        //? if <1.21.10 {
        return de.maxhenkel.camera.Main.PACKET_MANAGER;
        //? } else {
        /*return de.maxhenkel.camera.CameraMod.PACKET_MANAGER;
        *///?}
    }
}
