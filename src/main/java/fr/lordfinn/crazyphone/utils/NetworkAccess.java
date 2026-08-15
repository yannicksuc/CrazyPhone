package fr.lordfinn.crazyphone.utils;

//? if >=1.20.5 {
/*import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
*///?}
//? if fabric && >=1.20.5 {
/*import net.minecraft.server.level.ServerPlayer;
*///?}

/** Single choke point for sending a packet, on either loader:
 *  - sendToServer (client -> server) moved from {@code PacketDistributor.sendToServer} onto its own
 *    {@code ClientPacketDistributor} class as of NeoForge 21.10 (1.21.10).
 *  - sendToPlayer (server -> client) is {@code PacketDistributor.sendToPlayer(player, ...)} on NeoForge
 *    (>=1.20.5 - this whole file is currently >=1.20.5-only, see below) or Fabric's
 *    {@code ServerPlayNetworking.send(player, payload)} on Fabric.
 *  Every call site in the mod goes through this instead of calling the loader-specific class directly, so
 *  a future relocation - or a new loader - only means rewriting this one file.
 *
 *  Scoped to >=1.20.5 only for now: CustomPacketPayload (the type every packet in this mod is built on)
 *  doesn't exist before 1.20.5 at all - pre-1.20.5 networking is a completely different vanilla API
 *  (raw ResourceLocation + FriendlyByteBuf), which no NeoForge node in this project has ever actually
 *  compiled against (the 3 NeoForge targets are all >=1.20.5; the codebase's existing "//? if <1.20.5"
 *  network branches are vestigial, written for a hypothetical 1.20.1 NeoForge target that's blocked - see
 *  task list). 1.20.1-fabric is the first real <1.20.5 compile target this project has ever had; its
 *  networking layer needs its own dedicated pass, not a bolt-on here.
 *
 *  Every branch below uses a single flat, compound condition (e.g. "neoforge >=1.21.10") rather than
 *  nesting a loader-only //? if inside (or around) a version-only //? if - Stonecutter processes boolean-
 *  constant predicates (fabric/neoforge) and semver-range predicates via genuinely different internal
 *  machinery, and nesting one kind inside the other produces corrupted output (confirmed empirically: the
 *  nested form silently leaked an inner branch's real code into what should have been an outer commented-
 *  out region, breaking compilation on the "wrong" loader). Flat compound conditions avoid the interaction
 *  entirely. */
public final class NetworkAccess {
    private NetworkAccess() {
    }

    //? if neoforge && >=1.20.5 <1.21.10 {
    /*public static void sendToServer(CustomPacketPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
    }
    *///?}
    //? if neoforge && >=1.21.10 {
    /*public static void sendToServer(CustomPacketPayload payload) {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
    }
    *///?}
    //? if fabric && >=1.20.5 {
    /*public static void sendToServer(CustomPacketPayload payload) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(payload);
    }
    *///?}

    //? if neoforge && >=1.20.5 {
    /*public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, CustomPacketPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
    }
    *///?}
    //? if fabric && >=1.20.5 {
    /*public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    *///?}
}
