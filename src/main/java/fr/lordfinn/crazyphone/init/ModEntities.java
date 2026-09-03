package fr.lordfinn.crazyphone.init;

//? if neoforge {
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
//?}
//? if fabric {
/*import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import fr.lordfinn.crazyphone.utils.RegistryEntry;
*///?}

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.Registries;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.entity.CrazyPhonePhotoFrameEntity;

/**
 * The mod's first (and so far only) custom entity - see {@link CrazyPhonePhotoFrameEntity}'s own doc comment
 * for why it isn't a vanilla HangingEntity. >=1.20.5-only across both loaders: the wall-photo feature this
 * backs was built directly against the Data Components / registry-based enchantment API (Silk Touch lookup
 * in the entity's own {@code hurt()}), matching this project's own established scope line for new features
 * (1.20.4/1.20.1-fabric are frozen/unmaintained anyway - see the README's own maintenance table). Three
 * independent top-level blocks below (not one nested inside another's else) - see
 * CrazyPhoneSelfieCameraMixin's own doc comment for why nesting a version toggle inside an already-wrapped
 * loader toggle corrupts Stonecutter's output; this file follows the same established flat-sibling shape.
 */
// EntityType.Builder#build's own String-id convenience overload is gone on >=26 - confirmed against the
// real decompiled EntityType.java, only build(ResourceKey<EntityType<?>>) remains there. <26 still accepts
// either; kept as the simpler .toString() form there to match every other .build(...)/.register(...) call
// site elsewhere in this codebase.
//? if neoforge && >=1.20.5 <26 {
/*public class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(Registries.ENTITY_TYPE, Crazyphone.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CrazyPhonePhotoFrameEntity>> PHOTO_FRAME =
            REGISTRY.register("photo_frame", () -> EntityType.Builder.<CrazyPhonePhotoFrameEntity>of(CrazyPhonePhotoFrameEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(10)
                    .build(Crazyphone.resource("photo_frame").toString()));
}
*///?}
//? if neoforge && >=26 {
/*public class ModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(Registries.ENTITY_TYPE, Crazyphone.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CrazyPhonePhotoFrameEntity>> PHOTO_FRAME =
            REGISTRY.register("photo_frame", () -> EntityType.Builder.<CrazyPhonePhotoFrameEntity>of(CrazyPhonePhotoFrameEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f)
                    .clientTrackingRange(10)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, Crazyphone.resource("photo_frame"))));
}
*///?}
//? if fabric && >=1.20.5 <26 {
/*public class ModEntities {
    public static RegistryEntry<EntityType<CrazyPhonePhotoFrameEntity>> PHOTO_FRAME;

    public static void register() {
        EntityType<CrazyPhonePhotoFrameEntity> type = EntityType.Builder.<CrazyPhonePhotoFrameEntity>of(CrazyPhonePhotoFrameEntity::new, MobCategory.MISC)
                .sized(1.0f, 1.0f)
                .clientTrackingRange(10)
                .build(Crazyphone.resource("photo_frame").toString());
        PHOTO_FRAME = new RegistryEntry<>(Registry.register(BuiltInRegistries.ENTITY_TYPE, Crazyphone.resource("photo_frame"), type));
    }
}
*///?}
//? if fabric && >=26 {
/*public class ModEntities {
    public static RegistryEntry<EntityType<CrazyPhonePhotoFrameEntity>> PHOTO_FRAME;

    public static void register() {
        EntityType<CrazyPhonePhotoFrameEntity> type = EntityType.Builder.<CrazyPhonePhotoFrameEntity>of(CrazyPhonePhotoFrameEntity::new, MobCategory.MISC)
                .sized(1.0f, 1.0f)
                .clientTrackingRange(10)
                .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, Crazyphone.resource("photo_frame")));
        PHOTO_FRAME = new RegistryEntry<>(Registry.register(BuiltInRegistries.ENTITY_TYPE, Crazyphone.resource("photo_frame"), type));
    }
}
*///?}
// Inert placeholder on every pre-1.20.5 target (1.20.4, 1.20.1-fabric - both frozen/unmaintained, see the
// README's own maintenance table) - no photo-frame entity there at all, and neither loader's own real
// register()/REGISTRY branch above gets stonecut into the build on those targets.
//? if <1.20.5 {
public class ModEntities {
    public static void register() {
    }
}
//?}
