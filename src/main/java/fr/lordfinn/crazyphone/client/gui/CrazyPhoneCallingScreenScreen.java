package fr.lordfinn.crazyphone.client.gui;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;

import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneCallingScreenMenu;

import java.util.function.Consumer;

/** Caller-side "Calling..." screen, shown between starting a call and it being answered. The ringback tone
 * itself isn't tied to this screen - see CallRingtoneManager, which plays it as long as the player actually
 * carries the calling phone, whether or not this screen (or any screen) is open. */
public class CrazyPhoneCallingScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneCallingScreenMenu> {
    private final Consumer<CrazyPhoneCallStateSyncPacket> callStateListener = this::onCallStateChanged;
    private Button button_cancel;

    public CrazyPhoneCallingScreenScreen(CrazyPhoneCallingScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public java.util.HashMap<String, Object> getWidgets() {
        return CrazyPhoneCallingScreenMenu.guistate;
    }

    @Override
    public void init() {
        super.init();
        setBackButtonActive(false);
        setHomeButtonActive(false);
        setLockButtonActive(false);
        ClientCallState.setListener(callStateListener);

        button_cancel = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_calling_screen.button_cancel"), e -> {
            PacketDistributor.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
        }).bounds(this.leftPos + 8, this.topPos + 158, 106, 14).build();
        this.addRenderableWidget(button_cancel);
    }

    @Override
    public void onClose() {
        super.onClose();
        ClientCallState.clearListener(callStateListener);
    }

    private void onCallStateChanged(CrazyPhoneCallStateSyncPacket packet) {
        if (!packet.conversationId().equals(menu.getConversationId()))
            return;
        if (packet.state() == CrazyPhoneCallStateSyncPacket.State.ACTIVE) {
            // Someone answered - hand off to the server to reopen us as the InCall screen.
            PacketDistributor.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.OPEN_CALL_SCREEN, menu.getConversationId()));
        } else if (packet.state() == CrazyPhoneCallStateSyncPacket.State.ENDED) {
            if (this.minecraft != null && this.minecraft.player != null)
                this.minecraft.player.closeContainer();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_calling_screen.title"));
        guiGraphics.drawCenteredString(this.font, Component.literal(menu.getDisplayTitle())
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                this.leftPos + 61, this.topPos + 100, 0xFFFFFF);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
