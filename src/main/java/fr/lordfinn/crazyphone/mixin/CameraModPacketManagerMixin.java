package fr.lordfinn.crazyphone.mixin;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.gen.Accessor;

import de.maxhenkel.camera.ImageData;
import de.maxhenkel.camera.ImageTools;
import de.maxhenkel.camera.net.PacketManager;
import fr.lordfinn.crazyphone.utils.CameraModAccess;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

@Mixin(PacketManager.class)
public abstract class CameraModPacketManagerMixin {

    @Accessor("clientDataMap")
    public abstract Map<UUID, byte[]> getClientDataMap();

    @Accessor("imageCache")
    public abstract Map<UUID, BufferedImage> getImageCache();

    @Overwrite
    public void addBytes(ServerPlayer player, UUID imageID, int offset, int length, byte[] bytes) {
        byte[] data = prepareOrGetByteArray(imageID, length);
        System.arraycopy(bytes, 0, data, offset, bytes.length);
        getClientDataMap().put(imageID, data);

        if (offset + bytes.length >= data.length) {
            handleImageCompletion(player, imageID);
        }
    }

    private byte[] prepareOrGetByteArray(UUID imageID, int length) {
        return getClientDataMap().getOrDefault(imageID, new byte[length]);
    }

    private void handleImageCompletion(ServerPlayer player, UUID imageID) {
        try {
            BufferedImage image = invokeCompleteImage(imageID);
            if (image == null)
                throw new IOException("Image incomplete");

            getImageCache().put(imageID, image);

            new Thread(() -> saveImageAndGiveItem(player, imageID, image), "SaveImageThread").start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BufferedImage invokeCompleteImage(UUID imageID) throws Exception {
        Method completeImageMethod = PacketManager.class.getDeclaredMethod("completeImage", UUID.class);
        completeImageMethod.setAccessible(true);
        return (BufferedImage) completeImageMethod.invoke(this, imageID);
    }

    private void saveImageAndGiveItem(ServerPlayer player, UUID imageID, BufferedImage image) {
        try {
            ImageTools.saveImage(player, imageID, image);

            player.level().getServer().submitAsync(() -> {
                ItemStack imageStack = new ItemStack(CameraModAccess.imageItem());
                ImageData imageData = ImageData.create(player, imageID);
                imageData.addToImage(imageStack);

                if (CameraModHelper.tryInsertImageIntoCrazyPhone(player, imageStack))
                    return;

                if (!player.addItem(imageStack)) {
                    Containers.dropItemStack(player.level(), player.getX(), player.getY(), player.getZ(), imageStack);
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
