package fr.lordfinn.crazyphone.network;

//? if neoforge {
//? if >=1.20.5 {
/*import net.neoforged.neoforge.network.handling.IPayloadContext;
*///? } else {
import net.neoforged.neoforge.network.handling.PlayPayloadContext;
//?}
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
//?}
// Real, original Forge 1.20.1 - same FMLCommonSetupEvent/EventBusSubscriber/SubscribeEvent shape as
// NeoForge's own <1.20.5 branch above (see NetworkAccess.java's own doc comment) - PlayPayloadContext
// itself is NOT imported here: this file is in the same package as the same-package compat shim
// (fr.lordfinn.crazyphone.network.PlayPayloadContext), so it resolves with no import at all.
//? if legacyforge {
/*import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;
*///?}

//? if fabric || neoforge {
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.data.PhoneLiveScreenServer;

/**
 * Client -> server: "the phone app I have on screen is now X" (a {@link fr.lordfinn.crazyphone.utils.PhoneScreenApp}
 * id, one byte). Sent by LiveScreenClient only when the value changes (and throttled), and only to a server
 * that advertised the live_screen feature flag, so an older server never receives an unknown payload. The
 * server validates and rate limits it (PhoneLiveScreenServer#onReport), then writes the category onto the
 * sender's held phone stack so vanilla's equipment sync shows it to other players. It is a category only:
 * nothing about what is actually on the screen ever leaves the client.
 */
//? if legacyforge {
/*public record PhoneScreenAppSyncPacket(int app) {
*///? } else {
public record PhoneScreenAppSyncPacket(int app) implements CustomPacketPayload {
//?}
    //? if >=1.20.5 {
    /*public static final Type<PhoneScreenAppSyncPacket> TYPE = new Type<>(
            Crazyphone.resource("phone_screen_app_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PhoneScreenAppSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, PhoneScreenAppSyncPacket message) -> buffer.writeByte(message.app),
                    (RegistryFriendlyByteBuf buffer) -> new PhoneScreenAppSyncPacket(buffer.readByte())
            );

    @Override
    public Type<PhoneScreenAppSyncPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("phone_screen_app_sync");

    public PhoneScreenAppSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readByte());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(app);
    }

    //? if fabric || neoforge {
    @Override
    //?}
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge || legacyforge {
    //? if >=1.20.5 {
    /*public static void handleData(final PhoneScreenAppSyncPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player)
                PhoneLiveScreenServer.onReport(player, message.app);
        });
    }
    *///? } else {
    public static void handleData(final PhoneScreenAppSyncPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (context.player().orElse(null) instanceof ServerPlayer player)
                PhoneLiveScreenServer.onReport(player, message.app);
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(PhoneScreenAppSyncPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        PhoneLiveScreenServer.onReport(context.player(), message.app);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, PhoneScreenAppSyncPacket::handleDataFabric);
    }
    *///?}

    //? if neoforge {
    //? if <1.20.5 {
    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    //?} else {
    /*@EventBusSubscriber
    *///?}
    public static class Registration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            //? if >=1.20.5 {
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, PhoneScreenAppSyncPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, PhoneScreenAppSyncPacket::new, PhoneScreenAppSyncPacket::handleData);
            //?}
        }
    }
    //?}

    // Id 41, not the next free one (36): leaves room for packets other branches add in the meantime, since
    // a duplicate discriminator would make both registrations collide on Forge 1.20.1.
    //? if legacyforge {
    /*@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static class PhoneScreenAppSyncPacketLegacyForgeRegistration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(PhoneScreenAppSyncPacket.class, 41, PhoneScreenAppSyncPacket::write, PhoneScreenAppSyncPacket::new, PhoneScreenAppSyncPacket::handleData);
        }
    }
    *///?}
}
