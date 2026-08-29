package fr.lordfinn.crazyphone.fabric;

/**
 * Fabric-side equivalent of Crazyphone.addNetworkMessage (NeoForge): thin wrappers each packet file's
 * Fabric branch calls to register itself, invoked from the right entrypoint by ModPackets.java.
 *
 * Deliberately NOT a single "registerBidirectional" like NeoForge's playBidirectional: Fabric API's
 * ClientPlayNetworking class is client-only (references client-only Minecraft classes internally) - merely
 * resolving it as a method parameter type from code that runs on a dedicated server risks a
 * NoClassDefFoundError, so payload-TYPE registration (registerType, safe on both sides) is kept fully
 * separate from RECEIVER registration (registerClientReceiver only ever called from CrazyphoneFabricClient
 * #onInitializeClient; registerServerReceiver - ServerPlayNetworking is common-safe - called from
 * CrazyphoneFabric#onInitialize). ModPackets.java is the single place that knows which packets need which
 * calls, mirroring how Crazyphone.java's constructor is the single place that knows every NeoForge
 * DeferredRegister to register.
 */
//? if fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FabricNetworking {
    private FabricNetworking() {
    }

    public static <T extends CustomPacketPayload> void registerS2CType(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry./^$ fabric_payload_registry_s2c {^/playS2C/^$}^/().register(type, codec);
    }

    public static <T extends CustomPacketPayload> void registerC2SType(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry./^$ fabric_payload_registry_c2s {^/playC2S/^$}^/().register(type, codec);
    }

    // Only ever called from CrazyphoneFabricClient#onInitializeClient.
    public static <T extends CustomPacketPayload> void registerClientReceiver(
            CustomPacketPayload.Type<T> type, ClientPlayNetworking.PlayPayloadHandler<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, handler);
    }

    public static <T extends CustomPacketPayload> void registerServerReceiver(
            CustomPacketPayload.Type<T> type, ServerPlayNetworking.PlayPayloadHandler<T> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type, handler);
    }
}
*///?}
