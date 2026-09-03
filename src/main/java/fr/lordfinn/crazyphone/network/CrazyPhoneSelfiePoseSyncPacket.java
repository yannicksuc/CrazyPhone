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
import net.minecraft.network.chat.Component;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

/**
 * Client -> server: "I'm framing a selfie (or just stopped), here's my current stick angle" - written into
 * the sender's own held phone stack (see CrazyPhoneHelper#setPhoneSelfiePose), so vanilla's own equipment
 * sync propagates it to every nearby observer automatically, the same "state lives on the stack" pattern
 * already proven for screen_on/call-state. Only ever targets the sender's OWN main-hand phone stack -
 * trusts the client's own report of {@code active}/stickX/stickY (a live-visual-only concern, nothing this
 * could be used to cheat with), the same trust level CrazyPhoneItemProperties already places in client-local
 * predicates like this one's own former isSelfieMode() check.
 *
 * Sent throttled from CrazyPhoneCaptureMode#tick() (every few ticks while active, not every frame) - stick
 * angles change continuously while the mouse moves, unlike screen_on/call-state's own one-shot
 * transition-only sends.
 */
public record CrazyPhoneSelfiePoseSyncPacket(boolean active, float stickX, float stickY) implements CustomPacketPayload {
    //? if >=1.20.5 {
    /*public static final Type<CrazyPhoneSelfiePoseSyncPacket> TYPE = new Type<>(
            Crazyphone.resource("selfie_pose_sync")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneSelfiePoseSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (RegistryFriendlyByteBuf buffer, CrazyPhoneSelfiePoseSyncPacket message) -> {
                        buffer.writeBoolean(message.active);
                        buffer.writeFloat(message.stickX);
                        buffer.writeFloat(message.stickY);
                    },
                    (RegistryFriendlyByteBuf buffer) -> new CrazyPhoneSelfiePoseSyncPacket(
                            buffer.readBoolean(),
                            buffer.readFloat(),
                            buffer.readFloat()
                    )
            );

    @Override
    public Type<CrazyPhoneSelfiePoseSyncPacket> type() {
        return TYPE;
    }
    *///? } else {
    public static final /*$ res_loc {*/ResourceLocation/*$}*/ ID = Crazyphone.resource("selfie_pose_sync");

    public CrazyPhoneSelfiePoseSyncPacket(FriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readFloat(), buffer.readFloat());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeFloat(stickX);
        buffer.writeFloat(stickY);
    }

    @Override
    public /*$ res_loc {*/ResourceLocation/*$}*/ id() {
        return ID;
    }
    //?}

    private static void handle(ServerPlayer player, boolean active, float stickX, float stickY) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.getItem() != ModItems.CRAZY_PHONE.get())
            return;
        CrazyPhoneHelper.setPhoneSelfiePose(held, active, stickX, stickY);
    }

    //? if neoforge {
    //? if >=1.20.5 {
    /*public static void handleData(final CrazyPhoneSelfiePoseSyncPacket message, final IPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            handle(player, message.active, message.stickX, message.stickY);
        }).exceptionally(e -> {
            context.connection().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    *///? } else {
    public static void handleData(final CrazyPhoneSelfiePoseSyncPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            handle(player, message.active, message.stickX, message.stickY);
        }).exceptionally(e -> {
            context.packetHandler().disconnect(Component.literal(e.getMessage()));
            return null;
        });
    }
    //?}
    //?}
    //? if fabric && >=1.20.5 {
    /*public static void handleDataFabric(CrazyPhoneSelfiePoseSyncPacket message, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
        handle(context.player(), message.active, message.stickX, message.stickY);
    }

    public static void registerFabricType() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerC2SType(TYPE, STREAM_CODEC);
    }

    public static void registerFabricServerReceiver() {
        fr.lordfinn.crazyphone.fabric.FabricNetworking.registerServerReceiver(TYPE, CrazyPhoneSelfiePoseSyncPacket::handleDataFabric);
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
            /*Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneSelfiePoseSyncPacket::handleData);
            *///? } else {
            Crazyphone.addNetworkMessage(ID, CrazyPhoneSelfiePoseSyncPacket::new, CrazyPhoneSelfiePoseSyncPacket::handleData);
            //?}
        }
    }
    //?}
}
