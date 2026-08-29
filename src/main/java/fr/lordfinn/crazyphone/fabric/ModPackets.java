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
        fr.lordfinn.crazyphone.network.PlayerPhoneStateSyncPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.ConversationCallActivitySyncPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneGroupMembershipNotificationPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneNewMessageNotificationPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.UpdateContactInfoMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneDefaultScreenButtonMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyphoneHomeScreenButtonMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneContactsScreenButtonMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneGroupSettingsButtonMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneMayorsCandidatesButtonMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhonePasswordScreenButtonMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneSignInScreenButtonMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneContactInfoScreenButtonMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneUploadPicturePacket.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhonePictureRequestPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneGivePhotoItemPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneMyPhotosActionMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhonePictureResponsePacket.registerFabricType();
        fr.lordfinn.crazyphone.network.ConversationRequestPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.ConversationResponsePacket.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.CrazyPhoneConversationButtonMessage.registerFabricType();
        fr.lordfinn.crazyphone.network.VoiceMessageAudioRequestPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.VoiceMessageStopPacket.registerFabricType();
        fr.lordfinn.crazyphone.network.VoiceMessageUploadPacket.registerFabricType();
    }

    // Called from CrazyphoneFabricClient#onInitializeClient - registers every server->client receiver.
    public static void registerClient() {
        fr.lordfinn.crazyphone.network.FeatureFlagSyncPacket.registerFabricClientReceiver();
        fr.lordfinn.crazyphone.network.PhoneRegistrySyncPacket.registerFabricClientReceiver();
        fr.lordfinn.crazyphone.network.PlayerPhoneStateSyncPacket.registerFabricClientReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.registerFabricClientReceiver();
        fr.lordfinn.crazyphone.network.ConversationCallActivitySyncPacket.registerFabricClientReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneGroupMembershipNotificationPacket.registerFabricClientReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneNewMessageNotificationPacket.registerFabricClientReceiver();
        fr.lordfinn.crazyphone.network.UpdateContactInfoMessage.registerFabricClientReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhonePictureResponsePacket.registerFabricClientReceiver();
        fr.lordfinn.crazyphone.network.ConversationResponsePacket.registerFabricClientReceiver();
    }

    // Called from CrazyphoneFabric#onInitialize - registers every client->server receiver (safe on
    // dedicated server too, ServerPlayNetworking is common-safe - see FabricNetworking's own doc comment).
    public static void registerServer() {
        fr.lordfinn.crazyphone.network.CrazyPhoneDefaultScreenButtonMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyphoneHomeScreenButtonMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneContactsScreenButtonMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneGroupSettingsButtonMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneMayorsCandidatesButtonMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhonePasswordScreenButtonMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneSignInScreenButtonMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneContactInfoScreenButtonMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneUploadPicturePacket.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhonePictureRequestPacket.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneGivePhotoItemPacket.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneMyPhotosActionMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.ConversationRequestPacket.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.CrazyPhoneConversationButtonMessage.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.VoiceMessageAudioRequestPacket.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.VoiceMessageStopPacket.registerFabricServerReceiver();
        fr.lordfinn.crazyphone.network.VoiceMessageUploadPacket.registerFabricServerReceiver();
    }
}
*///?}
