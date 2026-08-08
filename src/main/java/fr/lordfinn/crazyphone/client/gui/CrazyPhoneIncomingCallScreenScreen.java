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
import fr.lordfinn.crazyphone.world.inventory.CrazyPhoneIncomingCallScreenMenu;

import java.util.function.Consumer;

/** Callee-side "Incoming call" screen, shown while ringing and not yet answered - distinct from both the
 * caller-side Calling screen and the active-call InCall screen, with its own Accept/Decline choice instead
 * of answering automatically the instant the phone is used. The ringtone itself isn't tied to this screen -
 * see CallRingtoneManager, which plays it as long as the player actually carries the ringing phone, whether
 * or not this screen (or any screen) is open. */
public class CrazyPhoneIncomingCallScreenScreen extends CrazyPhoneDefaultScreenScreen<CrazyPhoneIncomingCallScreenMenu> {
    private final Consumer<CrazyPhoneCallStateSyncPacket> callStateListener = this::onCallStateChanged;
    private Button button_accept;
    private Button button_decline;

    public CrazyPhoneIncomingCallScreenScreen(CrazyPhoneIncomingCallScreenMenu container, Inventory inventory, Component text) {
        super(container, inventory, text);
    }

    public java.util.HashMap<String, Object> getWidgets() {
        return CrazyPhoneIncomingCallScreenMenu.guistate;
    }

    @Override
    public void init() {
        super.init();
        setBackButtonActive(false);
        setHomeButtonActive(false);
        setLockButtonActive(false);
        ClientCallState.setListener(callStateListener);

        button_accept = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_incoming_call_screen.button_accept"), e -> {
            PacketDistributor.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.ANSWER, menu.getConversationId()));
        }).bounds(this.leftPos + 7, this.topPos + 137, 108, 20).build();
        this.addRenderableWidget(button_accept);

        button_decline = Button.builder(Component.translatable("gui.crazyphone.crazy_phone_incoming_call_screen.button_decline"), e -> {
            PacketDistributor.sendToServer(new CrazyPhoneCallActionMessage(CrazyPhoneCallActionMessage.HANGUP, menu.getConversationId()));
        }).bounds(this.leftPos + 7, this.topPos + 160, 108, 20).build();
        this.addRenderableWidget(button_decline);
    }

    @Override
    public void onClose() {
        super.onClose();
        ClientCallState.clearListener(callStateListener);
    }

    private void onCallStateChanged(CrazyPhoneCallStateSyncPacket packet) {
        if (!packet.conversationId().equals(menu.getConversationId()))
            return;
        // Missed (caller cancelled, or the ring timeout expired) - nothing left to accept/decline.
        if (packet.state() == CrazyPhoneCallStateSyncPacket.State.ENDED) {
            if (this.minecraft != null && this.minecraft.player != null)
                this.minecraft.player.closeContainer();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderHeader(guiGraphics, new ItemStack(ModItems.CRAZY_PHONE.get()),
                Component.translatable("gui.crazyphone.crazy_phone_incoming_call_screen.title"));
        guiGraphics.drawCenteredString(this.font, Component.literal(menu.getDisplayTitle())
                        .withStyle(style -> style.withColor(ChatFormatting.GRAY)),
                this.leftPos + 61, this.topPos + 100, 0xFFFFFF);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
