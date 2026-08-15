package fr.lordfinn.crazyphone.utils;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//? if fabric {
/*import net.minecraft.server.level.ServerPlayer;
*///?}

/** Single choke point for sending a packet, on either loader:
 *  - sendToServer (client -> server) moved from {@code PacketDistributor.sendToServer} onto its own
 *    {@code ClientPacketDistributor} class as of NeoForge 21.10 (1.21.10), and before that from
 *    {@code PacketDistributor.SERVER.noArg().send(...)} to {@code PacketDistributor.sendToServer(...)} at
 *    1.20.5 alongside the StreamCodec-based payload registration rewrite.
 *  - sendToPlayer (server -> client) is {@code PacketDistributor.PLAYER.with(player).send(...)} pre-1.20.5
 *    vs {@code PacketDistributor.sendToPlayer(player, ...)} from 1.20.5 onward on NeoForge, or Fabric's
 *    {@code ServerPlayNetworking.send(player, payload)} on Fabric.
 *  Every call site in the mod goes through this instead of calling the loader-specific class directly, so
 *  a future relocation - or a new loader - only means rewriting this one file.
 *
 *  IMPORTANT: CustomPacketPayload itself has existed since 1.20.2 (well before the "//? if >=1.20.5"
 *  boundary this codebase otherwise mostly uses for the Data Components rewrite) - every packet record in
 *  network/ already implements it unconditionally, old-style (id()/buffer constructor/write()) below
 *  1.20.5 and StreamCodec-based from 1.20.5 on. That means the "<1.20.5" branches below are NOT vestigial
 *  the way most other "<1.20.5" NeoForge branches in this codebase are (written for a hypothetical, never-
 *  built 1.20.1 NeoForge target) - our actual, currently-shipping 1.20.4 NeoForge node IS <1.20.5 and DOES
 *  compile and exercise this exact branch. Confirmed the hard way: an earlier version of this file only had
 *  a >=1.20.5 branch and broke :1.20.4:compileJava.
 *
 *  Every branch below uses a single flat, compound condition (e.g. "neoforge >=1.21.10") rather than
 *  nesting a loader-only //? if inside (or around) a version-only //? if - Stonecutter processes boolean-
 *  constant predicates (fabric/neoforge) and semver-range predicates via genuinely different internal
 *  machinery, and nesting one kind inside the other produces corrupted output (confirmed empirically: the
 *  nested form silently leaked an inner branch's real code into what should have been an outer commented-
 *  out region, breaking compilation on the "wrong" loader). Flat compound conditions avoid the interaction
 *  entirely. Fabric is scoped to >=1.20.5 only for now - 1.20.1-fabric's networking needs its own pass
 *  (pre-1.20.2 Fabric networking is a different API family entirely, not just a different payload shape). */
public final class NetworkAccess {
    private NetworkAccess() {
    }

    //? if neoforge && <1.20.5 {
    public static void sendToServer(CustomPacketPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.SERVER.noArg().send(payload);
    }
    //?}
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

    //? if neoforge && <1.20.5 {
    public static void sendToPlayer(net.minecraft.server.level.ServerPlayer player, CustomPacketPayload payload) {
        net.neoforged.neoforge.network.PacketDistributor.PLAYER.with(player).send(payload);
    }
    //?}
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
