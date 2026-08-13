package fr.lordfinn.crazyphone.network;

import de.maxhenkel.camera.ImageData;
import fr.lordfinn.crazyphone.Crazyphone;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
//? if >=1.20.5 {
import net.minecraft.nbt.NbtOps;
//? }
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
//? }
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
import net.neoforged.fml.common.EventBusSubscriber;
//? } else {
/*import net.neoforged.fml.common.Mod.EventBusSubscriber;
*///?}
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
//? if >=1.20.5 {
import net.neoforged.neoforge.network.handling.IPayloadContext;
//? } else {
/*import net.neoforged.neoforge.network.handling.PlayPayloadContext;
*///?}

@EventBusSubscriber
public record CrazyPhoneImageActionMessage(ItemStack stack, ImageActionType actionType) implements CustomPacketPayload {

	//? if >=1.20.5 {
	public static final Type<CrazyPhoneImageActionMessage> TYPE = new Type<>(Crazyphone.resource("crazyphone_image_action"));

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
    //? } else {
    /*public static final ResourceLocation ID = new ResourceLocation(Crazyphone.MODID, "crazyphone_image_action");

    // Stack is written/read before actionType here (unlike the >=1.20.5 branch's wire order) purely so the
    // two reads can be sequenced correctly as constructor arguments below - this protocol version never
    // talks to the other, so the two branches' byte layouts don't need to match each other.
    public CrazyPhoneImageActionMessage(FriendlyByteBuf buf) {
        this(readStack(buf), buf.readEnum(ImageActionType.class));
    }

    private static ItemStack readStack(FriendlyByteBuf buf) {
        Tag tag = buf.readNbt();
        return tag instanceof CompoundTag compound ? ItemStack.of(compound) : ItemStack.EMPTY;
    }

    public void write(FriendlyByteBuf buf) {
        CompoundTag tag = new CompoundTag();
        stack.save(tag);
        buf.writeNbt(tag);
        buf.writeEnum(actionType);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }
    *///?}

    //? if >=1.20.5 {
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
    //? } else {
    /*public static void handleData(CrazyPhoneImageActionMessage message, PlayPayloadContext context) {
        if (context.flow() == PacketFlow.SERVERBOUND) {
            context.workHandler().submitAsync(() -> {
                ServerPlayer player = (ServerPlayer) context.player().orElse(null);
                ItemStack stack = message.stack().copy();

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
                context.packetHandler().disconnect(Component.literal(e.getMessage()));
                return null;
            });
        }
    }
    *///?}


    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        //? if >=1.20.5 {
        Crazyphone.addNetworkMessage(TYPE, STREAM_CODEC, CrazyPhoneImageActionMessage::handleData);
        //? } else {
        /*Crazyphone.addNetworkMessage(ID, CrazyPhoneImageActionMessage::new, CrazyPhoneImageActionMessage::handleData);
        *///?}
    }

    public enum ImageActionType {
        GIVE_PLAYER,
        GIVE_ALBUM
    }
}
