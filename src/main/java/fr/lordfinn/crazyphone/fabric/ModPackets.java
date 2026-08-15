package fr.lordfinn.crazyphone.fabric;

/**
 * Central list of every packet's Fabric registration, mirroring how Crazyphone.java's NeoForge constructor
 * is the single place that knows every DeferredRegister to register. Split into two calls matching Fabric's
 * two safe entrypoints - see FabricNetworking's own doc comment for why the client-receiver half can't be
 * merged into the common half.
 */
//? if fabric && >=1.20.5 {
/*public final class ModPackets {
    private ModPackets() {
    }

    // Called from CrazyphoneFabric#onInitialize - safe on both dedicated server and client.
    public static void registerCommon() {
        fr.lordfinn.crazyphone.network.FeatureFlagSyncPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.PhoneRegistrySyncPacket.registerFabricType();
    }

    // Called from CrazyphoneFabricClient#onInitializeClient - registers every server->client receiver.
    public static void registerClient() {
        fr.lordfinn.crazyphone.network.FeatureFlagSyncPacket.registerFabricClientReceiver();
        fr.lordfinn.crazyphone.network.PhoneRegistrySyncPacket.registerFabricClientReceiver();
    }
}
*///?}
