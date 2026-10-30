package fr.lordfinn.crazyphone.network;

//? if legacyforge {
/*import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

import fr.lordfinn.crazyphone.Crazyphone;

/^*
 * Client -> server, Forge 1.20.1 only: a photo frame resize menu button click carrying a full int id.
 * The resize screen packs one axis' two extents into a single button id (see
 * CrazyPhonePhotoFrameResizeMenu#encodeAxisU), well above 127, but real 1.20.1 vanilla's
 * ServerboundContainerButtonClickPacket writes the button id as a single byte (javap-checked), so those ids
 * arrive truncated there. This packet carries the same (containerId, buttonId) pair as an int and then does
 * exactly what vanilla's own ServerGamePacketListenerImpl#handleContainerButtonClick does with it.
 ^/
public record PhotoFrameResizeButtonPacket(int containerId, int buttonId) {
    public PhotoFrameResizeButtonPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt());
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(buttonId);
    }

    public static void handleData(final PhotoFrameResizeButtonPacket message, final PlayPayloadContext context) {
        if (context.flow() != PacketFlow.SERVERBOUND)
            return;
        context.workHandler().submitAsync(() -> {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            player.resetLastActionTime();
            if (player.containerMenu.containerId != message.containerId() || player.isSpectator())
                return;
            if (!player.containerMenu.stillValid(player))
                return;
            if (player.containerMenu.clickMenuButton(player, message.buttonId()))
                player.containerMenu.broadcastChanges();
        });
    }

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static class PhotoFrameResizeButtonPacketLegacyForgeRegistration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(PhotoFrameResizeButtonPacket.class, 36, PhotoFrameResizeButtonPacket::write, PhotoFrameResizeButtonPacket::new, PhotoFrameResizeButtonPacket::handleData);
        }
    }
}
*///?}
