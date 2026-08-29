package fr.lordfinn.crazyphone.enchantment;

// Keeps soulbound-enchanted items out of a dying player's drops and hands them back on respawn - vanilla
// has no "survive death" mechanic of its own (only the reverse: Curse of Vanishing), so this has to be done
// entirely in event code rather than a data-driven enchantment effect component. NeoForge and Fabric get
// fully separate bodies below (rather than a shared-fields merge like PlayerPhoneState/SoulboundStash)
// since neither their event hooks nor their approach (pull items back OUT of already-generated drops vs.
// remove them from the inventory just BEFORE drops are generated) have anything in common.
//? if neoforge && >=1.20.5 {
/*import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import /^$ game_rules_pkg {^/net.minecraft.world.level.GameRules/^$}^/;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import fr.lordfinn.crazyphone.data.SoulboundStash;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber
public class SoulboundHandler {

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!Config.soulboundEnchantmentEnabled)
            return;
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;
        // keepInventory already keeps everything, including anything soulbound - nothing to stash.
        if (player.level().getGameRules()./^$ keep_inventory_call {^/getBoolean(GameRules.RULE_KEEPINVENTORY)/^$}^/)
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
//? if fabric && >=1.20.5 {
/*import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import /^$ game_rules_pkg {^/net.minecraft.world.level.GameRules/^$}^/;

import fr.lordfinn.crazyphone.Config;
import fr.lordfinn.crazyphone.data.PhoneAttachmentTypes;
import fr.lordfinn.crazyphone.data.SoulboundStash;

import java.util.ArrayList;
import java.util.List;

// Fabric's data-attachment-api-v1 has no LivingDropsEvent equivalent to pull items back OUT of an
// already-generated drop list, so instead this hooks the moment just before death is finalized
// (ServerLivingEntityEvents.ALLOW_DEATH) - the player's inventory is still intact at that point, so
// removing soulbound items here means vanilla's own death-drop logic (which reads straight from the live
// inventory) never sees them in the first place. Net effect on what ends up on the ground is identical to
// NeoForge's approach, just reached from the other end of the same pipeline.
public class SoulboundHandler {
    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (Config.soulboundEnchantmentEnabled && entity instanceof ServerPlayer player)
                stashSoulboundItems(player);
            return true;
        });

        // ServerPlayerEvents' "alive" flag is true when this respawn is NOT from death (e.g. returning from
        // the End through a portal) - see PhoneAttachmentTypes' COPY_FROM handler for the same convention.
        // Only a real death respawn (alive == false) should ever pull the stash back out.
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive)
                return;

            SoulboundStash stash = ((AttachmentTarget) oldPlayer).getAttached(PhoneAttachmentTypes.SOULBOUND_STASH);
            if (stash == null || stash.items.isEmpty())
                return;

            for (ItemStack stack : stash.items) {
                if (!newPlayer.getInventory().add(stack))
                    newPlayer.drop(stack, false);
            }
            ((AttachmentTarget) oldPlayer).setAttached(PhoneAttachmentTypes.SOULBOUND_STASH, new SoulboundStash());
        });
    }

    private static void stashSoulboundItems(ServerPlayer player) {
        // keepInventory already keeps everything, including anything soulbound - nothing to stash.
        if (player.level().getGameRules()./^$ keep_inventory_call {^/getBoolean(GameRules.RULE_KEEPINVENTORY)/^$}^/)
            return;

        Holder<Enchantment> soulbound = resolveSoulbound(player);
        if (soulbound == null)
            return;

        Inventory inventory = player.getInventory();
        List<ItemStack> kept = new ArrayList<>();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (EnchantmentHelper.getItemEnchantmentLevel(soulbound, stack) <= 0)
                continue;
            kept.add(stack.copy());
            inventory.setItem(slot, ItemStack.EMPTY);
        }

        if (!kept.isEmpty()) {
            SoulboundStash stash = new SoulboundStash();
            stash.items = kept;
            ((AttachmentTarget) player).setAttached(PhoneAttachmentTypes.SOULBOUND_STASH, stash);
        }
    }

    private static Holder<Enchantment> resolveSoulbound(ServerPlayer player) {
        return ModEnchantments.resolve(player.level());
    }
}
*///?}
