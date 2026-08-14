package fr.lordfinn.crazyphone.utils;

import fr.lordfinn.crazyphone.init.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the item-NBT-driven screen-open/call-state tags added so bystanders (not just the phone's own
 * owner) see a correct texture via vanilla's normal equipment sync - see CrazyPhoneItemProperties/
 * CrazyPhoneDefaultScreenMenu/CallRegistry for where these are actually wired into gameplay.
 */
class CrazyPhoneHelperCallStateTest {

    private static ItemStack crazyPhone(String number) {
        ItemStack stack = new ItemStack(ModItems.CRAZY_PHONE.get());
        PhoneTagAccess.updateTag(stack, tag -> tag.putString("number", number));
        return stack;
    }

    private static ServerPlayer playerWithInventory(ItemStack... items) {
        ServerPlayer player = mock(ServerPlayer.class);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getContainerSize()).thenReturn(items.length);
        for (int i = 0; i < items.length; i++) {
            int index = i;
            when(inventory.getItem(index)).thenReturn(items[index]);
        }
        when(player.getInventory()).thenReturn(inventory);
        // inventoryMenu is a real final field, not a method, so Mockito never initializes it and
        // it can't be assigned directly: reflection is the only way to give it a non-null value,
        // or CrazyPhoneHelper's broadcastChanges() call NPEs against the mock.
        try {
            Field field = net.minecraft.world.entity.player.Player.class.getField("inventoryMenu");
            field.setAccessible(true);
            field.set(player, mock(InventoryMenu.class));
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return player;
    }

    // --- screen open ---

    @Test
    void isPhoneScreenOpen_defaultsToFalse() {
        assertFalse(CrazyPhoneHelper.isPhoneScreenOpen(crazyPhone("555")));
    }

    @Test
    void setPhoneScreenOpen_true_thenFalse_roundTrips() {
        ItemStack phone = crazyPhone("555");
        CrazyPhoneHelper.setPhoneScreenOpen(phone, true);
        assertTrue(CrazyPhoneHelper.isPhoneScreenOpen(phone));

        CrazyPhoneHelper.setPhoneScreenOpen(phone, false);
        assertFalse(CrazyPhoneHelper.isPhoneScreenOpen(phone));
    }

    // --- call state ---

    @Test
    void getPhoneCallState_defaultsToEmptyString() {
        assertEquals("", CrazyPhoneHelper.getPhoneCallState(crazyPhone("555")));
    }

    @Test
    void setCallStateForMatchingPhones_onlyTagsPhonesWhoseNumberIsInTheCall() {
        ItemStack onCall = crazyPhone("555");
        ItemStack notOnCall = crazyPhone("666"); // a second phone the same player happens to carry
        ServerPlayer player = playerWithInventory(onCall, notOnCall);

        CrazyPhoneHelper.setCallStateForMatchingPhones(player, List.of("555", "777"), "ACTIVE");

        assertEquals("ACTIVE", CrazyPhoneHelper.getPhoneCallState(onCall));
        assertEquals("", CrazyPhoneHelper.getPhoneCallState(notOnCall),
                "a phone the player merely happens to be carrying, not part of THIS call, must stay untagged");
    }

    @Test
    void setCallStateForMatchingPhones_tagsEveryMatchingPhoneNotJustTheFirst() {
        // A player could carry duplicates or (more realistically) this exercises the loop's "not a break
        // after first match" behavior directly.
        ItemStack phoneA = crazyPhone("555");
        ItemStack phoneB = crazyPhone("777");
        ServerPlayer player = playerWithInventory(phoneA, phoneB);

        CrazyPhoneHelper.setCallStateForMatchingPhones(player, List.of("555", "777"), "RINGING");

        assertEquals("RINGING", CrazyPhoneHelper.getPhoneCallState(phoneA));
        assertEquals("RINGING", CrazyPhoneHelper.getPhoneCallState(phoneB));
    }

    @Test
    void setCallStateForMatchingPhones_ignoresNonCrazyPhoneItems() {
        ItemStack stick = new ItemStack(Items.STICK);
        ServerPlayer player = playerWithInventory(stick);

        assertDoesNotThrow(() -> CrazyPhoneHelper.setCallStateForMatchingPhones(player, List.of("555"), "ACTIVE"));
    }

    @Test
    void clearCallStateForAllPhones_clearsEveryCrazyPhoneRegardlessOfNumber() {
        ItemStack phoneA = crazyPhone("555");
        ItemStack phoneB = crazyPhone("666");
        CrazyPhoneHelper.setCallStateForMatchingPhones(mockPlayerFor(phoneA), List.of("555"), "ACTIVE");
        CrazyPhoneHelper.setCallStateForMatchingPhones(mockPlayerFor(phoneB), List.of("666"), "CALLING");
        ServerPlayer player = playerWithInventory(phoneA, phoneB);

        CrazyPhoneHelper.clearCallStateForAllPhones(player);

        assertEquals("", CrazyPhoneHelper.getPhoneCallState(phoneA));
        assertEquals("", CrazyPhoneHelper.getPhoneCallState(phoneB));
    }

    @Test
    void clearCallStateForAllPhones_leavesNonCrazyPhoneItemsAlone() {
        ItemStack stick = new ItemStack(Items.STICK);
        ServerPlayer player = playerWithInventory(stick);
        assertDoesNotThrow(() -> CrazyPhoneHelper.clearCallStateForAllPhones(player));
    }

    private static ServerPlayer mockPlayerFor(ItemStack stack) {
        return playerWithInventory(stack);
    }
}
