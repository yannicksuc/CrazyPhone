package fr.lordfinn.crazyphone.network;

//? if legacyforge {
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Real, original Forge 1.20.1 has no PlayPayloadContext type of its own (that's a NeoForge-only
 * abstraction over the older NetworkEvent.Context) - this same-package shim reproduces its exact shape
 * (flow/workHandler/player/connection/packetHandler, javap-verified against NetworkEvent.Context) so every
 * packet's handleData(message, PlayPayloadContext) method body works completely unchanged on both loaders:
 * being in the same package as every packet record, it's picked up with no import needed at all, in place
 * of the neoforge-only "import net.neoforged.neoforge.network.handling.PlayPayloadContext" line.
 */
public final class PlayPayloadContext {
    private final NetworkEvent.Context ctx;

    public PlayPayloadContext(NetworkEvent.Context ctx) {
        this.ctx = ctx;
    }

    public PacketFlow flow() {
        return ctx.getDirection() == NetworkDirection.PLAY_TO_SERVER ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND;
    }

    public WorkHandler workHandler() {
        return new WorkHandler(ctx);
    }

    public Optional<Player> player() {
        return Optional.ofNullable(ctx.getSender());
    }

    public Connection connection() {
        return ctx.getNetworkManager();
    }

    public PacketHandler packetHandler() {
        return new PacketHandler(ctx);
    }

    public record WorkHandler(NetworkEvent.Context ctx) {
        public CompletableFuture<Void> submitAsync(Runnable task) {
            return ctx.enqueueWork(task);
        }
    }

    public record PacketHandler(NetworkEvent.Context ctx) {
        public void disconnect(Component reason) {
            ctx.getNetworkManager().disconnect(reason);
        }
    }
}
//?}
