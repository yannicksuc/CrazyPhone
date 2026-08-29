package fr.lordfinn.crazyphone.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.neoforge.client.event.RenderHandEvent;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.network.CrazyPhoneCallStateSyncPacket.State;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

/**
 * Makes a ringing CrazyPhone buzz in the holder's hand: short bursts of jitter separated by pauses, like a
 * real phone vibrating on a table, rather than one continuous shake. Reads {@link CrazyPhoneHelper#getPhoneCallState}
 * directly off the rendered {@code stack} itself - same pattern as {@link fr.lordfinn.crazyphone.item.CrazyPhoneItemProperties}'s
 * texture predicates - so a bystander watching someone else's phone ring in their hand sees the same buzz, not
 * just the ringing player's own client.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class CrazyPhoneVibrationRenderer {
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        boolean isPhone = event.getItemStack().getItem() == ModItems.CRAZY_PHONE.get();
        String callState = isPhone ? CrazyPhoneHelper.getPhoneCallState(event.getItemStack()) : null;
        if (!(isPhone && State.RINGING.name().equals(callState)))
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        float time = mc.level.getGameTime() + event.getPartialTick();
        float phase = time % CallVibrationTiming.CYCLE_TICKS;
        // A single smooth 0 -> 1 -> 0 bump across the buzzing window, then flat silence for the rest of the
        // cycle - reads as a pulse coming and going rather than an abrupt on/off jump.
        float envelope = phase < CallVibrationTiming.BUZZ_TICKS ? Mth.sin((float) Math.PI * phase / CallVibrationTiming.BUZZ_TICKS) : 0f;
        if (envelope <= 0f)
            return;

        PoseStack poseStack = event.getPoseStack();
        float shakeX = Mth.sin(time * 3.5f) * 0.02f * envelope;
        float shakeY = Mth.cos(time * 5.3f) * 0.015f * envelope;
        float shakeRot = Mth.sin(time * 4.1f) * 4.0f * envelope;
        poseStack.translate(shakeX, shakeY, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(shakeRot));
    }
}
