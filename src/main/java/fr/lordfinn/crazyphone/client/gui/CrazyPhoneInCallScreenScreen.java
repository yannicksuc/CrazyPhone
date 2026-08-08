package fr.lordfinn.crazyphone.client.gui;

import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;

import fr.lordfinn.crazyphone.client.ClientCallState;
import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallActionMessage;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket;
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneInCallScreenMenu;

import java.util.function.Consumer;

/**
 * Active-call screen. Escape (handled by the base class's keyPressed - closes the container) deliberately
 * does NOT end the call: call state lives in the server-side CallRegistry, entirely decoupled from this
 * menu's lifecycle, so closing this screen just stops looking at it.
 */
public class CrazyPhoneInCallScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneInCallScreenMenu> {
    private final Consumer<CrazyPhoneCallStateSyncPacket> callStateListener = this::onCallStateChanged;
    private Button button_hangup;

    public CrazyPhoneInCallScreenScreen(CrazyPhoneInCallScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public java.util.HashMap<String, Object> getWidgets() {
        return CrazyPhoneInCallScreenMenu.guistate;
    }

    @Override
    public void init() {
        super.init();
        setBackButtonActive(false);
        setHomeButtonActive(false);
        setLockButtonActive(false);
        ClientCallState.setListener(callStateListener);

        button_hangup = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_in_call_screen.button_hangup"), e -> {
            PacketDistributor.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
        }).bounds(this.leftPos + 7, this.topPos + 160, 108, 20).build();
        this.addRenderableWidget(button_hangup);
    }

    @Override
    public void onClose() {
        super.onClose();
        ClientCallState.clearListener(callStateListener);
    }

    private void onCallStateChanged(CrazyPhoneCallStateSyncPacket packet) {
        if (!packet.conversationId().equals(menu.getConversationId()))
            return;
        if (packet.state() == CrazyPhoneCallStateSyncPacket.State.ENDED) {
            if (this.minecraft != null && this.minecraft.player != null)
                this.minecraft.player.closeContainer();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_in_call_screen.title"));
        guiGraphics.drawCenteredString(this.font, Component.literal(menu.getDisplayTitle())
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                this.leftPos + 61, this.topPos + 100, 0xFFFFFF);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
