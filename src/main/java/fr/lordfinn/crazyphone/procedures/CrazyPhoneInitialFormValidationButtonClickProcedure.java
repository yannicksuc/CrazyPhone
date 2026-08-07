package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Holder;

import java.util.HashMap;

public class CrazyPhoneInitialFormValidationButtonClickProcedure {
public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, HashMap textstate) {
    if (entity == null || textstate == null)
        return;

		ResourceKey<SoundEvent> successSoundKey = ResourceKey.create(Registries.SOUND_EVENT, ResourceLocation.tryParse("entity.experience_orb.pickup"));
		ResourceKey<SoundEvent> failSoundKey = ResourceKey.create(Registries.SOUND_EVENT, ResourceLocation.tryParse("entity.villager.no"));

		Holder<SoundEvent> successSoundHolder = world.registryAccess().registryOrThrow(Registries.SOUND_EVENT).getHolderOrThrow(successSoundKey);
		Holder<SoundEvent> failSoundHolder = world.registryAccess().registryOrThrow(Registries.SOUND_EVENT).getHolderOrThrow(failSoundKey);


    if (("Ok!").equals(CrazyPhoneGetInitialFormValidationMessageProcedure.execute(world, entity, textstate))) {
        RegisterNewPhoneFromFormProcedure.execute(world, entity, CrazyPhoneHelper.getMainHandItemOrEmpty(entity), textstate);

        if (entity instanceof ServerPlayer serverPlayer) {
            // Play success sound only to this player
            serverPlayer.connection.send(new ClientboundSoundPacket(successSoundHolder, SoundSource.NEUTRAL, x, y, z, 1.0F, 1.2F, 1));
        }

        (CrazyPhoneHelper.getMainHandItemOrEmpty(entity)).set(DataComponents.CUSTOM_NAME,
                Component.literal(("CrazyPhone de " + ((CrazyPhoneHelper.getMainHandItemOrEmpty(entity)).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("name")))));
        CrazyPhoneOnUseProcedure.execute(world, x, y, z, entity);
    } else {
        if (entity instanceof ServerPlayer serverPlayer) {
            // Play fail sound only to this player
            serverPlayer.connection.send(new ClientboundSoundPacket(failSoundHolder, SoundSource.NEUTRAL, x, y, z, 1.0F, 1.0F, 1));
        }
    }
}
}
