package fr.lordfinn.crazyphone.network;

import de.maxhenkel.camera.ImageData;
import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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

@EventBusSubscriber
public record CrazyPhoneImageActionMessage(ItemStack stack, ImageActionType actionType) implements CustomPacketPayload {

	public static final Type<CrazyPhoneImageActionMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Crazyphone.MODID, "crazyphone_image_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CrazyPhoneImageActionMessage> STREAM_CODEC =
	    StreamCodec.of(
	        (buf, msg) -> {
	            buf.writeVarInt(msg.actionType.ordinal());
	            Tag tag = ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, msg.stack).getOrThrow();
	            buf.writeNbt(tag);
	        },
	        buf -> {
	            // Let decode failures propagate instead of swallowing them into a null message - a null
	            // message previously reached handleData with no .exceptionally() to catch it, throwing an
	            // uncaught NPE inside the enqueued server work.
	            int actionId = buf.readVarInt();
	            ImageActionType[] actionTypes = ImageActionType.values();
	            if (actionId < 0 || actionId >= actionTypes.length)
	                throw new IllegalArgumentException("Invalid image action id: " + actionId);
	            Tag tag = buf.readNbt();
	            ItemStack stack = ItemStack.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
	            return new CrazyPhoneImageActionMessage(stack, actionTypes[actionId]);
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

                // The client picks which ItemStack to send here, so without this check a modified client
                // could send an arbitrary item (any item/NBT, not just a photo) and have the server hand
                // it out for free - only proceed if it's actually a valid Camera-mod image.
                if (ImageData.fromStack(stack) == null)
                    return;

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
            }).exceptionally(e -> {
                context.connection().disconnect(Component.literal(e.getMessage()));
                return null;
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
