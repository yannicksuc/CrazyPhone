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
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.voicechat.SvcCallBridge;
import fr.lordfinn.crazyphone.voicechat.VoicechatIntegration;

import java.util.function.Supplier;

@EventBusSubscriber
public class PhoneAttachmentTypes {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Crazyphone.MODID);

    public static final Supplier<AttachmentType<PlayerPhoneState>> PLAYER_PHONE_STATE =
            ATTACHMENT_TYPES.register("player_phone_state", () -> AttachmentType.serializable(PlayerPhoneState::new).build());

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CrazyPhoneHelper.reconcilePhoneStateOnJoin(player);
            // CallRegistry itself is in-memory and always starts empty on a fresh server boot (see its own
            // javadoc), but Simple Voice Chat's own per-connection group assignment is tracked entirely
            // outside CrazyPhone and isn't reset by that - a player who reconnects mid-call (crash, forced
            // kill, alt-F4 instead of a graceful hangup) can come back still assigned to a now-orphaned SVC
            // group nobody else is in. Unconditionally clearing it here is never wrong for the same reason
            // reconcilePhoneStateOnJoin's own clearing isn't: a genuinely still-active call session gets its
            // real group membership pushed back moments later by the normal join/answer flow.
            if (VoicechatIntegration.isAvailable())
                SvcCallBridge.leaveGroup(player.getUUID());
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
