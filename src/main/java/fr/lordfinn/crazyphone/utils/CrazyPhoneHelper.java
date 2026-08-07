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
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
            List<String> numbers = getNumbersFromConversationId(conversationId);
            notifyContacts(world, messageTag, numbers, senderNumber, message, timestampInMinutes, conversationId);
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

            PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
            CompoundTag phonesTag = registry.phones;

            Tag receiverPhone = phonesTag.get(receiverNumber);
            if (receiverPhone instanceof CompoundTag receiverPhoneCompoundTag) {
                // Récupération ou création de la liste des notifications
                Tag notificationstag = receiverPhoneCompoundTag.get("notifications");
                ListTag notifications = (notificationstag instanceof ListTag listTag) ? listTag : new ListTag();

                // Vérifie si conversationId est déjà présent
                boolean alreadyExists = false;
                for (Tag tag : notifications) {
                    if (tag instanceof StringTag stringTag && stringTag.getAsString().equals(conversationId)) {
                        alreadyExists = true;
                        break;
                    }
                }

                // Ajout si pas encore présent
                if (!alreadyExists) {
                    notifications.add(StringTag.valueOf(conversationId));
                    receiverPhoneCompoundTag.put("notifications", notifications);
                    phonesTag.put(receiverNumber, receiverPhoneCompoundTag);
                    // Only the receiver's own notification badge changed - sync just to them if they're
                    // online (still persisted to disk either way via setDirty, so it's there next login).
                    if (receiverPlayer != null)
                        registry.syncTo(receiverPlayer);
                    else
                        registry.setDirty();
                }
            }

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
