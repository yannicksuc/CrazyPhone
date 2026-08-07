package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.UUID;
import de.maxhenkel.camera.Main;
import de.maxhenkel.camera.ModSounds;
import de.maxhenkel.camera.gui.CameraScreen;
import de.maxhenkel.camera.net.MessageTakeImage;
import fr.lordfinn.crazyphone.utils.CameraModHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

public class CrazyPhoneTakePhotoProcedure {
    public static void execute(LevelAccessor world, Entity entity) {
        if (!(entity instanceof Player playerIn)) return;
        if (!(world instanceof Level worldIn)) return;

        ItemStack stack = playerIn.getItemInHand(InteractionHand.MAIN_HAND);

        if (playerIn.isShiftKeyDown() && !CameraModHelper.isActive(stack)) {
            if (worldIn.isClientSide) {
                openClientGui(stack.get(Main.SHADER_DATA_COMPONENT));
            }
            return;
        }

        if (!(playerIn instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!CameraModHelper.isActive(stack)) {
            playerIn.closeContainer();
            Main.CAMERA.get().setActive(stack, true);
        } else if (Main.PACKET_MANAGER.canTakeImage(playerIn.getUUID())) {
            worldIn.playSound(null, playerIn.blockPosition(), ModSounds.TAKE_IMAGE.get(), SoundSource.AMBIENT, 1F, 1F);
            UUID uuid = UUID.randomUUID();
            PacketDistributor.sendToPlayer(serverPlayer, new MessageTakeImage(uuid));
            Main.CAMERA.get().setActive(stack, false);
        } else {
            playerIn.displayClientMessage(Component.translatable("message.image_cooldown"), true);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static void openClientGui(String currentShader) {
        Minecraft.getInstance().setScreen(new CameraScreen(currentShader));
    }
}