package fr.lordfinn.crazyphone.enchantment;

//? if >=1.20.5 {
/*import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import fr.lordfinn.crazyphone.data.SoulboundStash;

import java.util.ArrayList;
import java.util.List;

// Keeps soulbound-enchanted items out of a dying player's drops and hands them back on respawn - vanilla
// has no "survive death" mechanic of its own (only the reverse: Curse of Vanishing), so this has to be done
// entirely in event code rather than a data-driven enchantment effect component.
@EventBusSubscriber
public class SoulboundHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!Config.soulboundEnchantmentEnabled)
            return;
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        // keepInventory already keeps everything, including anything soulbound - nothing to stash.
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY))
            return;

        Holder<Enchantment> soulbound = resolveSoulbound(player);
        if (soulbound == null)
            return;

        List<ItemStack> kept = new ArrayList<>();
        event.getDrops().removeIf(itemEntity -> {
            ItemStack stack = itemEntity.getItem();
            if (EnchantmentHelper.getItemEnchantmentLevel(soulbound, stack) <= 0)
                return false;
            kept.add(stack);
            return true;
        });

        if (!kept.isEmpty()) {
            SoulboundStash stash = new SoulboundStash();
            stash.items = kept;
            player.setData(PhoneAttachmentTypes.SOULBOUND_STASH, stash);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath())
            return;
        Player original = event.getOriginal();
        if (!(original instanceof ServerPlayer) && !(event.getEntity() instanceof ServerPlayer))
            return;

        SoulboundStash stash = original.getData(PhoneAttachmentTypes.SOULBOUND_STASH);
        if (stash.items.isEmpty())
            return;

        if (event.getEntity() instanceof ServerPlayer newPlayer) {
            for (ItemStack stack : stash.items) {
                if (!newPlayer.getInventory().add(stack))
                    newPlayer.drop(stack, false);
            }
        }
        // Clear it on the new player too - copyOnDeath isn't used here (see PhoneAttachmentTypes), but
        // clearing defensively means a future respawn never sees a leftover stash from a previous death.
        event.getEntity().setData(PhoneAttachmentTypes.SOULBOUND_STASH, new SoulboundStash());
    }

    private static Holder<Enchantment> resolveSoulbound(ServerPlayer player) {
        return ModEnchantments.resolve(player.level());
    }
}
*///?}
