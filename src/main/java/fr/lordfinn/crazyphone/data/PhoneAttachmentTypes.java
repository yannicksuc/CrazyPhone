package fr.lordfinn.crazyphone.data;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.network.FeatureFlagSyncPacket;

import java.util.function.Supplier;

@EventBusSubscriber
public class PhoneAttachmentTypes {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Crazyphone.MODID);

    public static final Supplier<AttachmentType<PlayerPhoneState>> PLAYER_PHONE_STATE =
            ATTACHMENT_TYPES.register("player_phone_state", () -> AttachmentType.serializable(PlayerPhoneState::new).build());

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getData(PLAYER_PHONE_STATE).syncTo(player);
            PhoneRegistrySavedData.get(player.level()).syncTo(player);
            FeatureFlagSyncPacket.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            player.getData(PLAYER_PHONE_STATE).syncTo(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            player.getData(PLAYER_PHONE_STATE).syncTo(player);
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        PlayerPhoneState original = event.getOriginal().getData(PLAYER_PHONE_STATE);
        PlayerPhoneState clone = new PlayerPhoneState();
        if (!event.isWasDeath()) {
            clone.currentCrazyPhoneScreenOpened = original.currentCrazyPhoneScreenOpened;
            clone.crazyPhoneScreenHistory = original.crazyPhoneScreenHistory;
        }
        event.getEntity().setData(PLAYER_PHONE_STATE, clone);
    }
}
