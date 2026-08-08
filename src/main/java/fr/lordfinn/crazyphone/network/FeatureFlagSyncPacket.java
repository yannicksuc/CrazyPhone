package fr.lordfinn.crazyphone.network;

import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.FeatureFlag;
import fr.lordfinn.crazyphone.client.ClientFeatureFlagState;

import java.util.HashMap;
import java.util.Map;

/**
 * Server -> one client: the effective (global-switch AND permission-node) enabled state of every
 * {@link FeatureFlag} for THIS specific player. Computed per-player (not a single broadcast payload) because
 * the permission-node half of {@link FeatureFlag#isEnabledFor} can differ per player under a permission
 * plugin like LuckPerms, even though the global switch is the same for everyone. Sent once on login and again
 * whenever an admin changes a global switch via {@code /crazyphone feature} - lets the UI (call icon, mic
 * icon, image-send icon, mayor vote button) grey itself out instead of silently no-op'ing when clicked.
 */
public record FeatureFlagSyncPacket(Map<String, Boolean> enabledStates) implements CustomPacketPayload {

    public static final Type<FeatureFlagSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "feature_flag_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FeatureFlagSyncPacket> STREAM_CODEC = StreamCodec.of(
            (RegistryFriendlyByteBuf buffer, FeatureFlagSyncPacket message) -> {
                buffer.writeVarInt(message.enabledStates.size());
                for (Map.Entry<String, Boolean> entry : message.enabledStates.entrySet()) {
                    buffer.writeUtf(entry.getKey());
                    buffer.writeBoolean(entry.getValue());
                }
            },
            (RegistryFriendlyByteBuf buffer) -> {
                int size = buffer.readVarInt();
                Map<String, Boolean> states = new HashMap<>();
                for (int i = 0; i < size; i++)
                    states.put(buffer.readUtf(), buffer.readBoolean());
                return new FeatureFlagSyncPacket(states);
            });

    @Override
    public Type<FeatureFlagSyncPacket> type() {
        return TYPE;
    }

    public static void handleData(final FeatureFlagSyncPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(() -> ClientFeatureFlagState.onPacket(message)).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }

    private static Map<String, Boolean> computeFor(ServerPlayer player) {
        Map<String, Boolean> states = new HashMap<>();
        for (FeatureFlag flag : FeatureFlag.values())
            states.put(flag.id, flag.isEnabledFor(player));
        return states;
    }

    /** Sent right after login (see PhoneAttachmentTypes#onPlayerLoggedIn). */
    public static void syncTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new FeatureFlagSyncPacket(computeFor(player)));
    }

    /** Sent whenever a global switch changes via /crazyphone feature - each online player gets their own
     * freshly-computed states, not one shared payload, since permission nodes can differ per player. */
    public static void syncToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            syncTo(player);
    }

    @EventBusSubscriber
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, FeatureFlagSyncPacket::handleData);
        }
    }
}
