package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.ItemStack;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources./*$ res_loc {*/ResourceLocation/*$}*/;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;

import java.util.HashMap;

public class CrazyPhoneInitialFormValidationButtonClickProcedure {
public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, HashMap textstate) {
    if (entity == null || textstate == null)
        return;

		ResourceKey<SoundEvent> successSoundKey = ResourceKey.create(Registries.SOUND_EVENT, /*$ res_loc {*/ResourceLocation/*$}*/.tryParse("entity.experience_orb.pickup"));
		ResourceKey<SoundEvent> failSoundKey = ResourceKey.create(Registries.SOUND_EVENT, /*$ res_loc {*/ResourceLocation/*$}*/.tryParse("entity.villager.no"));

		Holder<SoundEvent> successSoundHolder = fr.lordfinn.crazyphone.utils.RegistryCompat.holderOrThrow(world.registryAccess(), Registries.SOUND_EVENT, successSoundKey);
		Holder<SoundEvent> failSoundHolder = fr.lordfinn.crazyphone.utils.RegistryCompat.holderOrThrow(world.registryAccess(), Registries.SOUND_EVENT, failSoundKey);


    if (CrazyPhoneGetInitialFormValidationMessageProcedure.OK.equals(CrazyPhoneGetInitialFormValidationMessageProcedure.execute(world, entity, textstate))) {
        RegisterNewPhoneFromFormProcedure.execute(world, entity, CrazyPhoneHelper.getMainHandItemOrEmpty(entity), textstate);

        if (entity instanceof ServerPlayer serverPlayer) {
            // Play success sound only to this player
            serverPlayer.connection.send(new ClientboundSoundPacket(successSoundHolder, SoundSource.NEUTRAL, x, y, z, 1.0F, 1.2F, 1));
        }

        PhoneTagAccess.setPhoneDisplayName(CrazyPhoneHelper.getMainHandItemOrEmpty(entity),
                fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(CrazyPhoneHelper.getMainHandItemOrEmpty(entity)), "name"));

        // The password/identity form is a custom no-slots menu (CrazyPhoneDefaultScreenMenu), so while it's
        // still open, vanilla's per-tick hotbar sync never looks at the mainhand slot - see that class's own
        // constructor/removed() for the same workaround. Without this, the name/number/display-name tags
        // just written above are correct on the server (visible via /crazyphone list) but never reach the
        // client, so the held phone keeps rendering as an unregistered, unnamed item.
        if (entity instanceof ServerPlayer serverPlayer)
            serverPlayer.inventoryMenu.broadcastChanges();

        CrazyPhoneOnUseProcedure.execute(world, x, y, z, entity);
    } else {
        if (entity instanceof ServerPlayer serverPlayer) {
            // Play fail sound only to this player
            serverPlayer.connection.send(new ClientboundSoundPacket(failSoundHolder, SoundSource.NEUTRAL, x, y, z, 1.0F, 1.0F, 1));
        }
    }
}
}
