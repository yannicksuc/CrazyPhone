package fr.lordfinn.crazyphone.utils;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import de.maxhenkel.camera.ImageData;
import de.maxhenkel.camera.Main;
import de.maxhenkel.camera.inventory.AlbumInventory;
import de.maxhenkel.camera.items.AlbumItem;
import de.maxhenkel.camera.items.ImageItem;
import fr.lordfinn.crazyphone.client.gui.components.MessageData;
import fr.lordfinn.crazyphone.data.ConversationSavedData;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.item.CrazyPhoneItem;
import fr.lordfinn.crazyphone.network.CrazyPhoneGroupMembershipNotificationPacket;
import fr.lordfinn.crazyphone.network.CrazyPhoneNewMessageNotificationPacket;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneAddContactToPhoneProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneGetContactsProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.resources.RegistryOps;
import java.util.concurrent.ThreadLocalRandom;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.network.PacketDistributor;

public class CrazyPhoneHelper {

    /** The item in entity's main hand, or ItemStack.EMPTY for a non-living entity (e.g. a fake player) or none held. */
    public static ItemStack getMainHandItemOrEmpty(Entity entity) {
        return entity instanceof LivingEntity livingEntity ? livingEntity.getMainHandItem() : ItemStack.EMPTY;
    }

    public static void deleteSlotsFromAlbum(AlbumInventory inventory, Set<Integer> slots) {
        // slots ultimately comes from a client-supplied CSV (CrazyPhonePicturesScreenButtonMessage) - never
        // trust it to already be in range.
        for (int slot : slots) {
            if (slot >= 0 && slot < inventory.getContainerSize())
                inventory.setItem(slot, ItemStack.EMPTY);
        }
    }

    @SuppressWarnings("null")
    public static void deleteSelectedAlbumSlotsFromHeldPhone(Player entity, Level world, Set<Integer> slots,
            Integer albumId) {

        IItemHandlerModifiable handler = getPhoneItemHandler(entity);
        ItemStack albumStack = getAlbumFromPhoneHandler(handler, albumId);

        if (!(albumStack.getItem() instanceof AlbumItem))
            return;

        AlbumInventory inventory = new AlbumInventory(world.registryAccess(), albumStack);
        deleteSlotsFromAlbum(inventory, slots);

        handler.setStackInSlot(albumId, albumStack);
    }

    @Nullable
    public static IItemHandlerModifiable getPhoneItemHandler(Player player) {
        ItemStack held = player.getMainHandItem();

        if (!(held.getItem() instanceof CrazyPhoneItem))
            return null;

        IItemHandler handler = held.getCapability(Capabilities.ItemHandler.ITEM, null);

        if (!(handler instanceof IItemHandlerModifiable modifiableHandler))
            return null;

        return modifiableHandler;
    }

    public static Contact getContact(Level world, String number) {
        Tag potentialContact = PhoneRegistrySavedData.get(world).phones.get(number);
        if (potentialContact != null && potentialContact instanceof CompoundTag contactTag) {
            String name = (contactTag.get("name")) instanceof StringTag _stringTag ? _stringTag.getAsString() : "";
            String uuid = (contactTag.get("uuid")) instanceof StringTag _stringTag ? _stringTag.getAsString() : "";
            String skin = (contactTag.get("skin")) instanceof StringTag _stringTag ? _stringTag.getAsString() : "";
            return new Contact(number, name, skin, uuid);
        }
        return null;
    }

    public static ItemStack getAlbumFromPhoneHandler(IItemHandlerModifiable handler, int albumIndex) {
        if (handler == null)
            return ItemStack.EMPTY;
        if (albumIndex < 0 || albumIndex >= handler.getSlots())
            return ItemStack.EMPTY;
        return handler.getStackInSlot(albumIndex);
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
        return Component.literal(name == null ? "Inconnu" : name).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
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
        head.set(DataComponents.CUSTOM_NAME, displayName);

        CustomData.update(DataComponents.CUSTOM_DATA, head, tag -> tag.putString("number", contact.getNumber()));
        CustomData.update(DataComponents.CUSTOM_DATA, head, tag -> tag.putString("name", contact.getName()));

        if (contact.getUuid() != null && !contact.getUuid().isEmpty()) {
            try {
                CustomData.update(DataComponents.CUSTOM_DATA, head, tag -> tag.putString("uuid", contact.getUuid()));
                GameProfile profile = new GameProfile(UUID.fromString(contact.getUuid()), "CustomHead");
                PropertyMap properties = profile.getProperties();
                if (contact.getSkin() != null || !contact.getSkin().isEmpty()) {
                    properties.put("textures", new Property("textures", contact.getSkin()));
                }
                ResolvableProfile resolvableProfile = new ResolvableProfile(profile);
                head.set(DataComponents.PROFILE, resolvableProfile);
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
                              int timestampInMinutes, @Nullable ItemStack image) {
        synchronized (messageLock) {
            CompoundTag messageTag = createMessageTag(senderNumber, message, timestampInMinutes, image);
            ConversationSavedData.get(world).appendMessage(conversationId, messageTag);
            List<String> numbers = getGroupMembers(world, conversationId);
            notifyContacts(world, messageTag, numbers, senderNumber, message, timestampInMinutes, conversationId);
        }
    }

    /** The timecode of the most recent message in a conversation, or 0 if it has none yet - used to sort
     * the contacts/groups grid by recency (most recently active conversation first). */
    public static int getLastMessageTimecode(LevelAccessor world, String conversationId) {
        List<CompoundTag> last = ConversationSavedData.get(world).getPage(conversationId, 0, 1);
        return last.isEmpty() ? 0 : last.get(0).getInt("timecode");
    }

    /** Toggles whether {@code number} is favorited for {@code owner} - favorited contacts are pinned in
     * their own section above the rest of the contacts grid. Marks the registry dirty but does not sync;
     * callers refresh the requesting player's own contacts menu afterward. */
    public static void toggleFavorite(LevelAccessor world, String owner, String number) {
        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        ListTag numbers = registry.favorites.get(owner) instanceof ListTag list ? list.copy() : new ListTag();
        boolean removed = numbers.removeIf(t -> t instanceof StringTag s && s.getAsString().equals(number));
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
                    result.add(s.getAsString());
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
                PacketDistributor.sendToPlayer(receiverPlayer, new CrazyPhoneNewMessageNotificationPacket(messageTag, ""));
            }
            addNotificationBadge(registry, number, conversationId, receiverPlayer);
        }
    }

    private static CompoundTag createMessageTag(String senderNumber, String message, int timestampInMinutes,
                                                @Nullable ItemStack image) {
        CompoundTag tag = new CompoundTag();
        tag.putString("sender", senderNumber);
        tag.putString("value", message);
        tag.putInt("timecode", timestampInMinutes);
        if (image != null && !image.isEmpty() && image.getItem() instanceof ImageItem) {
            if (image.has(Main.IMAGE_DATA_COMPONENT)) {
                ImageData imageData = image.get(Main.IMAGE_DATA_COMPONENT);
                CompoundTag data = imageDataToCompoundTag(imageData);
                tag.put("image", data);
            }
        }
        return tag;
    }

    private static CompoundTag createSystemMessageTag(Component text, @Nullable ItemStack icon, int timestampInMinutes) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("system", true);
        tag.putInt("timecode", timestampInMinutes);
        tag.put("systemText", ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, text).getOrThrow());
        if (icon != null && !icon.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(icon.getItem());
            tag.putString("systemIcon", id.toString());
        }
        return tag;
    }

    public static CompoundTag imageDataToCompoundTag(ImageData data) {
        CompoundTag tag = new CompoundTag();

        tag.putLong("image_id_most", data.getId().getMostSignificantBits());
        tag.putLong("image_id_least", data.getId().getLeastSignificantBits());
        tag.putLong("image_time", data.getTime());
        tag.putString("owner", data.getOwner());

        if (data.getBiome() != null) {
            tag.putString("biome", data.getBiome().toString());
        }

        List<ResourceLocation> entities = data.getEntities();
        if (entities != null && !entities.isEmpty()) {
            ListTag entityListTag = new ListTag();
            for (ResourceLocation entityID : entities) {
                entityListTag.add(StringTag.valueOf(entityID.toString()));
            }
            tag.put("entities", entityListTag);
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
                PacketDistributor.sendToPlayer(receiverPlayer, new CrazyPhoneNewMessageNotificationPacket(messageTag,sender.getName()));
            }

            addNotificationBadge(registry, receiverNumber, conversationId, receiverPlayer);

            ListTag contactsOfReceiver = CrazyPhoneGetContactsProcedure.execute(world, receiverNumber);
            boolean hasContact = false;
            for (Tag contact : contactsOfReceiver) {
                if (contact instanceof CompoundTag compoundTag
                        && senderNumber.equals(compoundTag.getString("number"))) {
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
            if (tag instanceof StringTag stringTag && stringTag.getAsString().equals(conversationId))
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
        return new GroupMeta(tag.getString("name"), decodeItemStack(world, tag.getCompound("icon")), tag.getString("admin"), readMembers(tag));
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
        RegistryOps<Tag> ops = world.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
        return encoded instanceof CompoundTag compound ? compound : new CompoundTag();
    }

    /** The inverse of {@link #encodeItemStack} - an empty/absent tag decodes back to {@link ItemStack#EMPTY}. */
    public static ItemStack decodeItemStack(LevelAccessor world, CompoundTag tag) {
        if (tag == null || tag.isEmpty())
            return ItemStack.EMPTY;
        RegistryOps<Tag> ops = world.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
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

    private static List<String> readMembers(CompoundTag groupMetaTag) {
        List<String> members = new ArrayList<>();
        if (groupMetaTag.get("members") instanceof ListTag list) {
            for (Tag t : list)
                if (t instanceof StringTag s)
                    members.add(s.getAsString());
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
            meta.putString("name", "");
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
                if (tag instanceof StringTag stringTag && stringTag.getAsString().equals(conversationId)) {
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
            PacketDistributor.sendToPlayer(memberPlayer, new CrazyPhoneGroupMembershipNotificationPacket(groupLabel, actorName, added));
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
        if (meta.getString("admin").equals(memberNumber) && !remaining.isEmpty()) {
            newAdmin = remaining.get(ThreadLocalRandom.current().nextInt(remaining.size()));
            meta.putString("admin", newAdmin);
        }
        registry.groupMeta.put(conversationId, meta);

        if (registry.phones.get(memberNumber) instanceof CompoundTag phoneTag && phoneTag.get("groups") instanceof ListTag groups) {
            ListTag updatedGroups = new ListTag();
            for (Tag t : groups) {
                if (!(t instanceof StringTag s) || !s.getAsString().equals(conversationId))
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

    public static List<MessageData> getMessagesFromBuf(RegistryFriendlyByteBuf buffer) {
        List<MessageData> messageDatas = new ArrayList<>();
        Tag rawTag = RegistryFriendlyByteBuf.readNbt(buffer, NbtAccounter.create(2097152L));

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

        if (messageTag.getBoolean("system")) {
            int timecode = messageTag.getInt("timecode");
            Component text = messageTag.contains("systemText")
                    ? ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, messageTag.get("systemText")).result().orElse(Component.empty())
                    : Component.empty();
            ItemStack icon = ItemStack.EMPTY;
            if (messageTag.contains("systemIcon", Tag.TAG_STRING)) {
                ResourceLocation id = ResourceLocation.tryParse(messageTag.getString("systemIcon"));
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                    icon = new ItemStack(BuiltInRegistries.ITEM.get(id));
                }
            }
            return MessageData.system(timecode, text, icon);
        }

        String value = messageTag.getString("value");
        String sender = messageTag.getString("sender");
        int timecode = messageTag.getInt("timecode");

        ItemStack stack = ItemStack.EMPTY;
        if (messageTag.contains("image", Tag.TAG_COMPOUND)) {
            CompoundTag imageTag = messageTag.getCompound("image");
            ImageData imageData = ImageData.fromImageTag(imageTag);
            if (imageData != null) {
                stack = new ItemStack(Main.IMAGE.get());
                imageData.addToImage(stack);
            }
        }

        return new MessageData(timecode, value, sender, stack);
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

                profile.getProperties().put(name, new Property(name, value, signature));
            }
            return profile;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return profile;
    }

    public static void takeSelectedAlbumSlotsFromHeldPhone(Player entity, Level world, Set<Integer> slotsToCopy,
            int albumId) {
        IItemHandlerModifiable handler = getPhoneItemHandler(entity);
        if (handler == null)
            return;

        ItemStack albumStack = getAlbumFromPhoneHandler(handler, albumId).copy();
        if (!(albumStack.getItem() instanceof AlbumItem))
            return;

        AlbumInventory inventory = new AlbumInventory(world.registryAccess(), albumStack);

        for (int slot : slotsToCopy) {
            if (slot < 0 || slot >= inventory.getContainerSize())
                continue;
            ItemStack itemToCopy = inventory.getItem(slot).copy();

            if (itemToCopy.isEmpty())
                continue;
            boolean added = entity.getInventory().add(itemToCopy);
            if (!added && !world.isClientSide()) {
                entity.drop(itemToCopy, false);
            }
        }
    }

    public static void sendSelectedAlbumSlotsFromHeldPhone(Player entity, Level world, Set<Integer> selectedSlots, int albumId, String conversationId) {

        IItemHandlerModifiable handler = getPhoneItemHandler(entity);
        if (handler == null)
            return;

        ItemStack albumStack = getAlbumFromPhoneHandler(handler, albumId).copy();
        if (!(albumStack.getItem() instanceof AlbumItem))
            return;
        String senderNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(entity, null);
        AlbumInventory inventory = new AlbumInventory(world.registryAccess(), albumStack);
		int timestampInMinutes = (int) (Instant.now().getEpochSecond() / 60);

        for (int slot : selectedSlots) {
            if (slot < 0 || slot >= inventory.getContainerSize())
                continue;
            ItemStack imageToSend = inventory.getItem(slot).copy();

            if (imageToSend.isEmpty())
                continue;
            // addMessage already appends to ConversationSavedData (bounded, per-conversation) and notifies
            // the receiver with a targeted packet - no follow-up full-registry sync needed here, unlike the
            // old code's unconditional MapVariables.syncData(world) call which used to broadcast every
            // conversation's full history to every player after every image send.
            addMessage(world, conversationId, senderNumber, "", timestampInMinutes, imageToSend);
        }
    }
}
