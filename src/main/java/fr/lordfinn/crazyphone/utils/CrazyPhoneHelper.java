package fr.lordfinn.crazyphone.utils;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import fr.lordfinn.crazyphone.client.gui.components.MessageData;
import fr.lordfinn.crazyphone.data.ConversationSavedData;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.item.CrazyPhoneItem;
import fr.lordfinn.crazyphone.network.ConversationCallActivitySyncPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneGroupMembershipNotificationPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneNewMessageNotificationPacket;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneAddContactToPhoneProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneGetContactsProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberProcedure;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.20.5 {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.resources.RegistryOps;
import java.util.concurrent.ThreadLocalRandom;
//? if neoforge {
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
//?}

public class CrazyPhoneHelper {

    /** The item in entity's main hand, or ItemStack.EMPTY for a non-living entity (e.g. a fake player) or none held. */
    public static ItemStack getMainHandItemOrEmpty(Entity entity) {
        return entity instanceof LivingEntity livingEntity ? livingEntity.getMainHandItem() : ItemStack.EMPTY;
    }

    //? if neoforge {
    @Nullable
    public static IItemHandlerModifiable getPhoneItemHandler(Player player) {
        ItemStack held = player.getMainHandItem();

        if (!(held.getItem() instanceof CrazyPhoneItem))
            return null;

        //? if >=1.21.10 {
        /*net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> handler = held.getCapability(Capabilities.Item.ITEM, null);
        *///? } else {
        IItemHandler handler = held.getCapability(Capabilities.ItemHandler.ITEM, null);
        //?}

        if (!(handler instanceof IItemHandlerModifiable modifiableHandler))
            return null;

        return modifiableHandler;
    }
    //?}

    public static Contact getContact(Level world, String number) {
        Tag potentialContact = PhoneRegistrySavedData.get(world).phones.get(number);
        if (potentialContact != null && potentialContact instanceof CompoundTag contactTag) {
            String name = NbtCompat.getString(contactTag, "name");
            String uuid = NbtCompat.getString(contactTag, "uuid");
            String skin = NbtCompat.getString(contactTag, "skin");
            return new Contact(number, name, skin, uuid);
        }
        return null;
    }

    public static String getConversationNumber(String recipientNumber, Entity entity) {
        return getConversationNumber(
                Arrays.asList(recipientNumber, GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null)));
    }

    /** Same as {@link #getConversationNumber(String, Entity)} but for a group: any number of other
     * participants plus the caller's own number, sorted and joined the same way - the id doesn't care
     * how many numbers go in, so a 2-party DM and an N-party group use the exact same scheme. */
    public static String getConversationNumber(List<String> otherNumbers, Entity entity) {
        List<String> all = new ArrayList<>(otherNumbers);
        all.add(GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null));
        return getConversationNumber(all);
    }

    public static String getConversationNumber(String numberA, String numberB) {
        return getConversationNumber(Arrays.asList(numberA, numberB));
    }

    private static String getConversationNumber(List<String> numbers) {
        List<String> sorted = new ArrayList<>(numbers);
        Collections.sort(sorted);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            result.append(sorted.get(i));
            if (i < sorted.size() - 1) {
                result.append(".");
            }
        }
        return result.toString();
    }

    public static List<String> getNumbersFromConversationId(String conversationId) {
        return Arrays.asList(conversationId.split("\\."));
    }

    /**
     * The "Name • number" format used for contact head display names in the contacts menu (and,
     * reused here, for the conversation screen's head-hover tooltip - so both show the exact same
     * formatting instead of drifting apart).
     */
    public static MutableComponent formatContactDisplayName(String name, String number) {
        return (name == null ? Component.translatable("message.crazyphone.unknown_contact") : Component.literal(name)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal(" • ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(number == null ? "" : number).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Same layout as {@link #formatContactDisplayName} ("Name • detail") so a group entry reads
     * consistently with an individual contact, but in cyan instead of gold to tell the two apart at a
     * glance, and with the literal word "Group" (translated) standing in for the phone number.
     */
    public static MutableComponent formatGroupDisplayName(String customName, List<Contact> members) {
        String names = (customName != null && !customName.isEmpty())
                ? customName
                : members.stream().map(Contact::getName).collect(java.util.stream.Collectors.joining(","));
        return Component.literal(names).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(" • ").withStyle(ChatFormatting.WHITE))
                .append(Component.translatable("gui.crazyphone.crazy_phone_contacts_screen.label_group").withStyle(ChatFormatting.GRAY));
    }

    public static ItemStack createContactHead(Contact contact) {
        ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);

        MutableComponent displayName = formatContactDisplayName(contact.getName(), contact.getNumber());
        PhoneTagAccess.setCustomName(head, displayName);

        PhoneTagAccess.updateTag(head, tag -> tag.putString("number", contact.getNumber()));
        PhoneTagAccess.updateTag(head, tag -> tag.putString("name", contact.getName()));

        if (contact.getUuid() != null && !contact.getUuid().isEmpty()) {
            try {
                PhoneTagAccess.updateTag(head, tag -> tag.putString("uuid", contact.getUuid()));
                GameProfile profile = new GameProfile(UUID.fromString(contact.getUuid()), "CustomHead");
                if (contact.getSkin() != null || !contact.getSkin().isEmpty()) {
                    profile = GameProfileCompat.withTextureProperty(profile, contact.getSkin());
                }
                PhoneTagAccess.setSkullOwner(head, profile);
            } catch (Exception e) {
            }
        }

        return head;
    }

    private static final Object messageLock = new Object();

    /**
     * Appends a message to the conversation's history via {@link ConversationSavedData} (bounded, never
     * broadcast wholesale - see that class for why) and notifies the other participant(s) with a targeted
     * packet. Replaces the old code that manipulated a ListTag inside the monolithic
     * CrazythingsModVariables.MapVariables#crazyPhoneMessages and then broadcast the entire blob to every
     * player on every message - that unbounded broadcast was the root cause of the old mod's server crash
     * on player login.
     */
    public static void addMessage(Level world, String conversationId, String senderNumber, String message,
                              int timestampInMinutes) {
        synchronized (messageLock) {
            CompoundTag messageTag = createMessageTag(senderNumber, message, timestampInMinutes);
            ConversationSavedData.get(world).appendMessage(conversationId, messageTag);
            List<String> numbers = getGroupMembers(world, conversationId);
            notifyContacts(world, messageTag, numbers, senderNumber, message, timestampInMinutes, conversationId);
        }
    }

    /**
     * Appends a voice message: mirrors {@link #addMessage} but stores the audio bytes separately in
     * {@link ConversationSavedData#voiceAudio} (like the image-message pattern - only lightweight metadata,
     * id + duration, is embedded in the message tag itself) and notifies contacts the same way. The audio
     * is never sent to anyone here - only fetched later, on demand, when a recipient clicks play.
     */
    public static void addVoiceMessage(Level world, String conversationId, String senderNumber, UUID voiceId, byte[] audioPcm,
                                        int durationTicks, byte[] envelope, int timestampInMinutes) {
        synchronized (messageLock) {
            ConversationSavedData data = ConversationSavedData.get(world);
            data.storeVoiceAudio(voiceId, conversationId, audioPcm);

            CompoundTag voiceTag = new CompoundTag();
            voiceTag.putLong("voice_id_most", voiceId.getMostSignificantBits());
            voiceTag.putLong("voice_id_least", voiceId.getLeastSignificantBits());
            voiceTag.putInt("voice_duration_ticks", durationTicks);
            voiceTag.put("voice_envelope", new net.minecraft.nbt.ByteArrayTag(envelope));

            CompoundTag messageTag = new CompoundTag();
            messageTag.putString("sender", senderNumber);
            messageTag.putString("value", "");
            messageTag.putInt("timecode", timestampInMinutes);
            messageTag.put("voice", voiceTag);

            data.appendMessage(conversationId, messageTag);
            List<String> numbers = getGroupMembers(world, conversationId);
            notifyContacts(world, messageTag, numbers, senderNumber, "ðŸŽ¤", timestampInMinutes, conversationId);
        }
    }

    /**
     * Appends an image message on the Fabric-native picture pipeline (task #165): mirrors
     * {@link #addVoiceMessage}, storing PNG bytes in {@link ConversationSavedData#imageBytes} and embedding
     * only an id pointer in the message tag. Uses the SAME image_id_most/image_id_least/owner field names
     * {@link #imageDataToCompoundTag} already writes on NeoForge, so {@link #getMessageFromTag}'s image
     * block can stay a single shared read path that just resolves the id differently per loader.
     */
    public static void addImageMessage(Level world, String conversationId, String senderNumber, UUID imageId, int timestampInMinutes) {
        synchronized (messageLock) {
            ConversationSavedData data = ConversationSavedData.get(world);
            // Bytes are already stored by the caller (CrazyPhoneUploadPicturePacket) before this runs -
            // this only writes the lightweight message tag pointing at them.
            CompoundTag imageTag = new CompoundTag();
            imageTag.putLong("image_id_most", imageId.getMostSignificantBits());
            imageTag.putLong("image_id_least", imageId.getLeastSignificantBits());
            imageTag.putString("owner", senderNumber);

            CompoundTag messageTag = new CompoundTag();
            messageTag.putString("sender", senderNumber);
            messageTag.putString("value", "");
            messageTag.putInt("timecode", timestampInMinutes);
            messageTag.put("image", imageTag);

            data.appendMessage(conversationId, messageTag);
            List<String> numbers = getGroupMembers(world, conversationId);
            notifyContacts(world, messageTag, numbers, senderNumber, "📷", timestampInMinutes, conversationId);
        }
    }

    /** The timecode of the most recent message in a conversation, or 0 if it has none yet - used to sort
     * the contacts/groups grid by recency (most recently active conversation first). */
    public static int getLastMessageTimecode(LevelAccessor world, String conversationId) {
        List<CompoundTag> last = ConversationSavedData.get(world).getPage(conversationId, 0, 1);
        return last.isEmpty() ? 0 : NbtCompat.getInt(last.get(0), "timecode");
    }

    /** Toggles whether {@code number} is favorited for {@code owner} - favorited contacts are pinned in
     * their own section above the rest of the contacts grid. Marks the registry dirty but does not sync;
     * callers refresh the requesting player's own contacts menu afterward. */
    public static void toggleFavorite(LevelAccessor world, String owner, String number) {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        ListTag numbers = registry.favorites.get(owner) instanceof ListTag list ? list.copy() : new ListTag();
        boolean removed = numbers.removeIf(t -> t instanceof StringTag s && NbtCompat.asString(s).equals(number));
        if (!removed)
            numbers.add(StringTag.valueOf(number));
        registry.favorites.put(owner, numbers);
        registry.setDirty();
    }

    /** The set of contact numbers {@code owner} has favorited. */
    public static Set<String> getFavoriteNumbers(LevelAccessor world, String owner) {
        Set<String> result = new java.util.HashSet<>();
        if (PhoneRegistrySavedData.get(world).favorites.get(owner) instanceof ListTag list) {
            for (Tag t : list)
                if (t instanceof StringTag s)
                    result.add(NbtCompat.asString(s));
        }
        return result;
    }

    /**
     * Appends a system event (rename / icon change / member excluded / admin reassigned) to the
     * conversation feed - not sent by anyone, so it carries no sender contact head, just an optional
     * leading icon and styled text (kept as a real {@link Component}, not flattened to a plain string
     * server-side, so it still localizes correctly for whichever client renders it).
     */
    public static void addSystemMessage(Level world, String conversationId, Component text, @Nullable ItemStack icon) {
        synchronized (messageLock) {
            int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);
            CompoundTag messageTag = createSystemMessageTag(text, icon, timestampInMinutes);
            ConversationSavedData.get(world).appendMessage(conversationId, messageTag);
            notifySystemMessage(world, conversationId, messageTag);
        }
    }

    /** Posts a "call in progress" chat entry the moment a call actually connects (see
     * CallRegistry#markConnectedIfFirstTime) - its duration is filled in later, once the call ends, by
     * {@link #finalizeCallMessage} mutating this same entry rather than appending a second one. Rendered
     * client-side as a live-ticking banner while ongoing purely from this start timestamp (see
     * MessageWidget) - no further packets are needed to keep it updating in an already-open conversation. */
    public static void addCallMessage(Level world, String conversationId, UUID callId, long startEpochMillis) {
        synchronized (messageLock) {
            CompoundTag callTag = new CompoundTag();
            callTag.putLong("call_id_most", callId.getMostSignificantBits());
            callTag.putLong("call_id_least", callId.getLeastSignificantBits());
            callTag.putLong("call_start_millis", startEpochMillis);
            callTag.putLong("call_duration_millis", -1);

            CompoundTag messageTag = new CompoundTag();
            messageTag.putBoolean("system", true);
            messageTag.putInt("timecode", (int) (startEpochMillis / 60000));
            messageTag.put("call", callTag);

            ConversationSavedData.get(world).appendMessage(conversationId, messageTag);
            notifySystemMessage(world, conversationId, messageTag);
        }
    }

    /** Fills in the final duration once a call ends, mutating the same chat entry {@link #addCallMessage}
     * already posted, and pushes the real value to every online conversation member (see
     * {@link fr.lordfinn.crazyphone.network.CrazyPhoneNewCallDurationNotificationPacket}) - the two call
     * participants already freeze their own live-ticking display locally the moment they get the call's own
     * ENDED state sync, but a bystander merely watching someone else's call in a group conversation was never
     * "live" for it and had no other way to learn it ended; without this push their copy just kept ticking
     * an estimate forever. No-op (both for the stored entry and the push) if the entry was already evicted by
     * the message history cap. */
    public static void finalizeCallMessage(Level world, String conversationId, UUID callId, long durationMillis) {
        synchronized (messageLock) {
            ConversationSavedData.get(world).updateCallMessage(conversationId, callId,
                    callTag -> callTag.putLong("call_duration_millis", durationMillis));
        }
        //? if neoforge {
        MinecraftServer server = world.getServer();
        if (server == null)
            return;
        for (String number : getGroupMembers(world, conversationId)) {
            Contact receiver = getContact(world, number);
            if (receiver == null || receiver.getUuid() == null)
                continue;
            ServerPlayer receiverPlayer = server.getPlayerList().getPlayer(UUID.fromString(receiver.getUuid()));
            if (receiverPlayer != null)
                NetworkAccess.sendToPlayer(receiverPlayer, new fr.lordfinn.crazyphone.network.CrazyPhoneNewCallDurationNotificationPacket(conversationId, callId, durationMillis));
        }
        //?}
        // Only called from voicechat.CallRegistry (NeoForge-only this pass, see build.fabric.gradle.kts's
        // note) - the bystander push above is dead but harmless to leave gated rather than remove.
    }

    /** Posts a "missed call" system message - the mirror-image case to {@link #addCallMessage}/
     * {@link #finalizeCallMessage}, which only ever fire for a call that actually connected. Called from
     * CallRegistry#endCall whenever a session's connectedAtEpochMillis was never set: the caller cancelled
     * before anyone answered, everyone declined, or the ring timeout expired with no answer - all of those
     * currently left NO trace at all in the conversation feed before this existed. Uses the generic
     * system-message channel rather than the "call" message tag/live-ticking widget, since there's no
     * duration or live state to show for a call that never actually happened. */
    public static void addMissedCallMessage(Level world, String conversationId, UUID initiatorId) {
        String callerName = resolveContactNameByUuid(world, conversationId, initiatorId);
        Component text = callerName != null
                ? Component.translatable("gui.crazyphone.crazy_phone_conversation.system_missed_call_from", callerName).withStyle(ChatFormatting.RED)
                : Component.translatable("gui.crazyphone.crazy_phone_conversation.system_missed_call").withStyle(ChatFormatting.RED);
        addSystemMessage(world, conversationId, text, new ItemStack(ModItems.CRAZY_PHONE.get()));
    }

    @Nullable
    private static String resolveContactNameByUuid(LevelAccessor world, String conversationId, UUID playerId) {
        String playerIdString = playerId.toString();
        for (String number : getGroupMembers(world, conversationId)) {
            Contact contact = getContact((Level) world, number);
            if (contact != null && playerIdString.equals(contact.getUuid()))
                return contact.getName();
        }
        return null;
    }

    private static void notifySystemMessage(Level world, String conversationId, CompoundTag messageTag) {
        MinecraftServer server = world.getServer();
        if (server == null)
            return;
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        for (String number : getGroupMembers(world, conversationId)) {
            Contact receiver = getContact(world, number);
            if (receiver == null || receiver.getUuid() == null)
                continue;
            ServerPlayer receiverPlayer = server.getPlayerList().getPlayer(UUID.fromString(receiver.getUuid()));
            if (receiverPlayer != null) {
                NetworkAccess.sendToPlayer(receiverPlayer, new CrazyPhoneNewMessageNotificationPacket(messageTag, ""));
            }
            addNotificationBadge(registry, number, conversationId, receiverPlayer);
        }
    }

    private static CompoundTag createMessageTag(String senderNumber, String message, int timestampInMinutes) {
        CompoundTag tag = new CompoundTag();
        tag.putString("sender", senderNumber);
        tag.putString("value", message);
        tag.putInt("timecode", timestampInMinutes);
        return tag;
    }

    private static CompoundTag createSystemMessageTag(Component text, @Nullable ItemStack icon, int timestampInMinutes) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("system", true);
        tag.putInt("timecode", timestampInMinutes);
        //? if >=1.20.5 {
        /*tag.put("systemText", ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, text).getOrThrow());
        *///? } else {
        tag.put("systemText", ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, text).getOrThrow(false, s -> {}));
        //?}
        if (icon != null && !icon.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(icon.getItem());
            tag.putString("systemIcon", id.toString());
        }
        return tag;
    }

    /**
     * Notifies the other participant(s) of {@code conversationId} that a new message arrived. Only ever
     * targets those specific participants: a direct {@link PacketDistributor#sendToPlayer} for the message
     * content itself, and (if the receiver's notification list actually changed) a
     * {@link PhoneRegistrySavedData#syncTo} to just that receiver for the small/bounded phones registry -
     * never a broadcast of the message, the registry, or conversation history to every online player, since
     * no one else's phone/contacts state actually changed.
     */
    public static void notifyContacts(Level world, CompoundTag messageTag, List<String> numbers, String senderNumber, String message,
            int timestampInMinutes, String conversationId) {
        Contact sender = getContact(world, senderNumber);
        MinecraftServer server = world.getServer();

        if (sender == null || server == null)
            return;

        // Defensive: a group conversation is normally already registered for every participant at
        // creation time (see CrazyPhoneContactsScreenButtonMessage buttonID 2), but this keeps every
        // participant's "groups" list correct even if that step was somehow missed. No-ops for a plain
        // 1:1 conversation and for a group whose metadata already exists (see createGroup).
        createGroup(world, conversationId, numbers, senderNumber);

        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        for (String receiverNumber : numbers) {
            if (receiverNumber.equals(senderNumber))
                continue;

            Contact receiver = getContact(world, receiverNumber);
            if (receiver == null || receiver.getUuid() == null)
                continue;

            ServerPlayer receiverPlayer = server.getPlayerList().getPlayer(UUID.fromString(receiver.getUuid()));
            if (receiverPlayer != null) {
                NetworkAccess.sendToPlayer(receiverPlayer, new CrazyPhoneNewMessageNotificationPacket(messageTag, sender.getName()));
            }

            addNotificationBadge(registry, receiverNumber, conversationId, receiverPlayer);

            ListTag contactsOfReceiver = CrazyPhoneGetContactsProcedure.execute(world, receiverNumber);
            boolean hasContact = false;
            for (Tag contact : contactsOfReceiver) {
                if (contact instanceof CompoundTag compoundTag
                        && senderNumber.equals(NbtCompat.getString(compoundTag, "number"))) {
                    hasContact = true;
                    break;
                }
            }
            if (!hasContact) {
                CrazyPhoneAddContactToPhoneProcedure.execute(world, senderNumber, receiverNumber);
            }
        }
    }

    /** Adds {@code conversationId} to {@code receiverNumber}'s unread-notifications list if it isn't
     * already there, and syncs just that receiver (if online) - shared by both normal and system messages. */
    private static void addNotificationBadge(PhoneRegistrySavedData registry, String receiverNumber, String conversationId, @Nullable ServerPlayer receiverPlayer) {
        Tag receiverPhone = registry.phones.get(receiverNumber);
        if (!(receiverPhone instanceof CompoundTag receiverPhoneCompoundTag))
            return;

        Tag notificationstag = receiverPhoneCompoundTag.get("notifications");
        ListTag notifications = (notificationstag instanceof ListTag listTag) ? listTag : new ListTag();

        for (Tag tag : notifications) {
            if (tag instanceof StringTag stringTag && NbtCompat.asString(stringTag).equals(conversationId))
                return;
        }

        notifications.add(StringTag.valueOf(conversationId));
        receiverPhoneCompoundTag.put("notifications", notifications);
        registry.phones.put(receiverNumber, receiverPhoneCompoundTag);
        if (receiverPlayer != null)
            registry.syncTo(receiverPlayer);
        else
            registry.setDirty();
    }

    /** A group conversation's metadata: custom name/icon (empty item = unset, client falls back to
     * defaults), the current admin's number, and the LIVE member list - unlike the conversationId (which
     * stays fixed for the conversation's lifetime), members can shrink via {@link #excludeGroupMember}. */
    public record GroupMeta(String name, ItemStack icon, String admin, List<String> members) {}

    public static GroupMeta getGroupMeta(LevelAccessor world, String conversationId) {
        Tag raw = PhoneRegistrySavedData.get(world).groupMeta.get(conversationId);
        if (!(raw instanceof CompoundTag tag))
            return new GroupMeta("", ItemStack.EMPTY, "", getNumbersFromConversationId(conversationId));
        return new GroupMeta(NbtCompat.getString(tag, "name"), decodeItemStack(world, NbtCompat.getCompound(tag, "icon")), NbtCompat.getString(tag, "admin"), readMembers(tag));
    }

    /**
     * Encodes a full {@link ItemStack} (item id AND every data component - custom name, enchantments,
     * anything else) to NBT for storage/transmission, using the given accessor's own registry access.
     * Returns an empty {@link CompoundTag} for {@link ItemStack#isEmpty()} (the "no icon set" sentinel -
     * {@link #decodeItemStack} recognizes it the same way, short-circuiting before ever touching the
     * codec, so an empty stack never actually round-trips through it).
     */
    public static CompoundTag encodeItemStack(LevelAccessor world, ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return new CompoundTag();
        //? if >=1.20.5 {
        /*RegistryOps<Tag> ops = world.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
        return encoded instanceof CompoundTag compound ? compound : new CompoundTag();
        *///? } else {
        return stack.save(new CompoundTag());
        //?}
    }

    /** The inverse of {@link #encodeItemStack} - an empty/absent tag decodes back to {@link ItemStack#EMPTY}. */
    public static ItemStack decodeItemStack(LevelAccessor world, CompoundTag tag) {
        if (tag == null || tag.isEmpty())
            return ItemStack.EMPTY;
        //? if >=1.20.5 {
        /*RegistryOps<Tag> ops = world.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
        *///? } else {
        return ItemStack.of(tag);
        //?}
    }

    /** Whether {@code conversationId} IS a group - i.e. {@link #createGroup} was ever called for it -
     * regardless of how many members currently remain. A group that's shrunk to 2 (or even 1) people via
     * exclusion is still that group, not a plain 1:1: its id is a random token (see
     * {@link #generateGroupConversationId}), completely unrelated to whatever a real 1:1 conversation id
     * between any of its members would be, so there's no ambiguity to resolve either way. */
    public static boolean hasGroupMeta(LevelAccessor world, String conversationId) {
        return PhoneRegistrySavedData.get(world).groupMeta.get(conversationId) instanceof CompoundTag;
    }

    /**
     * A fresh, random id for a new group conversation - deliberately NOT derived from the members' phone
     * numbers the way a 1:1 conversation id is. Two people can only ever have one 1:1 conversation
     * together, so deriving that id from their sorted numbers is correct and lets either side "find" it
     * independently; a group has no such constraint - a player must be able to create several distinct
     * groups that happen to share the exact same membership (e.g. two different "trip planning" chats
     * with the same three friends), and a numbers-derived id would collide them into a single
     * conversation. The "group-" prefix also makes the id visually unmistakable from a 1:1 id (which is
     * always dot-joined numbers), as a defensive backstop against anything that might otherwise try to
     * parse it as one.
     */
    public static String generateGroupConversationId() {
        return "group-" + UUID.randomUUID();
    }

    /** The conversation's current, live participant list: a group's {@code groupMeta.members} if it has
     * one (which shrinks as people are excluded), otherwise the numbers baked into the conversationId
     * itself (a plain 1:1, or a group that hasn't been created via {@link #createGroup} yet). Every
     * permission check and message-routing call site should resolve participants through this, not
     * {@link #getNumbersFromConversationId} directly, so an excluded member loses access immediately. */
    public static List<String> getGroupMembers(LevelAccessor world, String conversationId) {
        Tag raw = PhoneRegistrySavedData.get(world).groupMeta.get(conversationId);
        if (raw instanceof CompoundTag tag) {
            List<String> members = readMembers(tag);
            if (!members.isEmpty())
                return members;
        }
        return getNumbersFromConversationId(conversationId);
    }

    /**
     * These 4 methods write the phone's "screen open" / "call state" directly into the ITEM's own persisted
     * data (the same CustomData tag "number"/"password" already live in) instead of any client-only field,
     * so that vanilla's existing equipment-sync (which already replicates a held item's data to every nearby
     * tracking client, purely because the ItemStack's own data changed) carries it to bystanders for free -
     * zero new packets, and it's automatically correct for whichever specific phone stack this is, not just
     * "the local viewer's own phone". Before this, {@link fr.lordfinn.crazyphone.item.CrazyPhoneItemProperties}
     * read purely client-local state (Minecraft.getInstance().screen/.player, ClientCallState), so a phone
     * screen lighting up or a call texture only ever rendered correctly for the owning player's own view -
     * never for another player standing nearby watching them.
     */
    private static final String TAG_SCREEN_OPEN = "screenOpen";
    private static final String TAG_CALL_STATE = "callState";

    public static void setPhoneScreenOpen(ItemStack phone, boolean open) {
        PhoneTagAccess.updateTag(phone, tag -> {
            if (open)
                tag.putBoolean(TAG_SCREEN_OPEN, true);
            else
                tag.remove(TAG_SCREEN_OPEN);
        });
    }

    public static boolean isPhoneScreenOpen(ItemStack phone) {
        return NbtCompat.getBoolean(PhoneTagAccess.getTag(phone), TAG_SCREEN_OPEN);
    }

    private static void setPhoneCallState(ItemStack phone, String state) {
        PhoneTagAccess.updateTag(phone, tag -> {
            if (state.isEmpty())
                tag.remove(TAG_CALL_STATE);
            else
                tag.putString(TAG_CALL_STATE, state);
        });
    }

    public static String getPhoneCallState(ItemStack phone) {
        return NbtCompat.getString(PhoneTagAccess.getTag(phone), TAG_CALL_STATE);
    }

    /** Applies {@code state} to every CrazyPhone anywhere in {@code player}'s inventory whose OWN registered
     * number is one of {@code callNumbers} - not just the one in their main hand, since a call stays "active"
     * on a phone even after it's put away (see the item's in_call texture), and not every phone the player
     * happens to be carrying, since a player can own several registered numbers at once (see the per-item
     * call-state fix this mirrors). */
    public static void setCallStateForMatchingPhones(ServerPlayer player, List<String> callNumbers, String state) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.CRAZY_PHONE.get() && callNumbers.contains(GetCrazyPhoneNumberProcedure.execute(stack, player.level())))
                setPhoneCallState(stack, state);
        }
        // While a custom phone menu is open, vanilla's per-tick slot broadcast never looks at the hotbar
        // (see CrazyPhoneDefaultScreenMenu's constructor for the same fix) - force it here too, or the
        // calling/called_in/in_call texture never reaches the client until the menu happens to close.
        // broadcastChanges, not broadcastFullState - see that same call site for why full-content resync
        // visibly flickers the held item.
        player.inventoryMenu.broadcastChanges();
    }

    /** Only one call can ever be active per player (CallRegistry refuses to stack a second one), so ending a
     * call can safely clear every phone the player holds rather than needing to know which numbers were on
     * it. */
    public static void clearCallStateForAllPhones(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.CRAZY_PHONE.get())
                setPhoneCallState(stack, "");
        }
        player.inventoryMenu.broadcastChanges();
    }

    /** screenOpen/callState are written straight into the item's own persisted NBT (see the 4 methods
     * above) so vanilla's equipment sync carries them to bystanders for free - but that also means they
     * survive on disk through anything that skips the normal cleanup path: a server crash mid-call, or a
     * player alt-F4ing instead of a graceful disconnect. CallRegistry itself is in-memory only and always
     * starts empty on a fresh server boot, so "this player isn't actually in a call or looking at a screen"
     * is always the correct baseline the instant they join - a genuinely still-active session (the server
     * merely had a network hiccup, not a restart) gets its real state pushed back moments later by the
     * normal CrazyPhoneCallStateSyncPacket/menu-open flow, so clearing here first is never wrong, only
     * briefly premature. Called from PhoneAttachmentTypes#onPlayerLoggedIn - every join, not just the first
     * one after a crash, since there's no cheap way to tell those apart from here. */
    public static void reconcilePhoneStateOnJoin(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == ModItems.CRAZY_PHONE.get()) {
                setPhoneScreenOpen(stack, false);
                setPhoneCallState(stack, "");
            }
        }
    }

    /** Tells every ONLINE member of {@code conversationId} - not just the call's own participants/ringers -
     * whether it currently has a live call, so someone who left a group call (or was never on it) can still
     * see it's happening and rejoin: the contacts-list badge and the conversation screen's call icon both
     * read this via ClientCallState. Only call this on the two transitions that actually flip the boolean
     * (a session starting fresh, or fully ending) - see CallRegistry. */
    public static void broadcastConversationCallActivity(Level world, String conversationId, boolean active) {
        MinecraftServer server = world.getServer();
        if (server == null)
            return;
        for (String number : getGroupMembers(world, conversationId)) {
            Contact member = getContact(world, number);
            if (member == null || member.getUuid() == null)
                continue;
            ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(member.getUuid()));
            if (player != null)
                NetworkAccess.sendToPlayer(player, new ConversationCallActivitySyncPacket(conversationId, active));
        }
    }

    private static List<String> readMembers(CompoundTag groupMetaTag) {
        List<String> members = new ArrayList<>();
        if (groupMetaTag.get("members") instanceof ListTag list) {
            for (Tag t : list)
                if (t instanceof StringTag s)
                    members.add(NbtCompat.asString(s));
        }
        return members;
    }

    /** Resolves a group's current members to full {@link Contact} records (name/uuid/skin), for rendering
     * heads and names in the contacts grid / group settings screen. */
    public static List<Contact> getGroupMemberContacts(Level world, String conversationId) {
        List<Contact> contacts = new ArrayList<>();
        for (String number : getGroupMembers(world, conversationId)) {
            Contact contact = getContact(world, number);
            if (contact != null)
                contacts.add(contact);
        }
        return contacts;
    }

    /**
     * Creates {@code conversationId}'s group metadata the first time it's seen (idempotent - a no-op if
     * it already exists, so this is safe to call defensively on every message too) and makes sure every
     * given member's phone lists it under "groups" so it shows up in their Contacts screen. A no-op for a
     * plain 1:1 conversation (fewer than 3 members) - those are identified by the other person's contact
     * entry, not a separate group entry.
     */
    public static void createGroup(Level world, String conversationId, List<String> members, String adminNumber) {
        if (members.size() < 3)
            return;

        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        if (!(registry.groupMeta.get(conversationId) instanceof CompoundTag)) {
            CompoundTag meta = new CompoundTag();
            meta.putString("name", generateDefaultGroupName(world, members));
            meta.put("icon", new CompoundTag());
            meta.putString("admin", adminNumber == null ? "" : adminNumber);
            ListTag membersTag = new ListTag();
            for (String number : members)
                membersTag.add(StringTag.valueOf(number));
            meta.put("members", membersTag);
            registry.groupMeta.put(conversationId, meta);
        }

        addToGroupsList(registry, conversationId, getGroupMembers(world, conversationId));
        registry.setDirty();
    }

    private static final Pattern VOWEL_GROUP = Pattern.compile("[aeiouyAEIOUY]+");
    private static final Random GROUP_NAME_RANDOM = new Random();

    /** Splits a name into rough syllable-sized chunks (e.g. "Georges" -> "Geor", "ges"; "Bouteilles" ->
     * "Bou", "teil", "les") - a deliberately simple heuristic (no real hyphenation dictionary), good enough
     * for a fun randomized name and not meant to be linguistically precise. Follows the "maximal onset"
     * convention for where to split a consonant cluster sitting between two vowel groups: a single
     * consonant starts the next syllable whole ("Bou" | "teilles" has just "t" between "ou" and "ei"), but
     * a cluster of 2+ splits down the middle, leaving everything but the last consonant attached to the
     * PRECEDING syllable instead - e.g. "rg" between "eo" and "e" in "Georges" gives "Geor" + "ges", not
     * "Geo" + "rges" (putting the whole cluster on the next syllable reads as an unnatural split). A name
     * with no vowels at all (numbers, symbols-only) comes back as a single "syllable": itself. */
    private static List<String> splitIntoSyllables(String word) {
        Matcher vowelGroups = VOWEL_GROUP.matcher(word);
        List<int[]> vowelRanges = new ArrayList<>();
        while (vowelGroups.find())
            vowelRanges.add(new int[]{vowelGroups.start(), vowelGroups.end()});
        if (vowelRanges.size() <= 1)
            return List.of(word);

        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);
        for (int i = 0; i < vowelRanges.size() - 1; i++) {
            int consonantStart = vowelRanges.get(i)[1];
            int consonantEnd = vowelRanges.get(i + 1)[0];
            boundaries.add(consonantEnd - consonantStart <= 1 ? consonantStart : consonantEnd - 1);
        }
        boundaries.add(word.length());

        List<String> syllables = new ArrayList<>();
        for (int i = 0; i < boundaries.size() - 1; i++) {
            String syllable = word.substring(boundaries.get(i), boundaries.get(i + 1));
            if (!syllable.isEmpty())
                syllables.add(syllable);
        }
        return syllables.isEmpty() ? List.of(word) : syllables;
    }

    /** Builds a fun default group name by picking one random syllable from each member's own contact name
     * and running them together into a single portmanteau (e.g. "LordFinn" + "Bouteilles" + "Georges" might
     * come out "Lo" + "teil" + "Geo" = "LoteilGeo") - used as {@code createGroup}'s initial name instead of
     * a plain ", "-joined list of full names. The admin can always rename it afterward from the group
     * settings screen; this is only ever the starting point. */
    private static String generateDefaultGroupName(Level world, List<String> memberNumbers) {
        List<String> parts = new ArrayList<>();
        for (String number : memberNumbers) {
            Contact contact = getContact(world, number);
            String name = contact != null ? contact.getName() : null;
            if (name == null || name.isBlank())
                continue;
            List<String> syllables = splitIntoSyllables(name);
            String chosen = syllables.get(GROUP_NAME_RANDOM.nextInt(syllables.size()));
            parts.add(Character.toUpperCase(chosen.charAt(0)) + chosen.substring(1).toLowerCase());
        }
        return String.join("", parts);
    }

    /** Makes sure every given member's phone registry entry lists {@code conversationId} under "groups" -
     * this (like {@code groupMeta} itself) is pure server-side bookkeeping: {@link CrazyPhoneGetGroupsProcedure}
     * re-reads it fresh from the world every time a client opens the Contacts or group settings screen, no
     * client ever reads it from its own locally-synced registry copy, so none of the group-mutating methods
     * below need to sync anything over the network - a disk-persistence mark is enough. */
    private static void addToGroupsList(PhoneRegistrySavedData registry, String conversationId, List<String> members) {
        CompoundTag phonesTag = registry.phones;

        for (String number : members) {
            if (!(phonesTag.get(number) instanceof CompoundTag phoneCompoundTag))
                continue;

            Tag groupsTag = phoneCompoundTag.get("groups");
            ListTag groups = (groupsTag instanceof ListTag listTag) ? listTag : new ListTag();

            boolean alreadyMember = false;
            for (Tag tag : groups) {
                if (tag instanceof StringTag stringTag && NbtCompat.asString(stringTag).equals(conversationId)) {
                    alreadyMember = true;
                    break;
                }
            }
            if (alreadyMember)
                continue;

            groups.add(StringTag.valueOf(conversationId));
            phoneCompoundTag.put("groups", groups);
            phonesTag.put(number, phoneCompoundTag);
        }
    }

    /** Tells {@code memberNumber} (if currently online) that {@code actorName} just added them to a group,
     * the same "toast + sound" notification style as a new message - a targeted send, never a broadcast. */
    public static void notifyGroupAddition(Level world, String memberNumber, String groupLabel, String actorName) {
        sendGroupMembershipNotification(world, memberNumber, groupLabel, actorName, true);
    }

    /** Tells {@code memberNumber} (if currently online) that {@code actorName} just removed them from a
     * group - only meant for an admin excluding someone else; a voluntary leave doesn't need this, the
     * leaver already knows. */
    public static void notifyGroupRemoval(Level world, String memberNumber, String groupLabel, String actorName) {
        sendGroupMembershipNotification(world, memberNumber, groupLabel, actorName, false);
    }

    private static void sendGroupMembershipNotification(Level world, String memberNumber, String groupLabel, String actorName, boolean added) {
        MinecraftServer server = world.getServer();
        if (server == null)
            return;
        Contact memberContact = getContact(world, memberNumber);
        if (memberContact == null || memberContact.getUuid() == null)
            return;
        ServerPlayer memberPlayer = server.getPlayerList().getPlayer(UUID.fromString(memberContact.getUuid()));
        if (memberPlayer != null)
            NetworkAccess.sendToPlayer(memberPlayer, new CrazyPhoneGroupMembershipNotificationPacket(groupLabel, actorName, added));
    }

    public static void renameGroup(Level world, String conversationId, String newName) {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        if (!(registry.groupMeta.get(conversationId) instanceof CompoundTag meta))
            return;
        meta.putString("name", newName == null ? "" : newName);
        registry.groupMeta.put(conversationId, meta);
        registry.setDirty();
    }

    public static void setGroupIcon(Level world, String conversationId, ItemStack icon) {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        if (!(registry.groupMeta.get(conversationId) instanceof CompoundTag meta))
            return;
        meta.put("icon", encodeItemStack(world, icon));
        registry.groupMeta.put(conversationId, meta);
        registry.setDirty();
    }

    /**
     * Removes {@code memberNumber} from {@code conversationId}'s live membership and from their own
     * "groups" list (so it disappears from their Contacts screen - their message history access is also
     * gated on live membership, see the ownership checks in ConversationRequestPacket /
     * CrazyPhoneConversationButtonMessage). If the excluded member was the admin, a random remaining
     * member is promoted. Returns the new admin's number if one was (re)assigned, or null if the admin
     * didn't change (including "no members left to promote").
     */
    @Nullable
    public static String excludeGroupMember(Level world, String conversationId, String memberNumber) {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        if (!(registry.groupMeta.get(conversationId) instanceof CompoundTag meta))
            return null;

        List<String> remaining = new ArrayList<>();
        for (String number : readMembers(meta))
            if (!number.equals(memberNumber))
                remaining.add(number);

        ListTag updatedMembers = new ListTag();
        for (String number : remaining)
            updatedMembers.add(StringTag.valueOf(number));
        meta.put("members", updatedMembers);

        String newAdmin = null;
        if (NbtCompat.getString(meta, "admin").equals(memberNumber) && !remaining.isEmpty()) {
            newAdmin = remaining.get(ThreadLocalRandom.current().nextInt(remaining.size()));
            meta.putString("admin", newAdmin);
        }
        registry.groupMeta.put(conversationId, meta);

        if (registry.phones.get(memberNumber) instanceof CompoundTag phoneTag && phoneTag.get("groups") instanceof ListTag groups) {
            ListTag updatedGroups = new ListTag();
            for (Tag t : groups) {
                if (!(t instanceof StringTag s) || !NbtCompat.asString(s).equals(conversationId))
                    updatedGroups.add(t);
            }
            phoneTag.put("groups", updatedGroups);
            registry.phones.put(memberNumber, phoneTag);
        }

        registry.setDirty();
        return newAdmin;
    }

    /** Adds {@code memberNumber} to {@code conversationId}'s live membership (a no-op if they're already
     * in it) and registers the group under their own "groups" list so it shows up in their Contacts
     * screen - the mirror image of {@link #excludeGroupMember}. */
    public static void addGroupMember(Level world, String conversationId, String memberNumber) {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        if (!(registry.groupMeta.get(conversationId) instanceof CompoundTag meta))
            return;

        List<String> members = readMembers(meta);
        if (members.contains(memberNumber))
            return;

        ListTag updatedMembers = new ListTag();
        for (String number : members)
            updatedMembers.add(StringTag.valueOf(number));
        updatedMembers.add(StringTag.valueOf(memberNumber));
        meta.put("members", updatedMembers);
        registry.groupMeta.put(conversationId, meta);

        addToGroupsList(registry, conversationId, List.of(memberNumber));
        registry.setDirty();
    }

    //? if >=1.20.5 {
    /*public static List<MessageData> getMessagesFromBuf(RegistryFriendlyByteBuf buffer) {
        Tag rawTag = RegistryFriendlyByteBuf.readNbt(buffer, NbtAccounter.create(2097152L));
    *///? } else {
    public static List<MessageData> getMessagesFromBuf(FriendlyByteBuf buffer) {
        Tag rawTag = buffer.readNbt(NbtAccounter.create(2097152L));
    //?}
        List<MessageData> messageDatas = new ArrayList<>();

        if (!(rawTag instanceof ListTag listTag)) {
            return messageDatas;
        }

        for (Tag entry : listTag) {
            if (entry instanceof CompoundTag messageTag) {
                MessageData data = getMessageFromTag(messageTag);
                if (data != null) {
                    messageDatas.add(data);
                }
            }
        }

        return messageDatas;
    }

    public static @Nullable MessageData getMessageFromTag(CompoundTag messageTag) {
        if (messageTag == null) return null;

        if (messageTag.get("call") instanceof CompoundTag callTag) {
            int timecode = NbtCompat.getInt(messageTag, "timecode");
            UUID callId = new UUID(NbtCompat.getLong(callTag, "call_id_most"), NbtCompat.getLong(callTag, "call_id_least"));
            long startMillis = NbtCompat.getLong(callTag, "call_start_millis");
            long durationMillis = NbtCompat.getLong(callTag, "call_duration_millis");
            return MessageData.call(timecode, callId, startMillis, durationMillis);
        }

        if (NbtCompat.getBoolean(messageTag, "system")) {
            int timecode = NbtCompat.getInt(messageTag, "timecode");
            Component text = NbtCompat.contains(messageTag, "systemText")
                    ? ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, messageTag.get("systemText")).result().orElse(Component.empty())
                    : Component.empty();
            ItemStack icon = ItemStack.EMPTY;
            if (NbtCompat.contains(messageTag, "systemIcon")) {
                ResourceLocation id = ResourceLocation.tryParse(NbtCompat.getString(messageTag, "systemIcon"));
                if (id != null && BuiltInRegistries.ITEM.containsKey(id))
                    icon = new ItemStack(RegistryCompat.get(BuiltInRegistries.ITEM, id));
            }
            return MessageData.system(timecode, text, icon);
        }

        String value = NbtCompat.getString(messageTag, "value");
        String sender = NbtCompat.getString(messageTag, "sender");
        int timecode = NbtCompat.getInt(messageTag, "timecode");

        if (NbtCompat.contains(messageTag, "voice")) {
            CompoundTag voiceTag = NbtCompat.getCompound(messageTag, "voice");
            UUID voiceId = new UUID(NbtCompat.getLong(voiceTag, "voice_id_most"), NbtCompat.getLong(voiceTag, "voice_id_least"));
            byte[] envelope = voiceTag.get("voice_envelope") instanceof net.minecraft.nbt.ByteArrayTag tag ? tag.getAsByteArray() : new byte[0];
            return MessageData.voice(timecode, sender, voiceId, NbtCompat.getInt(voiceTag, "voice_duration_ticks"), envelope);
        }

        // Native picture pipeline format, shared by both loaders (see addImageMessage) - bytes are fetched
        // lazily on demand from PhotoSavedData, keyed by this id, never eagerly loaded here. A message
        // persisted before the native pipeline existed (NeoForge only, real Camera-mod item data) has an
        // "image" tag without this field - Camera mod's own classes are gone along with it, so that old
        // image is unrecoverable and the message just renders as a plain (imageless) text bubble.
        if (NbtCompat.contains(messageTag, "image")) {
            CompoundTag imageTag = NbtCompat.getCompound(messageTag, "image");
            if (NbtCompat.contains(imageTag, "image_id_most")) {
                UUID imageId = new UUID(NbtCompat.getLong(imageTag, "image_id_most"), NbtCompat.getLong(imageTag, "image_id_least"));
                return MessageData.image(timecode, sender, imageId);
            }
        }

        return new MessageData(timecode, value, sender);
    }

    public static List<Contact> getContactsFromBuf(FriendlyByteBuf buffer) {
        List<Contact> contacts = new ArrayList<>();
        if (buffer.readableBytes() <= 0)
            return contacts;
        int contactsCount = buffer.readInt(); // Read the number of contact
        for (int i = 0; i < contactsCount; i++) {
            String name = buffer.readUtf(); // Read name
            String number = buffer.readUtf(); // Read number
            String uuid = buffer.readUtf(); // Read uuid
            String skin = buffer.readUtf(); // Read skin
            contacts.add(new Contact(number, name, skin, uuid));
        }
        return contacts;
    }

    private static JsonObject readJsonFromUrl(URL url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            try (InputStreamReader reader = new InputStreamReader(connection.getInputStream())) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static GameProfile applySkinToProfile(GameProfile profile, String uuid) {
        try {
            // Fetch skin properties from Mojang session server
            URL skinUrl = new URL(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false");
            JsonObject skinResponse = readJsonFromUrl(skinUrl);
            if (skinResponse == null || !skinResponse.has("properties"))
                return null;

            JsonArray properties = skinResponse.getAsJsonArray("properties");
            for (JsonElement element : properties) {
                JsonObject property = element.getAsJsonObject();
                String name = property.get("name").getAsString();
                String value = property.get("value").getAsString();
                String signature = property.has("signature") ? property.get("signature").getAsString() : null;

                GameProfileCompat.properties(profile).put(name, new Property(name, value, signature));
            }
            return profile;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return profile;
    }

}
