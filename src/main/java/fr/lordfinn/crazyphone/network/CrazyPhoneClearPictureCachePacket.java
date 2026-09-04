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

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.PacketFlow;
//? if >=1.20.5 {
/*import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.client.picture.FabricPictureCache;

/**
 * Server -> client: "wipe your local photo cache, RAM and disk both" - sent to a single targeted player by
 * the admin-only {@code /crazyphone cache clear [player]} command ({@link fr.lordfinn.crazyphone.command.
 * ModCommands}), for troubleshooting a stuck/corrupted local {@link FabricPictureCache} without needing the
 * affected player to manually delete files or fully reinstall. No payload needed - the recipient IS the
 * target, there's nothing else to identify.
 */
public record CrazyPhoneClearPictureCachePacket() implements CustomPacketPayload {

    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneClearPictureCachePacket> TYPE = new Type<>(
            Crazyphone.resource("clear_picture_cache")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneClearPictureCachePacket> STREAM_CODEC =
            StreamCodec.unit(new CrazyPhoneClearPictureCachePacket());

    @Override
    public Type<CrazyPhoneClearPictureCachePacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("clear_picture_cache");

    public CrazyPhoneClearPictureCachePacket(FriendlyByteBuf buffer) {
        this();
    }

    public void write(FriendlyByteBuf buffer) {
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhoneClearPictureCachePacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.enqueueWork(FabricPictureCache::clearAll);
    }
    *///? } else {
    public static void handleData(final CrazyPhoneClearPictureCachePacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.CLIENTBOUND)
            return;
        context.workHandler().submitAsync(FabricPictureCache::clearAll);
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneClearPictureCachePacket message, net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.Context context) {
        FabricPictureCache.clearAll();
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerS2CType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricClientReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerClientReceiver(TYPE, CrazyPhoneClearPictureCachePacket::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneClearPictureCachePacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneClearPictureCachePacket::new, CrazyPhoneClearPictureCachePacket::handleData);
            //?}
        }
    }
    //?}
}
