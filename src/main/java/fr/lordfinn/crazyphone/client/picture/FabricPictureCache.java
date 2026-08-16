package fr.lordfinn.crazyphone.client.picture;

/**
 * Client-side texture cache for the Fabric-native picture pipeline (task #165): lazily fetches an image
 * message's PNG bytes the first time its texture is asked for (see {@link #getOrRequest}), decodes and
 * uploads it as a {@link net.minecraft.client.renderer.texture.DynamicTexture}, and caches the resulting
 * ResourceLocation by image id so a scrolled-past-and-back message doesn't refetch. Same lazy/on-demand
 * shape as VoiceMessageRecorder's audio fetch, just for a texture instead of playback.
 */
//? if fabric && >=1.20.5 {
/*import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import fr.lordfinn.crazyphone.network.CrazyPhonePictureRequestPacket;
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricPictureCache {
    private static final Map<UUID, ResourceLocation> RESOLVED = new ConcurrentHashMap<>();
    private static final Set<UUID> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    // A failed decode (corrupt/truncated bytes) shouldn't retry every single frame the message is on
    // screen - remembered so the caller can fall back to a placeholder instead of hammering the server.
    private static final Set<UUID> FAILED = ConcurrentHashMap.newKeySet();

    private FabricPictureCache() {
    }

    // Returns the cached texture for this image, or null while it's still being fetched/decoded (and kicks
    // off that fetch on first call) - callers should draw a placeholder until this returns non-null.
    public static ResourceLocation getOrRequest(UUID imageId) {
        ResourceLocation cached = RESOLVED.get(imageId);
        if (cached != null)
            return cached;
        if (FAILED.contains(imageId))
            return null;
        if (IN_FLIGHT.add(imageId))
            NetworkAccess.sendToServer(new CrazyPhonePictureRequestPacket(imageId));
        return null;
    }

    public static void onBytesReceived(UUID imageId, byte[] pngBytes) {
        IN_FLIGHT.remove(imageId);
        if (pngBytes.length == 0) {
            FAILED.add(imageId);
            return;
        }
        try {
            NativeImage image = NativeImage.read(pngBytes);
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation id = Minecraft.getInstance().getTextureManager().register("crazyphone-picture-" + imageId, texture);
            RESOLVED.put(imageId, id);
        } catch (Exception e) {
            FAILED.add(imageId);
        }
    }
}
*///?}
