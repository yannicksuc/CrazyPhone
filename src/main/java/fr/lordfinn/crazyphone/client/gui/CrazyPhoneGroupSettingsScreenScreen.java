package fr.lordfinn.crazyphone.client.gui;

import fr.lordfinn.crazyphone.utils.GuiCompat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import fr.lordfinn.crazyphone.utils.NetworkAccess;

import fr.lordfinn.crazyphone.client.CursorEffects;
import fr.lordfinn.crazyphone.network.CrazyPhoneGroupSettingsButtonMessage;
import fr.lordfinn.crazyphone.procedures.GetCrazyPhoneNumberFromMainHandProcedure;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneConversationMenu;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneGroupSettingsScreenMenu;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rename the group, pick an icon (click an item in the player inventory shown alongside to pick up a copy
 * onto the cursor - the real stack never leaves the inventory, no matter which mouse button is used - then
 * click the icon preview slot to place it there, or click anywhere else to cancel), and exclude members
 * (admin can exclude anyone, everyone else only themselves - "leave"). Nothing is applied server-side
 * until Validate; Cancel just closes without sending anything.
 *
 * Stays on the same phone-frame chrome as every other phone screen (header banner, back/home/lock row) -
 * only the overall window is widened so the player's real inventory can sit as an extra panel to the
 * right of the phone, rather than breaking from the phone's look entirely.
 */
public class CrazyPhoneGroupSettingsScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneGroupSettingsScreenMenu> {
    private static final int PHONE_WIDTH = 122;
    private static final int INV_GAP = 8;
    private static final int INV_PANEL_WIDTH = 9 * 18;
    private static final int INV_PANEL_HEIGHT = 3 * 18 + 4 + 18;
    private static final int RIGHT_MARGIN = 8;

    private static final int CONTENT_X = 8;
    /** The dark panel backgrounds (member list, icon preview) are drawn 1px larger than CONTENT_X on each
     * side (see the -1/+1 fill calls) - widgets use this wider span too so their edges actually line up
     * with those panels instead of looking 1px narrower. */
    private static final int PANEL_EDGE_INSET = 1;
    private static final int NAME_FIELD_Y = 30;
    private static final int ICON_ROW_Y = 48;
    /** 3px more than the icon preview row's own bottom edge (ICON_ROW_Y+16=64), so the list doesn't sit
     * right up against it. */
    private static final int MEMBER_LIST_Y = 69;
    private static final int MEMBER_LIST_WIDTH = PHONE_WIDTH - CONTENT_X * 2;
    /** Bottom edge stays fixed (was MEMBER_LIST_Y(66)+88=154) - shortened by the same 3px the top grew by. */
    private static final int MEMBER_LIST_HEIGHT = 85;
    private static final int MEMBER_ROW_HEIGHT = 18;
    private static final int TOGGLE_SIZE = 12;
    private static final int SCROLL_STEP = 10;

    private final static HashMap<String, Object> guistate = CrazyPhoneGroupSettingsScreenMenu.guistate;
    private final ItemStack headerIcon = CrazyPhoneConversationMenu.createGroupSettingsIcon();
    private final String viewerNumber;
    private final boolean viewerIsAdmin;
    private final Set<String> stagedExcluded = new HashSet<>();
    private final Set<String> stagedAdded = new HashSet<>();
    private ItemStack stagedIcon;
    private EditBox nameField;
    private int scrollPosition = 0;
    /** A copy of an inventory item currently riding the cursor, picked up via right-click and waiting to
     * be placed on the icon slot (or cancelled by clicking elsewhere) - purely a client-side visual, the
     * real stack it was copied from is never touched. */
    private ItemStack cursorCarriedIcon = ItemStack.EMPTY;

    public CrazyPhoneGroupSettingsScreenScreen(CrazyPhoneGroupSettingsScreenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PHONE_WIDTH + INV_GAP + INV_PANEL_WIDTH + RIGHT_MARGIN;
        this.viewerNumber = GetCrazyPhoneNumberFromMainHandProcedure.execute(this.entity, null);
        this.viewerIsAdmin = !viewerNumber.isEmpty() && viewerNumber.equals(menu.getGroupAdmin());
        this.stagedIcon = menu.getGroupIcon().copy();
    }

    @Override
    public HashMap<String, Object> getWidgets() {
        return guistate;
    }

    @Override
    public void init() {
        super.init();

        nameField = new EditBox(this.font, this.leftPos + CONTENT_X - PANEL_EDGE_INSET, this.topPos + NAME_FIELD_Y,
                MEMBER_LIST_WIDTH + PANEL_EDGE_INSET * 2, 14,
                Component.translatable("gui.crazyphone.crazy_phone_group_settings.label_group_name"));
        nameField.setMaxLength(64);
        nameField.setValue(menu.getGroupName());
        nameField.setHint(Component.translatable("gui.crazyphone.crazy_phone_group_settings.hint_group_name"));
        guistate.put("text:group_name", nameField);
        this.addRenderableWidget(nameField);
        this.setInitialFocus(nameField);

        Button cancelButton = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_group_settings.button_cancel"), e -> onCancel())
                .bounds(this.leftPos + CONTENT_X - PANEL_EDGE_INSET, this.topPos + 158, 52 + PANEL_EDGE_INSET, 14).build();
        guistate.put("button:group_settings_cancel", cancelButton);
        this.addRenderableWidget(cancelButton);

        Button validateButton = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_group_settings.button_validate"), e -> onValidate())
                .bounds(this.leftPos + 62, this.topPos + 158, 52 + PANEL_EDGE_INSET, 14).build();
        guistate.put("button:group_settings_validate", validateButton);
        this.addRenderableWidget(validateButton);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int mouseX, int mouseY) {
        super.renderBg(guiGraphics, partialTicks, mouseX, mouseY);

        int invX = this.leftPos + CrazyPhoneGroupSettingsScreenMenu.PLAYER_INV_X;
        int invY = this.topPos + CrazyPhoneGroupSettingsScreenMenu.PLAYER_INV_Y;
        guiGraphics.fill(invX - 4, invY - 4, invX + INV_PANEL_WIDTH + 4, invY + INV_PANEL_HEIGHT + 4, 0x60000000);
        for (Slot slot : this.menu.slots) {
            int x = this.leftPos + slot.x - 1;
            int y = this.topPos + slot.y - 1;
            guiGraphics.fill(x, y, x + 18, y + 18, 0x40FFFFFF);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, headerIcon, Component.translatable("gui.crazyphone.crazy_phone_group_settings.title"));
        renderIconPreview(guiGraphics, mouseX, mouseY);
        Component memberTooltip = renderMemberList(guiGraphics, mouseX, mouseY);
        // Every tooltip below is deliberately rendered LAST, after everything else this frame draws -
        // rendering one earlier (e.g. inline inside the member-list loop, which still had its scissor
        // active) either got clipped by that scissor or painted over by later content, which is exactly
        // what made these look like an empty/cropped box instead of a proper tooltip.
        if (cursorCarriedIcon.isEmpty() && isHoveringIconPreview(mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(this.font,
                    List.of(Component.translatable("gui.crazyphone.crazy_phone_group_settings.tooltip_icon_hint")), mouseX, mouseY);
        }
        if (memberTooltip != null) {
            guiGraphics.renderComponentTooltip(this.font, List.of(memberTooltip), mouseX, mouseY);
        }
        // Drawn last of all so it always floats on top of everything else, following the cursor exactly
        // like vanilla's own carried-item rendering. Item icons are depth-tested 3D models, not flat 2D
        // quads, so draw ORDER alone doesn't guarantee this renders in front - a slot item rendered
        // earlier can still win the depth test and show through. Pushing the same Z translation (232)
        // vanilla itself uses for its own cursor-carried-item rendering (AbstractContainerScreen) is what
        // actually guarantees it wins.
        if (!cursorCarriedIcon.isEmpty()) {
            GuiCompat.pushPose(guiGraphics);
            GuiCompat.translate(guiGraphics, 0, 0);
            guiGraphics.renderItem(cursorCarriedIcon, mouseX - 8, mouseY - 8);
            GuiCompat.popPose(guiGraphics);
        }
    }

    private static final long ICON_HEAD_CYCLE_INTERVAL_MS = 1000;

    /** The custom icon if one is staged, otherwise a member head that cycles over time - same fallback
     * convention as the contacts screen's group entries, so an unset icon still reads as "these people"
     * instead of just sitting empty. */
    private ItemStack resolvePreviewIcon() {
        if (stagedIcon != null && !stagedIcon.isEmpty())
            return stagedIcon;
        List<Contact> members = menu.getMembers();
        if (members.isEmpty())
            return ItemStack.EMPTY;
        int memberIndex = (int) ((System.currentTimeMillis() / ICON_HEAD_CYCLE_INTERVAL_MS) % members.size());
        return CrazyPhoneHelper.createContactHead(members.get(memberIndex));
    }

    private boolean isHoveringIconPreview(int mouseX, int mouseY) {
        int x = this.leftPos + CONTENT_X;
        int y = this.topPos + ICON_ROW_Y;
        return mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
    }

    private void renderIconPreview(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = this.leftPos + CONTENT_X;
        int y = this.topPos + ICON_ROW_Y;
        guiGraphics.fill(x - 1, y - 1, x + 17, y + 17, 0x80000000);
        ItemStack icon = resolvePreviewIcon();
        if (!icon.isEmpty())
            guiGraphics.renderItem(icon, x, y);
    }

    /** Returns the toggle tooltip to show for whichever row is hovered, or null - rendering is deferred
     * to the caller so it happens after this scissor is disabled (a tooltip rendered while still inside
     * it would get clipped to this small viewport instead of drawing at full size over everything else).
     * Members render first (with an exclude/leave toggle), then the viewer's other contacts who aren't
     * in the group yet (with an invite toggle instead) - one continuous scrollable list. */
    private Component renderMemberList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = this.leftPos + CONTENT_X;
        int y = this.topPos + MEMBER_LIST_Y;
        guiGraphics.fill(x - 1, y - 1, x + MEMBER_LIST_WIDTH + 1, y + MEMBER_LIST_HEIGHT + 1, 0xA0000000);
        guiGraphics.enableScissor(x, y, x + MEMBER_LIST_WIDTH, y + MEMBER_LIST_HEIGHT);

        int rowY = y - scrollPosition;
        Component hoveredTooltip = null;
        for (Contact member : menu.getMembers()) {
            Component tooltip = renderMemberRow(guiGraphics, mouseX, mouseY, x, y, rowY, member);
            if (tooltip != null)
                hoveredTooltip = tooltip;
            rowY += MEMBER_ROW_HEIGHT;
        }
        for (Contact contact : menu.getInvitableContacts()) {
            Component tooltip = renderInvitableRow(guiGraphics, mouseX, mouseY, x, y, rowY, contact);
            if (tooltip != null)
                hoveredTooltip = tooltip;
            rowY += MEMBER_ROW_HEIGHT;
        }
        guiGraphics.disableScissor();
        return hoveredTooltip;
    }

    /** Head icon, name (and admin marker), all vertically centered on the row's own midpoint so they line
     * up with each other and with the toggle box a caller draws afterward. */
    private void drawPersonRow(GuiGraphics guiGraphics, int x, int rowY, Contact person, int textColor) {
        guiGraphics.renderItem(CrazyPhoneHelper.createContactHead(person), x, rowY + (MEMBER_ROW_HEIGHT - 16) / 2);

        int maxTextWidth = MEMBER_LIST_WIDTH - 16 - 2 - TOGGLE_SIZE - 2;
        String rawName = person.getNumber().equals(menu.getGroupAdmin()) ? person.getName() + " *" : person.getName();
        String nameLine = this.font.plainSubstrByWidth(rawName, maxTextWidth);
        guiGraphics.drawString(this.font, nameLine, x + 18, rowY + 5, textColor, false);
    }

    private boolean isToggleHovered(int mouseX, int mouseY, int toggleX, int toggleY, int listY) {
        return mouseX >= toggleX && mouseX < toggleX + TOGGLE_SIZE
                && mouseY >= toggleY && mouseY < toggleY + TOGGLE_SIZE
                && mouseY >= listY && mouseY < listY + MEMBER_LIST_HEIGHT;
    }

    /** Flat single-color squares read as boring - a 1px bevel (lighter top+left, darker bottom+right)
     * gives them the classic pixel-art "raised button" look, at the same outer footprint as before. */
    private void drawBeveledButton(GuiGraphics guiGraphics, int x, int y, int size, int baseColor) {
        guiGraphics.fill(x, y, x + size, y + size, baseColor);
        int light = adjustBrightness(baseColor, 1.5f);
        int dark = adjustBrightness(baseColor, 0.5f);
        guiGraphics.fill(x, y, x + size, y + 1, light); // top edge
        guiGraphics.fill(x, y, x + 1, y + size, light); // left edge
        guiGraphics.fill(x, y + size - 1, x + size, y + size, dark); // bottom edge
        guiGraphics.fill(x + size - 1, y, x + size, y + size, dark); // right edge
    }

    private static int adjustBrightness(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = clamp255(Math.round(((argb >> 16) & 0xFF) * factor));
        int g = clamp255(Math.round(((argb >> 8) & 0xFF) * factor));
        int b = clamp255(Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clamp255(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void drawToggleSymbol(GuiGraphics guiGraphics, int toggleX, int toggleY, String symbol, int color) {
        drawToggleSymbol(guiGraphics, toggleX, toggleY, symbol, color, 0f);
    }

    private void drawToggleSymbol(GuiGraphics guiGraphics, int toggleX, int toggleY, String symbol, int color, float extraXOffset) {
        int symbolWidth = this.font.width(symbol);
        GuiCompat.pushPose(guiGraphics);
        GuiCompat.translate(guiGraphics, 0.5f + extraXOffset, 0.5f);
        guiGraphics.drawString(this.font, symbol, toggleX + (TOGGLE_SIZE - symbolWidth) / 2, toggleY + 2, color, false);
        GuiCompat.popPose(guiGraphics);
    }

    /** The same two icons cover both toggle rows: a cross means clicking will exclude/cancel, an arrow
     * means clicking will add/restore - which one shows just depends on which action clicking would take
     * next, no separate "undo" glyph needed. */
    private void drawStateToggle(GuiGraphics guiGraphics, int toggleX, int toggleY, boolean showCross) {
        if (showCross) {
            drawBeveledButton(guiGraphics, toggleX, toggleY, TOGGLE_SIZE, 0xFF884444);
            drawToggleSymbol(guiGraphics, toggleX, toggleY, "âœ•", 0xFFFFFF, -0.25f);
        } else {
            drawBeveledButton(guiGraphics, toggleX, toggleY, TOGGLE_SIZE, 0xFFCCAA00);
            drawToggleSymbol(guiGraphics, toggleX, toggleY, "â†’", 0x000000);
        }
    }

    private Component renderMemberRow(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int listY, int rowY, Contact member) {
        if (rowY + MEMBER_ROW_HEIGHT < listY || rowY > listY + MEMBER_LIST_HEIGHT)
            return null; // scrolled out of view

        boolean staged = stagedExcluded.contains(member.getNumber());
        drawPersonRow(guiGraphics, x, rowY, member, staged ? 0xFF5555 : 0xFFFFFF); // red once marked for exclusion

        boolean isSelf = member.getNumber().equals(viewerNumber);
        boolean canToggle = isSelf || viewerIsAdmin;
        if (!canToggle)
            return null;

        int toggleX = x + MEMBER_LIST_WIDTH - TOGGLE_SIZE;
        int toggleY = rowY + (MEMBER_ROW_HEIGHT - TOGGLE_SIZE) / 2;
        boolean hovered = isToggleHovered(mouseX, mouseY, toggleX, toggleY, listY);
        if (hovered)
            CursorEffects.requestPointerCursor();

        drawStateToggle(guiGraphics, toggleX, toggleY, !staged); // staged for exclusion -> arrow (put back), else cross (exclude)

        if (!hovered)
            return null;
        return Component.translatable(staged
                ? "gui.crazyphone.crazy_phone_group_settings.button_undo"
                : (isSelf ? "gui.crazyphone.crazy_phone_group_settings.button_leave" : "gui.crazyphone.crazy_phone_group_settings.button_exclude"));
    }

    /** A contact not yet in the group - anyone currently viewing the screen (not just the admin) can
     * invite them, so unlike {@link #renderMemberRow} there's no permission gate on showing the toggle. */
    private Component renderInvitableRow(GuiGraphics guiGraphics, int mouseX, int mouseY, int x, int listY, int rowY, Contact contact) {
        if (rowY + MEMBER_ROW_HEIGHT < listY || rowY > listY + MEMBER_LIST_HEIGHT)
            return null;

        boolean staged = stagedAdded.contains(contact.getNumber());
        drawPersonRow(guiGraphics, x, rowY, contact, staged ? 0x55FF55 : 0x808080); // green once marked to be added, grey otherwise

        int toggleX = x + MEMBER_LIST_WIDTH - TOGGLE_SIZE;
        int toggleY = rowY + (MEMBER_ROW_HEIGHT - TOGGLE_SIZE) / 2;
        boolean hovered = isToggleHovered(mouseX, mouseY, toggleX, toggleY, listY);
        if (hovered)
            CursorEffects.requestPointerCursor();

        drawStateToggle(guiGraphics, toggleX, toggleY, staged); // staged for addition -> cross (cancel), else arrow (add)

        if (!hovered)
            return null;
        return Component.translatable(staged
                ? "gui.crazyphone.crazy_phone_group_settings.button_undo"
                : "gui.crazyphone.crazy_phone_group_settings.button_add");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!cursorCarriedIcon.isEmpty()) {
            // Already carrying a copy - this click either places it (icon slot) or cancels the pickup
            // (anywhere else), and must not also trigger whatever's underneath it (a member toggle, a
            // real inventory pickup, etc.), so it's handled first and always consumes the click.
            if (isHoveringIconPreview((int) mouseX, (int) mouseY)) {
                stagedIcon = cursorCarriedIcon.copy();
            }
            cursorCarriedIcon = ItemStack.EMPTY;
            return true;
        }
        if (handleMemberListClick(mouseX, mouseY, button))
            return true;
        // ANY click (not just right-click) on a real inventory slot here is a clone-pickup, never a real
        // vanilla move - so no gesture the player might reach for (left-click, the natural "pick up"
        // motion, included) can ever actually take the item out of their inventory.
        if (handleInventoryIconPick())
            return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleMemberListClick(double mouseX, double mouseY, int button) {
        if (button != 0)
            return false;
        int listX = this.leftPos + CONTENT_X;
        int listY = this.topPos + MEMBER_LIST_Y;
        if (mouseX < listX || mouseX >= listX + MEMBER_LIST_WIDTH || mouseY < listY || mouseY >= listY + MEMBER_LIST_HEIGHT)
            return false;

        List<Contact> members = menu.getMembers();
        List<Contact> invitable = menu.getInvitableContacts();
        int index = (int) ((mouseY - listY + scrollPosition) / MEMBER_ROW_HEIGHT);
        if (index < 0 || index >= members.size() + invitable.size())
            return false;

        int toggleX = listX + MEMBER_LIST_WIDTH - TOGGLE_SIZE;
        if (mouseX < toggleX || mouseX >= toggleX + TOGGLE_SIZE)
            return false;

        if (index < members.size()) {
            Contact member = members.get(index);
            boolean isSelf = member.getNumber().equals(viewerNumber);
            if (!isSelf && !viewerIsAdmin)
                return false;
            if (!stagedExcluded.remove(member.getNumber()))
                stagedExcluded.add(member.getNumber());
        } else {
            Contact contact = invitable.get(index - members.size());
            if (!stagedAdded.remove(contact.getNumber()))
                stagedAdded.add(contact.getNumber());
        }
        return true;
    }

    /** Clicking a player-inventory item (any button) picks up a copy of it onto the cursor - the real
     * stack is never touched or moved. Click the icon preview slot afterward to place it there as the new
     * group icon (works for any item, not just heads), or click anywhere else to cancel - see the
     * carried-icon branch at the top of {@link #mouseClicked}.
     *
     * Deliberately uses the inherited {@code hoveredSlot} (vanilla's own per-frame hover tracking, kept
     * up to date by {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen} itself)
     * instead of a separate hand-rolled hit-test: a parallel computation risked disagreeing with vanilla's
     * by even a pixel at a slot's edge, and whenever it did, the click fell through to
     * {@code super.mouseClicked} - which, using its own (correct) hoveredSlot, went ahead and performed a
     * REAL pickup, silently defeating the whole point of "copy, don't take". Reading the same field
     * vanilla itself relies on makes that mismatch impossible. */
    private boolean handleInventoryIconPick() {
        if (this.hoveredSlot == null)
            return false;
        ItemStack stack = this.hoveredSlot.getItem();
        if (!stack.isEmpty())
            cursorCarriedIcon = stack.copy();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        int totalHeight = (menu.getMembers().size() + menu.getInvitableContacts().size()) * MEMBER_ROW_HEIGHT;
        scrollPosition -= (int) (dy * SCROLL_STEP);
        if (scrollPosition < 0 || totalHeight <= MEMBER_LIST_HEIGHT)
            scrollPosition = 0;
        else if (scrollPosition > totalHeight - MEMBER_LIST_HEIGHT)
            scrollPosition = totalHeight - MEMBER_LIST_HEIGHT;
        return true;
    }

    private void onCancel() {
        this.minecraft.player.closeContainer();
    }

    /** Deliberately doesn't close the container itself - the server always responds to a validated
     * change by reopening the (now possibly self-excluded-from) conversation or contacts screen, which
     * naturally replaces this one. Closing here too could race that reopen. */
    private void onValidate() {
        HashMap<String, String> textstate = new HashMap<>();
        textstate.put("conversationId", menu.getConversationId());
        textstate.put("groupName", nameField.getValue());
        // The full staged icon (every data component, not just the item id) round-trips as SNBT text so
        // the server preserves things like a custom name or enchantments instead of losing them down to
        // a bare item type.
        textstate.put("iconItem", (stagedIcon == null || stagedIcon.isEmpty())
                ? ""
                : CrazyPhoneHelper.encodeItemStack(this.entity.level(), stagedIcon).toString());
        textstate.put("excludedNumbers", String.join(",", stagedExcluded));
        textstate.put("addedNumbers", String.join(",", stagedAdded));
        //? if >=1.20.5 {
        /*NetworkAccess.sendToServer(new CrazyPhoneGroupSettingsButtonMessage(0, x, y, z, textstate));
        *///? } else {
        PacketDistributor.SERVER.noArg().send(new CrazyPhoneGroupSettingsButtonMessage(0, x, y, z, textstate));
        //?}
    }
}
