package fr.lordfinn.crazyphone.data;

//? if neoforge {
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//?}
//? if fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
*///?}
// Real, original Forge 1.20.1 has no generic Attachment system of its own (that's a NeoForge-only
// abstraction added at the fork) - the idiomatic old-Forge equivalent for small persisted per-entity data
// is its (heavier, but stable since 1.7) Capability system: a Capability<T> token, an ICapabilityProvider
// added per-entity via AttachCapabilitiesEvent<Entity>, wrapping the exact same INBTSerializable-
// implementing PlayerPhoneState/SoulboundStash objects the neoforge <1.21.10 branch already uses (see
// those classes' own legacyforge import blocks) - so the only genuinely new code here is the plumbing,
// not the data classes themselves.
//? if legacyforge {
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
//?}

import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.network.FeatureFlagSyncPacket;
//? if neoforge || legacyforge {
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.voicechat.SvcCallBridge;
import fr.lordfinn.crazyphone.voicechat.VoicechatIntegration;
//?}

import java.util.function.Supplier;

//? if neoforge {
@EventBusSubscriber
public class PhoneAttachmentTypes {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Crazyphone.MODID);

    public static final Supplier<AttachmentType<PlayerPhoneState>> PLAYER_PHONE_STATE =
            ATTACHMENT_TYPES.register("player_phone_state", () -> AttachmentType.serializable(PlayerPhoneState::new).build());

    /** See {@link SoulboundStash} - only ever non-empty for the brief window between a death that pulled
     * soulbound items out of the drops and the respawn that reinserts them. */
    public static final Supplier<AttachmentType<SoulboundStash>> SOULBOUND_STASH =
            ATTACHMENT_TYPES.register("soulbound_stash", () -> AttachmentType.serializable(SoulboundStash::new).build());

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
            LegacyPhotoMigration.importPhoneAlbumsOnLogin(player);
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
//?}
//? if legacyforge {
@EventBusSubscriber
public class PhoneAttachmentTypes {
    public static final Capability<PlayerPhoneState> PLAYER_PHONE_STATE_CAP =
            CapabilityManager.get(new CapabilityToken<PlayerPhoneState>() {
            });

    /** See {@link SoulboundStash} - only ever non-empty for the brief window between a death that pulled
     * soulbound items out of the drops and the respawn that reinserts them. */
    public static final Capability<SoulboundStash> SOULBOUND_STASH_CAP =
            CapabilityManager.get(new CapabilityToken<SoulboundStash>() {
            });

    public static PlayerPhoneState getPlayerPhoneState(Player player) {
        return player.getCapability(PLAYER_PHONE_STATE_CAP, null).orElseGet(PlayerPhoneState::new);
    }

    public static SoulboundStash getSoulboundStash(Player player) {
        return player.getCapability(SOULBOUND_STASH_CAP, null).orElseGet(SoulboundStash::new);
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(Crazyphone.resource("player_phone_state"), new ICapabilityProvider() {
                private final LazyOptional<PlayerPhoneState> instance = LazyOptional.of(PlayerPhoneState::new);

                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                    return cap == PLAYER_PHONE_STATE_CAP ? instance.cast() : LazyOptional.empty();
                }
            });
            event.addCapability(Crazyphone.resource("soulbound_stash"), new ICapabilityProvider() {
                private final LazyOptional<SoulboundStash> instance = LazyOptional.of(SoulboundStash::new);

                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                    return cap == SOULBOUND_STASH_CAP ? instance.cast() : LazyOptional.empty();
                }
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CrazyPhoneHelper.reconcilePhoneStateOnJoin(player);
            if (VoicechatIntegration.isAvailable())
                SvcCallBridge.leaveGroup(player.getUUID());
            getPlayerPhoneState(player).syncTo(player);
            PhoneRegistrySavedData.get(player.level()).syncTo(player);
            FeatureFlagSyncPacket.syncTo(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            getPlayerPhoneState(player).syncTo(player);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            getPlayerPhoneState(player).syncTo(player);
    }

    // The new player instance for the respawn/clone already got its own fresh PlayerPhoneState attached by
    // AttachCapabilitiesEvent<Entity> above (empty, by construction) - unlike NeoForge's setData (which
    // replaces the whole attachment instance outright), an old-Forge capability instance is a fixed
    // container attached once at entity-creation time, so "restoring" data onto it means mutating that
    // already-attached instance's own fields in place instead.
    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        if (event.isWasDeath())
            return;
        PlayerPhoneState original = getPlayerPhoneState(event.getOriginal());
        PlayerPhoneState target = getPlayerPhoneState(event.getEntity());
        target.currentCrazyPhoneScreenOpened = original.currentCrazyPhoneScreenOpened;
        target.crazyPhoneScreenHistory = original.crazyPhoneScreenHistory;
    }
}
//?}
//? if fabric && >=1.20.5 {
/*// Fabric's data-attachment-api-v1 (net.fabricmc.fabric.api.attachment.v1) is the equivalent of NeoForge's
// AttachmentType: AttachmentRegistry.createPersistent takes a Codec instead of an INBTSerializable-style
// interface (see PlayerPhoneState.java), and every Entity implements AttachmentTarget via mixin, so a plain
// cast - not a capability lookup - is how getAttached/setAttached are reached (this is the API's own
// documented usage pattern, not a workaround).
public class PhoneAttachmentTypes {
    public static final AttachmentType<PlayerPhoneState> PLAYER_PHONE_STATE = AttachmentRegistry.createPersistent(
            Crazyphone.resource("player_phone_state"), PlayerPhoneState.CODEC);

    // See SoulboundStash's own javadoc - only ever non-empty for the brief window between a death that
    // pulled soulbound items out of the inventory and the respawn that reinserts them (see
    // SoulboundHandler.java's Fabric branch).
    public static final AttachmentType<SoulboundStash> SOULBOUND_STASH = AttachmentRegistry.createPersistent(
            Crazyphone.resource("soulbound_stash"), SoulboundStash.CODEC);

    public static void register() {
        ServerPlayerEvents.JOIN.register(player -> {
            // TODO(#161/#164 follow-up): CrazyPhoneHelper.reconcilePhoneStateOnJoin and the SVC leaveGroup
            // call aren't wired here yet - CrazyPhoneHelper.java isn't in the Fabric build's include list
            // (it still needs several unported packets/procedures, see build.fabric.gradle.kts), and the
            // voicechat package hasn't been audited for Fabric yet either.
            ((AttachmentTarget) player).getAttachedOrCreate(PLAYER_PHONE_STATE, PlayerPhoneState::new).syncTo(player);
            PhoneRegistrySavedData.get(player.level()).syncTo(player);
            FeatureFlagSyncPacket.syncTo(player);
        });

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                ((AttachmentTarget) newPlayer).getAttachedOrCreate(PLAYER_PHONE_STATE, PlayerPhoneState::new).syncTo(newPlayer));

        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            PlayerPhoneState clone = new PlayerPhoneState();
            if (alive) {
                PlayerPhoneState original = ((AttachmentTarget) oldPlayer).getAttached(PLAYER_PHONE_STATE);
                if (original != null) {
                    clone.currentCrazyPhoneScreenOpened = original.currentCrazyPhoneScreenOpened;
                    clone.crazyPhoneScreenHistory = original.crazyPhoneScreenHistory;
                }
            }
            ((AttachmentTarget) newPlayer).setAttached(PLAYER_PHONE_STATE, clone);
        });
    }
}
*///?}
