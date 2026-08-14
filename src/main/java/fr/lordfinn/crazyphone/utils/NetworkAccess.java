package fr.lordfinn.crazyphone.utils;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Single choke point for sending a client-to-server packet, which moved from
 *  {@code PacketDistributor.sendToServer} onto its own {@code ClientPacketDistributor} class as of
 *  NeoForge 21.10 (1.21.10) - every call site in the mod goes through this instead of calling either
 *  class directly, so a future relocation only means rewriting this one file. */
public final class NetworkAccess {
    private NetworkAccess() {
    }

    public static void sendToServer(CustomPacketPayload payload) {
        //? if <1.20.5 {
        net.neoforged.neoforge.network.PacketDistributor.SERVER.noArg().send(payload);
        //?}
        //? if >=1.20.5 <1.21.10 {
        /*net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        *///?}
        //? if >=1.21.10 {
        /*net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
        *///?}
    }
}
