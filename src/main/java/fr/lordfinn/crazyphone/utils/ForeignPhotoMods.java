package fr.lordfinn.crazyphone.utils;

/**
 * Recognizes a photo item belonging to one of two other, unrelated screenshot/photo mods a player might
 * also have installed - "Camera" (de.maxhenkel.camera) and "Camerapture" (me.chrr.camerapture) - and reads
 * it (id/owner/timestamp/bytes) well enough to import a copy into this mod's own {@link
 * fr.lordfinn.crazyphone.data.PhotoSavedData}. See {@link fr.lordfinn.crazyphone.utils.CrazyPhoneHelper#importForeignPhoto}
 * for the actual import, and {@link fr.lordfinn.crazyphone.item.CrazyPhoneItem} for the drag-the-phone-onto-
 * (or off-of)-a-foreign-item gesture that triggers it.
 * <p>
 * Neither mod is a compile-time dependency of this one (this project already avoids that for every
 * genuinely-optional integration - see build.gradle.kts's own doc comment on the Simple Voice Chat API for
 * why: compileOnly plus a runtime presence check, not a hard requirement). Reading their item data instead
 * goes entirely through reflection on each mod's own public API, so a version difference in either mod
 * simply fails the reflective lookup (caught below, same as "mod not installed at all") rather than needing
 * a matching compileOnly artifact per Minecraft version on top of this project's own 9-version matrix.
 * <p>
 * Both mods store the actual image bytes as a plain file under the world save folder rather than in NBT -
 * once the id is known, reading the bytes needs nothing from either mod's jar at all, installed or not (see
 * {@code #readWorldFile}).
 * <p>
 * NeoForge-only for now: both foreign mods only actually exist there in practice, and this reads the post-
 * 1.20.5 Data Components API ({@code ItemContainerContents}/{@code DataComponentType}) unconditionally,
 * which doesn't exist pre-1.20.5 (real Forge 1.20.1) at all - every other target gets an empty, harmless
 * stand-in below instead of a real porting pass no known Camera-mod/Camerapture build would even need yet.
 */
public final class ForeignPhotoMods {
    private ForeignPhotoMods() {
    }

    public record ForeignPhoto(java.util.UUID id, String owner, int createdMinutes, byte[] bytes) {
    }

    //? if neoforge {
    private static final net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/ CAMERA_IMAGE_ITEM = net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/.fromNamespaceAndPath("camera", "image");
    private static final net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/ CAMERA_ALBUM_ITEM = net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/.fromNamespaceAndPath("camera", "album");
    private static final net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/ CAMERAPTURE_PICTURE_ITEM = net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/.fromNamespaceAndPath("camerapture", "picture");
    private static final net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/ CAMERAPTURE_ALBUM_ITEM = net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/.fromNamespaceAndPath("camerapture", "album");

    public static boolean isForeignPhotoItem(net.minecraft.world.item.ItemStack stack) {
        return matchesItem(stack, CAMERA_IMAGE_ITEM) || matchesItem(stack, CAMERAPTURE_PICTURE_ITEM);
    }

    public static boolean isForeignAlbumItem(net.minecraft.world.item.ItemStack stack) {
        return matchesItem(stack, CAMERA_ALBUM_ITEM) || matchesItem(stack, CAMERAPTURE_ALBUM_ITEM);
    }

    private static boolean matchesItem(net.minecraft.world.item.ItemStack stack, net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/ id) {
        return !stack.isEmpty() && id.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    /** A single Camera-mod Image item or Camerapture Picture item, read via whichever mod's format it
     * actually matches. Null if neither mod recognizes this stack, the mod isn't installed, or the backing
     * file is gone. */
    @javax.annotation.Nullable
    public static ForeignPhoto readSingle(net.minecraft.server.MinecraftServer server, net.minecraft.world.item.ItemStack stack) {
        if (matchesItem(stack, CAMERA_IMAGE_ITEM))
            return readCameraModImage(server, stack);
        if (matchesItem(stack, CAMERAPTURE_PICTURE_ITEM))
            return readCamerapturePicture(server, stack);
        return null;
    }

    /** Every recoverable photo inside a Camera-mod Album or Camerapture Album item - both are a plain
     * vanilla {@code ItemContainerContents} of the mod's own photo item (the exact same component bundles
     * and shulker boxes use), so this needs no reflection at all to enumerate slots, only to read each
     * individual photo inside once found. */
    public static java.util.List<ForeignPhoto> readAlbum(net.minecraft.server.MinecraftServer server, net.minecraft.world.item.ItemStack stack) {
        java.util.List<ForeignPhoto> result = new java.util.ArrayList<>();
        if (!isForeignAlbumItem(stack))
            return result;
        net.minecraft.world.item.component.ItemContainerContents contents = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        if (contents == null)
            return result;
        // A plain indexed loop over getSlots()/getStackInSlot(int) rather than the convenience iterator -
        // that convenience method got renamed between versions (nonEmptyItemsCopy() pre-26, replaced by a
        // Stream-returning nonEmptyItemCopyStream() at 26.x - confirmed via javap on both jars), while these
        // two plain accessors are identical everywhere.
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            net.minecraft.world.item.ItemStack sub = contents.getStackInSlot(slot);
            if (sub.isEmpty())
                continue;
            ForeignPhoto photo = readSingle(server, sub);
            if (photo != null)
                result.add(photo);
        }
        return result;
    }

    @javax.annotation.Nullable
    private static ForeignPhoto readCameraModImage(net.minecraft.server.MinecraftServer server, net.minecraft.world.item.ItemStack stack) {
        try {
            Class<?> imageDataClass = Class.forName("de.maxhenkel.camera.ImageData");
            Object imageData = imageDataClass.getMethod("fromStack", net.minecraft.world.item.ItemStack.class).invoke(null, stack);
            if (imageData == null)
                return null;
            java.util.UUID id = (java.util.UUID) imageDataClass.getMethod("getId").invoke(imageData);
            String owner = (String) imageDataClass.getMethod("getOwner").invoke(imageData);
            long timeMillis = (long) imageDataClass.getMethod("getTime").invoke(imageData);
            byte[] bytes = readWorldFile(server, "camera_images", id, ".jpg", ".png");
            if (bytes == null)
                return null;
            return new ForeignPhoto(id, owner, (int) (timeMillis / 60000L), bytes);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    // Camerapture stores its pictures as WebP files, a format the game's own NativeImage/STB-backed decoder
    // (see FabricPictureCache#decodeAndRegister) cannot read at all - a photo imported from here is real and
    // correctly tracked (right conversation/owner/timestamp), but its thumbnail/viewer will show the same
    // "failed to load" placeholder a corrupt photo already does, until this mod ships its own WebP decode
    // path. Tracked as a known follow-up, not silently swallowed.
    @javax.annotation.Nullable
    private static ForeignPhoto readCamerapturePicture(net.minecraft.server.MinecraftServer server, net.minecraft.world.item.ItemStack stack) {
        try {
            Class<?> camerapture = Class.forName("me.chrr.camerapture.Camerapture");
            net.minecraft.core.component.DataComponentType<?> componentType =
                    (net.minecraft.core.component.DataComponentType<?>) camerapture.getField("PICTURE_DATA").get(null);
            Object pictureData = stack.get(componentType);
            if (pictureData == null)
                return null;
            // Camerapture's PictureItem.PictureData is a record - id()/creator()/timestamp() are its own
            // component accessors, not a getX() bean convention.
            Class<?> pictureDataClass = pictureData.getClass();
            java.util.UUID id = (java.util.UUID) pictureDataClass.getMethod("id").invoke(pictureData);
            String creator = (String) pictureDataClass.getMethod("creator").invoke(pictureData);
            long timestampMillis = (long) pictureDataClass.getMethod("timestamp").invoke(pictureData);
            byte[] bytes = readWorldFile(server, "camerapture", id, ".webp");
            if (bytes == null)
                return null;
            return new ForeignPhoto(id, creator, (int) (timestampMillis / 60000L), bytes);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    private static byte[] readWorldFile(net.minecraft.server.MinecraftServer server, String worldRelativeFolder, java.util.UUID id, String... extensionsToTry) {
        // LevelResource's own (String) constructor is private on vanilla/Fabric mappings - only NeoForge/
        // Forge access-transform it open - so this resolves from the one guaranteed-public constant every
        // mapping set exposes (the world's own root folder) instead of minting a custom resource, same fix
        // as LegacyPhotoMigration's own doc comment on this exact issue.
        java.nio.file.Path folder = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve(worldRelativeFolder);
        for (String extension : extensionsToTry) {
            java.nio.file.Path file = folder.resolve(id + extension);
            if (!java.nio.file.Files.exists(file))
                continue;
            try {
                return java.nio.file.Files.readAllBytes(file);
            } catch (java.io.IOException e) {
                return null;
            }
        }
        return null;
    }
    //?}
}
