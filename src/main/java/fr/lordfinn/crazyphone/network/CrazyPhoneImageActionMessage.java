package fr.lordfinn.crazyphone.network;

import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber
public record CrazyPhoneImageActionMessage(ItemStack stack, ImageActionType actionType) implements CustomPacketPayload {
	private static final Logger LOGGER = LoggerFactory.getLogger("crazyphone");

	public static final Type<CrazyPhoneImageActionMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "crazyphone_image_action"));

public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneImageActionMessage> STREAM_CODEC =
    StreamCodec.of(
        (buf, msg) -> {
            // Encode the ItemStack as a Tag using NbtOps
            try {
            buf.writeVarInt(msg.actionType.ordinal());
            Tag tag = ItemStack.CODEC
                .encodeStart(NbtOps.INSTANCE, msg.stack)
                .getOrThrow();
            buf.writeNbt(tag);
            } catch (Exception error){
                LOGGER.warn("Failed to encode ItemStack: {}", error);
            }
        },
        buf -> {
            // Read NBT tag and decode it into an ItemStack
            try {
            int actionId = buf.readVarInt();
            ImageActionType actionType = ImageActionType.values()[actionId];
            Tag tag = buf.readNbt();
            ItemStack stack = ItemStack.CODEC
                .parse(NbtOps.INSTANCE, tag)
                .getOrThrow();
            return new CrazyPhoneImageActionMessage(stack, actionType);
            } catch (Exception error){
                LOGGER.warn("Failed to decode ItemStack: {}", error);
            }
            return null;
        }
    );

    @Override
    public Type<CrazyPhoneImageActionMessage> type() {
        return TYPE;
    }

    public static void handleData(CrazyPhoneImageActionMessage message, IPayloadContext context) {
        if (context.flow() == PacketFlow.SERVERBOUND) {
            context.enqueueWork(() -> {
                ServerPlayer player = (ServerPlayer) context.player();
                ItemStack stack = message.stack().copy();

                switch (message.actionType()) {
                    case GIVE_PLAYER -> {
                        if (!player.getInventory().add(stack)) {
                            player.drop(stack, false);
                        }
                    }
                    case GIVE_ALBUM -> {
                        CameraModHelper.tryInsertImageIntoCrazyPhone(player, stack);
                    }
                }
            });
        }
    }


    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneImageActionMessage::handleData);
    }

    public enum ImageActionType {
        GIVE_PLAYER,
        GIVE_ALBUM
    }
}
