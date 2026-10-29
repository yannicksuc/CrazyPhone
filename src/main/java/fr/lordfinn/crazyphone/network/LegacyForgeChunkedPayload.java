package fr.lordfinn.crazyphone.network;

//? if legacyforge {
/*import fr.lordfinn.crazyphone.Crazyphone;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

// Forge 1.20.1 only. Vanilla 1.20.1 caps a custom payload at 32767 bytes client to server and 1 MiB server
// to client, and Forge's SimpleChannel does not split anything, so a photo upload (thumbnail + full PNG) or
// a voice message disconnected the sender with "Payload may not be larger than 32767 bytes". NeoForge (1.20.4
// and later) splits oversized payloads itself, so the other loaders never needed this.
//
// Every message registered through Crazyphone#addNetworkMessage is remembered here with its own codec. When
// an encoded message is bigger than the direction's limit, it is sent as numbered Chunk messages instead and
// the receiving side decodes the reassembled bytes with that same codec and hands the message to its normal
// handler, so no packet class needs to know about any of this.
public final class LegacyForgeChunkedPayload {
    // Channel discriminator of the Chunk message itself (0-35 are the packets registered so far).
    public static final int CHUNK_MESSAGE_ID = 100;
    private static final int TO_SERVER_LIMIT = 32000;
    private static final int TO_CLIENT_LIMIT = 1_000_000;
    private static final int CHUNK_BYTES = 30000;
    // Upper bound for one reassembled message (a full photo is capped well below this by the config).
    private static final int MAX_TOTAL_BYTES = 32 * 1024 * 1024;
    private static final long STALE_MILLIS = 120_000L;

    private record Codec<T>(int id, BiConsumer<T, FriendlyByteBuf> writer, Function<FriendlyByteBuf, T> reader,
                            BiConsumer<T, PlayPayloadContext> handler) {
    }

    private static final Map<Class<?>, Codec<?>> BY_CLASS = new ConcurrentHashMap<>();
    private static final Map<Integer, Codec<?>> BY_ID = new ConcurrentHashMap<>();
    private static final Map<String, Transfer> PENDING = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_TRANSFER = new AtomicInteger();

    private LegacyForgeChunkedPayload() {
    }

    public static <T> void remember(Class<T> clazz, int id, BiConsumer<T, FriendlyByteBuf> writer,
                                    Function<FriendlyByteBuf, T> reader, BiConsumer<T, PlayPayloadContext> handler) {
        Codec<T> codec = new Codec<>(id, writer, reader, handler);
        BY_CLASS.put(clazz, codec);
        BY_ID.put(id, codec);
    }

    public static void sendToServer(Object payload) {
        byte[] encoded = encodeIfOversized(payload, TO_SERVER_LIMIT);
        if (encoded == null) {
            Crazyphone.channel().sendToServer(payload);
            return;
        }
        for (Chunk chunk : split(encoded, BY_CLASS.get(payload.getClass()).id()))
            Crazyphone.channel().sendToServer(chunk);
    }

    public static void send(PacketDistributor.PacketTarget target, Object payload) {
        byte[] encoded = encodeIfOversized(payload, TO_CLIENT_LIMIT);
        if (encoded == null) {
            Crazyphone.channel().send(target, payload);
            return;
        }
        for (Chunk chunk : split(encoded, BY_CLASS.get(payload.getClass()).id()))
            Crazyphone.channel().send(target, chunk);
    }

    // The message's own encoded bytes when they exceed the limit, null when it can go out as it is.
    @SuppressWarnings("unchecked")
    private static byte[] encodeIfOversized(Object payload, int limit) {
        Codec<Object> codec = (Codec<Object>) BY_CLASS.get(payload.getClass());
        if (codec == null || payload instanceof Chunk)
            return null;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.writer().accept(payload, buffer);
            if (buffer.readableBytes() <= limit)
                return null;
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return bytes;
        } finally {
            buffer.release();
        }
    }

    private static Chunk[] split(byte[] encoded, int innerId) {
        int transfer = NEXT_TRANSFER.incrementAndGet();
        int total = (encoded.length + CHUNK_BYTES - 1) / CHUNK_BYTES;
        Chunk[] chunks = new Chunk[total];
        for (int i = 0; i < total; i++) {
            int from = i * CHUNK_BYTES;
            chunks[i] = new Chunk(transfer, innerId, i, total, Arrays.copyOfRange(encoded, from, Math.min(encoded.length, from + CHUNK_BYTES)));
        }
        return chunks;
    }

    public record Chunk(int transfer, int innerId, int index, int total, byte[] data) {
        public static void write(Chunk chunk, FriendlyByteBuf buffer) {
            buffer.writeVarInt(chunk.transfer);
            buffer.writeVarInt(chunk.innerId);
            buffer.writeVarInt(chunk.index);
            buffer.writeVarInt(chunk.total);
            buffer.writeByteArray(chunk.data);
        }

        public static Chunk read(FriendlyByteBuf buffer) {
            return new Chunk(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readByteArray(CHUNK_BYTES));
        }
    }

    private static final class Transfer {
        final int innerId;
        final byte[][] parts;
        final long startedAt = System.currentTimeMillis();
        int received;

        Transfer(int innerId, int total) {
            this.innerId = innerId;
            this.parts = new byte[total][];
        }
    }

    @SuppressWarnings("unchecked")
    static void handleChunk(Chunk chunk, PlayPayloadContext context) {
        String sender;
        if (context.flow() == PacketFlow.SERVERBOUND) {
            if (!(context.player().orElse(null) instanceof ServerPlayer player))
                return;
            sender = player.getUUID().toString();
        } else {
            sender = "server";
        }
        if (chunk.total() <= 0 || (long) chunk.total() * CHUNK_BYTES > MAX_TOTAL_BYTES || chunk.index() < 0 || chunk.index() >= chunk.total())
            return;
        long now = System.currentTimeMillis();
        PENDING.values().removeIf(t -> now - t.startedAt > STALE_MILLIS);

        String key = sender + "/" + chunk.transfer();
        Transfer transfer = PENDING.computeIfAbsent(key, k -> new Transfer(chunk.innerId(), chunk.total()));
        byte[][] parts;
        synchronized (transfer) {
            if (transfer.innerId != chunk.innerId() || transfer.parts.length != chunk.total()) {
                PENDING.remove(key);
                return;
            }
            if (transfer.parts[chunk.index()] == null) {
                transfer.parts[chunk.index()] = chunk.data();
                transfer.received++;
            }
            if (transfer.received < transfer.parts.length)
                return;
            parts = transfer.parts;
        }
        PENDING.remove(key);

        Codec<Object> codec = (Codec<Object>) BY_ID.get(chunk.innerId());
        if (codec == null || codec.id() == CHUNK_MESSAGE_ID)
            return;
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(parts));
        try {
            codec.handler().accept(codec.reader().apply(buffer), context);
        } finally {
            buffer.release();
        }
    }

    @EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
    public static class LegacyForgeChunkedPayloadRegistration {
        @SubscribeEvent
        public static void register(FMLCommonSetupEvent event) {
            Crazyphone.addNetworkMessage(Chunk.class, CHUNK_MESSAGE_ID, Chunk::write, Chunk::read, LegacyForgeChunkedPayload::handleChunk);
        }
    }
}
*///?}
