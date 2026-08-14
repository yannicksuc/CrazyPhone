package fr.lordfinn.crazyphone.utils;

import fr.lordfinn.crazyphone.Crazyphone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.netty.buffer.Unpooled;
import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.data.PlayerPhoneState;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneGetContactsProcedure;
import fr.lordfinn.crazyphone.procedures.CrazyPhoneGetGroupsProcedure;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneContactsScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneConversationMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneGroupSettingsScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneCallingScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneIncomingCallScreenMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneInCallScreenMenu;
import fr.lordfinn.crazyphone.voicechat.CallRegistry;
import java.util.UUID;
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
//? if >=1.20.5 {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
*///? }
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
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
            case "crazyphone:crazy_phone_group_settings_screen" ->
                openGroupSettingsMenu(player, hand, screenData);
            case "crazyphone:crazy_phone_mayors_candidates_list" ->
                openPhoneCustomMenu(player, hand, CrazyPhoneMayorsCandidatesListMenu.class);
            case "crazyphone:crazy_phone_calling_screen", "crazyphone:crazy_phone_in_call_screen",
                    "crazyphone:crazy_phone_incoming_call_screen" -> {
                if (player instanceof ServerPlayer serverPlayer)
                    openCallScreenForPlayer(serverPlayer);
            }
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
            Level world = player.level();
            ListTag allContactsTag = CrazyPhoneGetContactsProcedure.execute(world, ownerNumber);
            ListTag groupsTag = CrazyPhoneGetGroupsProcedure.execute(world, ownerNumber);
            Set<String> favoriteNumbers = CrazyPhoneHelper.getFavoriteNumbers(world, ownerNumber);

            List<CompoundTag> favorites = new ArrayList<>();
            List<CompoundTag> contacts = new ArrayList<>();
            for (Tag t : allContactsTag) {
                if (!(t instanceof CompoundTag compound))
                    continue;
                (favoriteNumbers.contains(NbtCompat.getString(compound, "number")) ? favorites : contacts).add(compound);
            }
            List<CompoundTag> groups = new ArrayList<>();
            for (Tag t : groupsTag) {
                if (t instanceof CompoundTag compound)
                    groups.add(compound);
            }

            // Most recently active conversation first, within each section - contacts/groups that have
            // never been messaged sort to the bottom (timecode 0), favorites always stay their own section
            // above the rest regardless of recency.
            sortByRecency(favorites, world, ownerNumber, false);
            sortByRecency(contacts, world, ownerNumber, false);
            sortByRecency(groups, world, ownerNumber, true);

            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crazyphone.crazy_phone");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    populateBufferWithMenuData(packetBuffer, player, hand, favorites, contacts, groups);

                    try {
                        return new CrazyPhoneContactsScreenMenu(id, inventory, packetBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create menu instance", e);
                    }
                }
            }, buf -> populateBufferWithMenuData(buf, player, hand, favorites, contacts, groups));
        }
    }

    private static void sortByRecency(List<CompoundTag> entries, Level world, String ownerNumber, boolean isGroup) {
        entries.sort((a, b) -> {
            String idA = isGroup ? NbtCompat.getString(a, "conversationId") : CrazyPhoneHelper.getConversationNumber(NbtCompat.getString(a, "number"), ownerNumber);
            String idB = isGroup ? NbtCompat.getString(b, "conversationId") : CrazyPhoneHelper.getConversationNumber(NbtCompat.getString(b, "number"), ownerNumber);
            return Integer.compare(CrazyPhoneHelper.getLastMessageTimecode(world, idB), CrazyPhoneHelper.getLastMessageTimecode(world, idA));
        });
    }

    private static void populateBufferWithMenuData(FriendlyByteBuf buf, Player player, InteractionHand hand,
            List<CompoundTag> favorites, List<CompoundTag> contacts, List<CompoundTag> groups) {
        buf.writeBlockPos(player.blockPosition());
        buf.writeByte(0); // Always Main Hand
        serializeContactsToBuffer(buf, favorites);
        serializeContactsToBuffer(buf, contacts);
        serializeGroupsToBuffer(buf, groups);
    }

    private static void serializeContactsToBuffer(FriendlyByteBuf buf, List<CompoundTag> contacts) {
        buf.writeInt(contacts.size());
        for (CompoundTag contact : contacts) {
            serializeContactDetails(buf, contact);
        }
    }

    private static void serializeContactDetails(FriendlyByteBuf buf, CompoundTag contact) {
        String number = NbtCompat.getString(contact, "number");
        String name = NbtCompat.getString(contact, "name");
        String uuid = NbtCompat.getString(contact, "uuid");
        String skin = NbtCompat.getString(contact, "skin");

        buf.writeUtf(number);
        buf.writeUtf(name);
        buf.writeUtf(uuid);
        buf.writeUtf(skin);
    }

    private static void serializeGroupsToBuffer(FriendlyByteBuf buf, List<CompoundTag> groups) {
        buf.writeInt(groups.size());
        for (CompoundTag groupCompound : groups) {
            buf.writeUtf(NbtCompat.getString(groupCompound, "conversationId"));
            buf.writeUtf(NbtCompat.getString(groupCompound, "name"));
            buf.writeNbt(NbtCompat.getCompound(groupCompound, "icon"));
            buf.writeUtf(NbtCompat.getString(groupCompound, "admin"));
            ListTag members = NbtCompat.getList(groupCompound, "members");
            buf.writeInt(members.size());
            for (Tag member : members) {
                if (member instanceof CompoundTag memberCompound) {
                    serializeContactDetails(buf, memberCompound);
                }
            }
        }
    }

    /** Opens the group settings screen for {@code conversationId}: current name/icon/admin plus every
     * current member (including the viewer, so the screen can tell who they are relative to the admin). */
    public static void openGroupSettingsMenu(Player player, InteractionHand hand, String conversationId) {
        if (player instanceof ServerPlayer) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crazyphone.crazy_phone");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    populateBufferWithGroupSettingsData(packetBuffer, player, conversationId);

                    try {
                        return new CrazyPhoneGroupSettingsScreenMenu(id, inventory, packetBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create menu instance", e);
                    }
                }
            }, buf -> populateBufferWithGroupSettingsData(buf, player, conversationId));
        }
    }

    private static void populateBufferWithGroupSettingsData(FriendlyByteBuf buf, Player player, String conversationId) {
        buf.writeBlockPos(player.blockPosition());
        buf.writeByte(0); // Always Main Hand
        CrazyPhoneHelper.GroupMeta meta = CrazyPhoneHelper.getGroupMeta(player.level(), conversationId);
        buf.writeUtf(conversationId);
        buf.writeUtf(meta.name());
        buf.writeNbt(CrazyPhoneHelper.encodeItemStack(player.level(), meta.icon()));
        buf.writeUtf(meta.admin());
        List<Contact> members = CrazyPhoneHelper.getGroupMemberContacts(player.level(), conversationId);
        buf.writeInt(members.size());
        for (Contact contact : members) {
            buf.writeUtf(contact.getNumber());
            buf.writeUtf(contact.getName());
            buf.writeUtf(contact.getUuid() == null ? "" : contact.getUuid());
            buf.writeUtf(contact.getSkin() == null ? "" : contact.getSkin());
        }

        // Viewer's own contacts who AREN'T already in the group - shown in the settings screen as
        // "invite to group" rows, so members can be added without leaving the screen.
        String viewerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(player, null);
        ListTag viewerContacts = CrazyPhoneGetContactsProcedure.execute(player.level(), viewerNumber);
        List<Contact> invitable = new ArrayList<>();
        for (Tag contactTag : viewerContacts) {
            if (!(contactTag instanceof CompoundTag compoundTag))
                continue;
            String number = NbtCompat.getString(compoundTag, "number");
            if (members.stream().anyMatch(m -> m.getNumber().equals(number)))
                continue;
            invitable.add(new Contact(number, NbtCompat.getString(compoundTag, "name"),
                    NbtCompat.getString(compoundTag, "skin"),
                    NbtCompat.getString(compoundTag, "uuid")));
        }
        buf.writeInt(invitable.size());
        for (Contact contact : invitable) {
            buf.writeUtf(contact.getNumber());
            buf.writeUtf(contact.getName());
            buf.writeUtf(contact.getUuid() == null ? "" : contact.getUuid());
            buf.writeUtf(contact.getSkin() == null ? "" : contact.getSkin());
        }
    }

    public static void openPhoneAlbumMenu(Player player, InteractionHand hand, int albumId) {
        if (player instanceof ServerPlayer serverPlayer) {

            SoundEvent sound = RegistryCompat.get(BuiltInRegistries.SOUND_EVENT, Crazyphone.parseId("minecraft:item.book.page_turn"));
            if (sound != null) {
                serverPlayer.playNotifySound(sound, SoundSource.PLAYERS, 0.7f, 1.2f);
            }

            // Resolved fresh from the server's own copy of the held phone right now, then transmitted whole
            // (see CrazyPhonePicturesScreenMenu) - the client's own local copy of that item can still be a
            // tick or two behind a photo that was just taken, which used to make it invisible until the
            // album was reopened a second time.
            IItemHandlerModifiable handler = CrazyPhoneHelper.getPhoneItemHandler(player);
            ItemStack album = CrazyPhoneHelper.getAlbumFromPhoneHandler(handler, albumId);
            CompoundTag albumTag = CrazyPhoneHelper.encodeItemStack(player.level(), album);

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
                    packetBuffer.writeNbt(albumTag);

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
                buf.writeNbt(albumTag);
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
                            if (!NbtCompat.asString(stringTag).equals(conversationId)) {
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

                //? if >=1.20.5 {
                /*RegistryAccess registryAccess = player.registryAccess();
                ConnectionType connectionType = ConnectionType.NEOFORGE;
                *///? }
                player.openMenu(new MenuProvider() {
                    @Override
                    public Component getDisplayName() {
                        return Component.translatable("item.crazyphone.crazy_phone");
                    }

                    @Override
                    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                        //? if >=1.20.5 {
                        /*RegistryFriendlyByteBuf packetBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registryAccess, connectionType);
                        *///? } else {
                        FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                        //?}
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

    //? if >=1.20.5 {
    /*protected static void populateBufferWithConversationData(RegistryFriendlyByteBuf packetBuffer, Player player,
    *///? } else {
    protected static void populateBufferWithConversationData(FriendlyByteBuf packetBuffer, Player player,
    //?}
            InteractionHand hand, String conversationId) {

        writePlayerAndConversationInfo(packetBuffer, player, conversationId);
        writeParticipantsToBuffer(packetBuffer, player, conversationId);
        writeGroupMetaToBuffer(packetBuffer, player, conversationId);
    }

    /** Whether this conversation IS a group (was ever created as one via createGroup) plus its custom
     * name/icon/admin, so the conversation screen can show the group's display name/icon in its header
     * and the settings cog icon only where it applies. Deliberately NOT based on the current live member
     * count: a group that's been excluded down to 2 (or even 1) people is still that same group - its
     * conversationId is a completely different id from the real 1:1 conversation between whichever people
     * happen to remain, and it must keep its identity (name, icon, settings access) rather than silently
     * degrading into looking like an ordinary 1:1 chat. Empty name/icon means "unset" - the client falls
     * back to member names / cycling heads, same convention as the contacts screen's group entries. */
    //? if >=1.20.5 {
    /*private static void writeGroupMetaToBuffer(RegistryFriendlyByteBuf buffer, Player player, String conversationId) {
    *///? } else {
    private static void writeGroupMetaToBuffer(FriendlyByteBuf buffer, Player player, String conversationId) {
    //?}
        boolean isGroup = CrazyPhoneHelper.hasGroupMeta(player.level(), conversationId);
        CrazyPhoneHelper.GroupMeta meta = CrazyPhoneHelper.getGroupMeta(player.level(), conversationId);
        buffer.writeBoolean(isGroup);
        buffer.writeUtf(meta.name());
        buffer.writeNbt(CrazyPhoneHelper.encodeItemStack(player.level(), meta.icon()));
        buffer.writeUtf(meta.admin());
    }

    //? if >=1.20.5 {
    /*private static void writePlayerAndConversationInfo(RegistryFriendlyByteBuf buffer, Player player, String conversationId) {
    *///? } else {
    private static void writePlayerAndConversationInfo(FriendlyByteBuf buffer, Player player, String conversationId) {
    //?}
        buffer.writeBlockPos(player.blockPosition());
        buffer.writeByte(0); // Always Main Hand
        buffer.writeUtf(conversationId);
    }

    //? if >=1.20.5 {
    /*private static void writeParticipantsToBuffer(RegistryFriendlyByteBuf buffer, Player player, String conversationId) {
    *///? } else {
    private static void writeParticipantsToBuffer(FriendlyByteBuf buffer, Player player, String conversationId) {
    //?}
        List<String> participantNumbers = CrazyPhoneHelper.getGroupMembers(player.level(), conversationId);
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

    /**
     * Opens whichever call screen matches this player's current state in {@link CallRegistry}: the
     * Incoming Call screen if they're themselves still ringing (haven't answered/declined yet), the Calling
     * screen if they're the initiator waiting for anyone to pick up, the InCall screen otherwise. No-op if
     * they aren't in a call at all. This is the single entry point for every "show me the call screen"
     * trigger: the lock-bypass hook in CrazyPhoneOnUseProcedure, the conversation screen's call icon
     * reopening an active call, the Calling screen asking to be swapped for the InCall screen once answered,
     * and the Incoming Call screen's own Accept button.
     */
    public static void openCallScreenForPlayer(ServerPlayer player) {
        CallRegistry.CallSession session = CallRegistry.getSessionFor(player.getUUID()).orElse(null);
        if (session == null)
            return;
        String displayTitle = buildCallDisplayTitle(player, session);
        if (session.ringing.contains(player.getUUID())) {
            openIncomingCallMenu(player, session.conversationId, session.callId, displayTitle, session.participants);
            return;
        }
        // The initiator, still waiting for anyone at all to pick up. The InCall screen (participant list,
        // working hangup-of-an-active-call UI) would be misleading here since there's no live audio yet.
        boolean stillWaitingForAnyAnswer = player.getUUID().equals(session.initiator) && session.participants.size() == 1;
        if (stillWaitingForAnyAnswer) {
            // Unlike every other case, the callee(s) being shown on THIS screen aren't in session.participants
            // yet (they're only added once they actually answer) - they're still in session.ringing. Without
            // this the Calling screen would always compute an empty "others" list (see populateCallScreenBuffer's
            // viewer-exclusion filter) since the only entry in participants at this point is the viewer
            // themselves.
            Set<UUID> callerAndRinging = new HashSet<>(session.participants);
            callerAndRinging.addAll(session.ringing);
            openCallingMenu(player, session.conversationId, session.callId, displayTitle, callerAndRinging);
        } else {
            openInCallMenu(player, session.conversationId, session.callId, displayTitle, session.participants);
        }
    }

    private static String buildCallDisplayTitle(Player viewer, CallRegistry.CallSession session) {
        String viewerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(viewer, null);
        List<String> otherNumbers = CrazyPhoneHelper.getGroupMembers(viewer.level(), session.conversationId).stream()
                .filter(number -> !number.equals(viewerNumber))
                .toList();
        List<String> names = new ArrayList<>();
        for (String number : otherNumbers) {
            Contact contact = CrazyPhoneHelper.getContact(viewer.level(), number);
            if (contact != null)
                names.add(contact.getName());
        }
        return String.join(", ", names);
    }

    private static void openCallingMenu(Player player, String conversationId, UUID callId, String displayTitle, Set<UUID> participantIds) {
        if (player instanceof ServerPlayer) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crazyphone.crazy_phone");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    populateCallScreenBuffer(packetBuffer, player, conversationId, callId, displayTitle, participantIds);
                    try {
                        return new CrazyPhoneCallingScreenMenu(id, inventory, packetBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create menu instance", e);
                    }
                }
            }, buf -> populateCallScreenBuffer(buf, player, conversationId, callId, displayTitle, participantIds));
        }
    }

    private static void openIncomingCallMenu(Player player, String conversationId, UUID callId, String displayTitle, Set<UUID> participantIds) {
        if (player instanceof ServerPlayer) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crazyphone.crazy_phone");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    populateCallScreenBuffer(packetBuffer, player, conversationId, callId, displayTitle, participantIds);
                    try {
                        return new CrazyPhoneIncomingCallScreenMenu(id, inventory, packetBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create menu instance", e);
                    }
                }
            }, buf -> populateCallScreenBuffer(buf, player, conversationId, callId, displayTitle, participantIds));
        }
    }

    private static void openInCallMenu(Player player, String conversationId, UUID callId, String displayTitle, Set<UUID> participantIds) {
        if (player instanceof ServerPlayer) {
            player.openMenu(new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crazyphone.crazy_phone");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                    FriendlyByteBuf packetBuffer = new FriendlyByteBuf(Unpooled.buffer());
                    populateCallScreenBuffer(packetBuffer, player, conversationId, callId, displayTitle, participantIds);
                    try {
                        return new CrazyPhoneInCallScreenMenu(id, inventory, packetBuffer);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to create menu instance", e);
                    }
                }
            }, buf -> populateCallScreenBuffer(buf, player, conversationId, callId, displayTitle, participantIds));
        }
    }

    private static final EquipmentSlot[] CALL_PREVIEW_ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** {@code participantIds} is written as (uuid, name, armor snapshot) tuples excluding the viewer
     * themselves - only the call screens' bust previews actually read them (see CallBustPreview), but
     * writing them here too (not just in the live CrazyPhoneCallStateSyncPacket resyncs) means the preview
     * is already populated the instant the screen first opens, not one packet round-trip later. The armor
     * (helmet/chestplate/leggings/boots, in that order) is a one-time snapshot of what the participant is
     * actually wearing right now - CallBustPreview never re-syncs it afterward, so mid-call gear changes
     * won't retroactively show up on an already-open screen. */
    private static void populateCallScreenBuffer(FriendlyByteBuf buf, Player player, String conversationId, UUID callId, String displayTitle, Set<UUID> participantIds) {
        buf.writeBlockPos(player.blockPosition());
        buf.writeByte(0); // Always Main Hand
        buf.writeUtf(conversationId);
        buf.writeUUID(callId);
        buf.writeUtf(displayTitle);
        List<UUID> others = participantIds.stream().filter(id -> !id.equals(player.getUUID())).toList();
        buf.writeVarInt(others.size());
        MinecraftServer server = player.level().getServer();
        for (UUID id : others) {
            buf.writeUUID(id);
            ServerPlayer other = server != null ? server.getPlayerList().getPlayer(id) : null;
            buf.writeUtf(other != null ? GameProfileCompat.name(other.getGameProfile()) : "");
            for (EquipmentSlot slot : CALL_PREVIEW_ARMOR_SLOTS) {
                ItemStack armor = other != null ? other.getItemBySlot(slot) : ItemStack.EMPTY;
                buf.writeNbt(CrazyPhoneHelper.encodeItemStack(player.level(), armor));
            }
        }
    }
}
