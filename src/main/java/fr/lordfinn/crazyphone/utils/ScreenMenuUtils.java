package fr.lordfinn.crazyphone.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.netty.buffer.Unpooled;
import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.data.PlayerPhoneState;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneGetContactsProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactsScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneConversationMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMayorCandidateScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneMayorsCandidatesListMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePasswordScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePictureFoldersScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhonePicturesScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneSignInScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyphoneHomeScreenMenu;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.LoggerFactory;

public class ScreenMenuUtils {

    public static void openLastCrazyPhoneMenu(Player player, InteractionHand hand) {

        popScreen(player);
        openCurrentCrazyPhoneMenu(player, hand);
    }

    public static void openCurrentCrazyPhoneMenu(Player player, InteractionHand hand) {
        PlayerPhoneState _vars = player.getData(PhoneAttachmentTypes.PLAYER_PHONE_STATE);
        String tag = _vars.currentCrazyPhoneScreenOpened;

        if (tag == null || tag.isEmpty())
            return;

        String screenId = parseScreenIdFromTag(tag);
        String screenData = parseScreenDataFromTag(tag);

        openCrazyPhoneMenuByTag(player, hand, screenId, screenData);
    }

    public static String parseScreenIdFromTag(String tag) {
        int dotIndex = tag.indexOf(';');
        return (dotIndex != -1) ? tag.substring(0, dotIndex) : tag;
    }

    public static String parseScreenDataFromTag(String tag) {
        int dotIndex = tag.indexOf(';');
        return (dotIndex != -1 && dotIndex + 1 < tag.length()) ? tag.substring(dotIndex + 1) : null;
    }

    public static void openCrazyPhoneMenuByTag(Player player, InteractionHand hand, String screenId,
            String screenData) {
        if (screenId == null || screenId.isEmpty())
            return;
        switch (screenId) {
            case "crazyphone:crazyphone_home_screen" ->
                openPhoneCustomMenu(player, hand, CrazyphoneHomeScreenMenu.class);
            case "crazyphone:crazy_phone_sign_in_screen" ->
                openPhoneCustomMenu(player, hand, CrazyPhoneSignInScreenMenu.class);
            case "crazyphone:crazy_phone_password_screen" ->
                openPhoneCustomMenu(player, hand, CrazyPhonePasswordScreenMenu.class);
            case "crazyphone:crazy_phone_picture_folders_screen" ->
                openPhoneCustomMenu(player, hand, CrazyPhonePictureFoldersScreenMenu.class);
            case "crazyphone:crazy_phone_pictures_screen" ->
                openPhoneAlbumMenuWithData(player, hand, screenData);
            case "crazyphone:crazy_phone_contacts_screen" ->
                openPhoneContactsMenu(player, hand);
            case "crazyphone:crazy_phone_conversation" ->
                openPhoneConversationMenu(player, hand, screenData);
            case "crazyphone:crazy_phone_mayors_candidates_list" ->
                openPhoneCustomMenu(player, hand, CrazyPhoneMayorsCandidatesListMenu.class);
            default ->
            	LoggerFactory.getLogger("crazyphone").warn("Unknown screen ID: " + screenId);
        }
    }

    public static void popScreen(Player player) {
        PlayerPhoneState playerData = player.getData(PhoneAttachmentTypes.PLAYER_PHONE_STATE);
        List<String> history = getScreenHistory(playerData.crazyPhoneScreenHistory);

        if (history.size() <= 1)
            return; // on est à la racine

        // Retirer la page actuelle
        history.remove(history.size() - 1);

        String newCurrent = history.get(history.size() - 1);
        playerData.currentCrazyPhoneScreenOpened = newCurrent;
        playerData.crazyPhoneScreenHistory = serializeScreenHistory(history);

        player.setData(PhoneAttachmentTypes.PLAYER_PHONE_STATE, playerData);
    }

    public static void pushScreen(Player player, String screenId, String screenData) {
        PlayerPhoneState playerData = player.getData(PhoneAttachmentTypes.PLAYER_PHONE_STATE);

        String screenTag = (screenData == null || screenData.isEmpty()) ? screenId : screenId + ";" + screenData;
        List<String> history = getScreenHistory(playerData.crazyPhoneScreenHistory);

        if (!history.isEmpty()) {
            String current = history.get(history.size() - 1);
            String currentId = parseScreenIdFromTag(current);

            // Si le screenId est identique mais les données ont changé → on met à jour
            // l'écran actuel sans empiler
            if (currentId.equals(screenId)) {
                history.set(history.size() - 1, screenTag); // mise à jour de l'élément courant
                playerData.crazyPhoneScreenHistory = serializeScreenHistory(history);
                playerData.currentCrazyPhoneScreenOpened = screenTag;
                player.setData(PhoneAttachmentTypes.PLAYER_PHONE_STATE, playerData);
                return;
            }

            // Si c’est exactement la même page (id + data), on ne fait rien
            if (current.equals(screenTag))
                return;
        }

        // Sinon on empile normalement
        history.add(screenTag);
        playerData.crazyPhoneScreenHistory = serializeScreenHistory(history);
        playerData.currentCrazyPhoneScreenOpened = screenTag;

        player.setData(PhoneAttachmentTypes.PLAYER_PHONE_STATE, playerData);
    }

    public static void addDataToCurrentPage(Player player, String newScreenData) {
        PlayerPhoneState playerData = player.getData(PhoneAttachmentTypes.PLAYER_PHONE_STATE);

        List<String> history = getScreenHistory(playerData.crazyPhoneScreenHistory);
        if (history.isEmpty())
            return; // Pas de page à mettre à jour

        String currentTag = history.get(history.size() - 1);
        String currentId = parseScreenIdFromTag(currentTag);

        // Ajouter des données à l'existant
        String newTag = (newScreenData == null || newScreenData.isEmpty()) ? currentId
                : currentId + ";" + newScreenData;

        // Mise à jour de l'élément courant dans l'historique
        history.set(history.size() - 1, newTag);
        playerData.crazyPhoneScreenHistory = serializeScreenHistory(history);
        playerData.currentCrazyPhoneScreenOpened = newTag;

        player.setData(PhoneAttachmentTypes.PLAYER_PHONE_STATE, playerData);
    }

    public static void resetToHomeScreen(Player player) {
        PlayerPhoneState playerData = player.getData(PhoneAttachmentTypes.PLAYER_PHONE_STATE);

        String homeScreen = "crazyphone:crazyphone_home_screen"; // ou un autre identifiant de page d'accueil
        playerData.crazyPhoneScreenHistory = homeScreen;
        playerData.currentCrazyPhoneScreenOpened = homeScreen;

        player.setData(PhoneAttachmentTypes.PLAYER_PHONE_STATE, playerData);
    }

    public static List<String> getScreenHistory(String historyString) {
        if (historyString == null || historyString.isEmpty())
            return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(historyString.split("\\|")));
    }

    public static String serializeScreenHistory(List<String> history) {
        return String.join("|", history);
    }

    private static void openPhoneAlbumMenuWithData(Player player, InteractionHand hand, String screenData) {
        if (screenData == null || screenData.isEmpty()) {
            System.err.println("Missing data for screen 'album'");
            return;
        }

        try {
            int data = Integer.parseInt(screenData);
            openPhoneAlbumMenu(player, hand, data);
        } catch (NumberFormatException e) {
            System.err.println("Invalid number for screen 'album': " + screenData);
        }
    }

    public static void openPhoneCustomMenu(Player player, InteractionHand hand,
            Class<? extends AbstractContainerMenu> menuClass) {
        if (player instanceof ServerPlayer) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crazyphone.crazy_phone");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    packetBuffer.writeBlockPos(player.blockPosition());
                    packetBuffer.writeByte(hand == InteractionHand.MAIN_HAND ? 0 : 1);

                    try {
                        // Assuming the menu class has a constructor that accepts these parameters
                        return menuClass.getConstructor(int.class, Inventory.class, FriendlyByteBuf.class)
                                .newInstance(id, inventory, packetBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create menu instance", e);
                    }
                }
            }, buf -> {
                buf.writeBlockPos(player.blockPosition());
                buf.writeByte(0); // Always Main Hand
            });
        }
    }

    public static void openPhoneMayorCandidateMenu(Player player, String candidateNumber) {
        if (player instanceof ServerPlayer) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crazyphone.crazy_phone");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    packetBuffer.writeBlockPos(player.blockPosition());
                    packetBuffer.writeByte(0);
                    packetBuffer.writeUtf(candidateNumber);
                    try {
                        return new CrazyPhoneMayorCandidateScreenMenu(id, inventory, packetBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create menu instance", e);
                    }
                }
            }, buf -> {
                    buf.writeBlockPos(player.blockPosition());
                    buf.writeByte(0);
                    buf.writeUtf(candidateNumber);
            });
        }
    }

    public static void openPhoneContactsMenu(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer) {
            String ownerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
            ListTag contacts = CrazyPhoneGetContactsProcedure.execute(player.level(), ownerNumber);

            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crazyphone.crazy_phone");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    populateBufferWithMenuData(packetBuffer, player, hand, contacts);

                    try {
                        return new CrazyPhoneContactsScreenMenu(id, inventory, packetBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create menu instance", e);
                    }
                }
            }, buf -> populateBufferWithMenuData(buf, player, hand, contacts));
        }
    }

    private static void populateBufferWithMenuData(FriendlyByteBuf buf, Player player, InteractionHand hand,
            ListTag contacts) {
        buf.writeBlockPos(player.blockPosition());
        buf.writeByte(0); // Always Main Hand
        serializeContactsToBuffer(buf, contacts);
    }

    private static void serializeContactsToBuffer(FriendlyByteBuf buf, ListTag contacts) {
        buf.writeInt(contacts.size());
        for (Tag contact : contacts) {
            if (contact instanceof CompoundTag compoundTag) {
                serializeContactDetails(buf, compoundTag);
            }
        }
    }

    private static void serializeContactDetails(FriendlyByteBuf buf, CompoundTag contact) {
        String number = contact.getString("number");
        String name = contact.getString("name");
        String uuid = contact.contains("uuid", CompoundTag.TAG_STRING) ? contact.getString("uuid") : "";
        String skin = contact.contains("skin", CompoundTag.TAG_STRING) ? contact.getString("skin") : "";

        buf.writeUtf(number);
        buf.writeUtf(name);
        buf.writeUtf(uuid);
        buf.writeUtf(skin);
    }

    public static void openPhoneAlbumMenu(Player player, InteractionHand hand, int albumId) {
        if (player instanceof ServerPlayer serverPlayer) {

            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("minecraft:item.book.page_turn"));
            if (sound != null) {
                serverPlayer.playNotifySound(sound, SoundSource.PLAYERS, 0.7f, 1.2f);
            }

            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crazyphone.crazy_phone");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    packetBuffer.writeBlockPos(player.blockPosition());
                    packetBuffer.writeByte(hand == InteractionHand.MAIN_HAND ? 0 : 1);
                    packetBuffer.writeInt(albumId);

                    try {
                        return new CrazyPhonePicturesScreenMenu(id, inventory, packetBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create menu instance", e);
                    }
                }
            }, buf -> {
                buf.writeBlockPos(player.blockPosition());
                buf.writeByte(0); // Always Main Hand
                buf.writeInt(albumId);
            });
        }
    }

    /**
     * Opens the conversation menu for {@code conversationId}. Unlike the old code, this does NOT embed the
     * conversation's message history in the menu-open buffer (that used to be done via a
     * writeMessagesToBuffer step reading the full stored history for the conversation). Message history is
     * unbounded in principle, so baking it into the menu buffer risked the same kind of unbounded-payload
     * problem the ConversationSavedData split was built to avoid. Instead, the conversation screen requests
     * its own first page after opening via fr.lordfinn.crazyphone.network.ConversationRequestPacket.
     */
    public static void openPhoneConversationMenu(Player player, InteractionHand hand, String conversationId) {
            if (player instanceof ServerPlayer serverPlayer) {

                String playerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);

                PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(player.level());
                CompoundTag phonesTag = registry.phones;
                Tag phoneTag = phonesTag.get(playerNumber);
                if (phoneTag instanceof CompoundTag phoneCompoundTag) {
                    Tag tag = phoneCompoundTag.get("notifications");
                    ListTag notifications = (tag instanceof ListTag list) ? list : new ListTag();

                    ListTag updatedNotifications = new ListTag();
                    for (Tag t : notifications) {
                        if (t instanceof StringTag stringTag) {
                            if (!stringTag.getAsString().equals(conversationId)) {
                                updatedNotifications.add(stringTag);
                            }
                        }
                    }

                    if (updatedNotifications.size() != notifications.size()) {
                        phoneCompoundTag.put("notifications", updatedNotifications);
                        phonesTag.put(playerNumber, phoneCompoundTag);
                        // Only this player's own notification badge changed - no one else needs the update.
                        registry.syncTo(serverPlayer);
                    }
                }

                RegistryAccess registryAccess = player.registryAccess();
                ConnectionType connectionType = ConnectionType.NEOFORGE;
                player.openMenu(new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("item.crazyphone.crazy_phone");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                        RegistryFriendlyByteBuf packetBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess, connectionType);
                        populateBufferWithConversationData(packetBuffer, player, hand, conversationId);

                        try {
                            return new CrazyPhoneConversationMenu(id, inventory, packetBuffer);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to create menu instance", e);
                        }
                    }
                }, buf -> populateBufferWithConversationData(buf, player, hand, conversationId));
            }
    }

    protected static void populateBufferWithConversationData(RegistryFriendlyByteBuf packetBuffer, Player player,
            InteractionHand hand, String conversationId) {

        writePlayerAndConversationInfo(packetBuffer, player, conversationId);
        writeParticipantsToBuffer(packetBuffer, player, conversationId);
    }

    private static void writePlayerAndConversationInfo(RegistryFriendlyByteBuf buffer, Player player, String conversationId) {
        buffer.writeBlockPos(player.blockPosition());
        buffer.writeByte(0); // Always Main Hand
        buffer.writeUtf(conversationId);
    }

    private static void writeParticipantsToBuffer(RegistryFriendlyByteBuf buffer, Player player, String conversationId) {
        List<String> participantNumbers = CrazyPhoneHelper.getNumbersFromConversationId(conversationId);
        List<Contact> participants = new ArrayList<>();

        for (String number : participantNumbers) {
            Contact contact = CrazyPhoneHelper.getContact(player.level(), number);
            if (contact != null) {
                participants.add(contact);
            }
        }

        buffer.writeInt(participants.size());
        for (Contact contact : participants) {
            buffer.writeUtf(contact.name);
            buffer.writeUtf(contact.number);
            buffer.writeUtf(contact.uuid);
            buffer.writeUtf(contact.skin);
        }
    }
}
