package fr.lordfinn.crazyphone.procedures;

//? if neoforge {
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
//? if >=1.20.5 {
/*import net.neoforged.fml.common.EventBusSubscriber;
*///? } else {
import net.neoforged.fml.common.Mod.EventBusSubscriber;
//?}
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.api.distmarker.Dist;
//?}
//? if fabric {
/*import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
*///?}

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import fr.lordfinn.crazyphone.init.ModItems;
import fr.lordfinn.crazyphone.utils.Contact;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;
import fr.lordfinn.crazyphone.utils.PhotoItemData;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

//? if neoforge {
@EventBusSubscriber(value = {Dist.CLIENT})
//?}
public class CrazyPhoneItemInInventoryTickProcedure {
	private static final DateTimeFormatter PHOTO_DATE_FORMATTER =
			DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

	//? if neoforge {
	//? if <1.21.10 {
	@OnlyIn(Dist.CLIENT)
	//?}
	@SubscribeEvent
	public static void onItemTooltip(ItemTooltipEvent event) {
		execute(event.getItemStack(), event.getToolTip());
	}
	//?}
	//? if fabric {
	/*public static void register() {
		//? if >=1.20.5 {
		/^ItemTooltipCallback.EVENT.register((itemStack, tooltipContext, tooltipType, list) -> execute(itemStack, list));
		^///? } else {
		ItemTooltipCallback.EVENT.register((itemStack, tooltipType, list) -> execute(itemStack, list));
		//?}
	}
	*///?}

	public static void execute(ItemStack itemstack, List<Component> tooltip) {
		if (tooltip == null)
			return;
		if (itemstack.getItem() == ModItems.CRAZY_PHONE.get()) {
			String number = fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(itemstack), "number");
			String name = fr.lordfinn.crazyphone.utils.NbtCompat.getString(PhoneTagAccess.getTag(itemstack), "name");
			if (!number.isEmpty()) {
				tooltip.add(Component.translatable("item.crazyphone.lore_number", number));
			}
			if (!name.isEmpty()) {
				tooltip.add(Component.translatable("item.crazyphone.lore_owner", name));
			}
		} else if (itemstack.getItem() == ModItems.CRAZY_PHONE_PHOTO.get()) {
			PhotoItemData data = PhotoItemData.fromStack(itemstack);
			if (data != null) {
				// This whole class only ever runs client-side (NeoForge: @OnlyIn(Dist.CLIENT) above; Fabric:
				// ItemTooltipCallback is inherently client-only), so Minecraft.getInstance().level is safe -
				// it's the synced client copy of PhoneRegistrySavedData, always up to date (see that class's
				// own doc comment on why it's always broadcast in full).
				Level level = net.minecraft.client.Minecraft.getInstance().level;
				Contact contact = level != null ? CrazyPhoneHelper.getContact(level, data.owner()) : null;
				String authorLabel = contact != null && contact.getName() != null && !contact.getName().isEmpty()
						? contact.getName() + " (" + data.owner() + ")"
						: data.owner();
				tooltip.add(Component.translatable("item.crazyphone.crazy_phone_photo.lore_author",
								Component.literal(authorLabel).withStyle(net.minecraft.ChatFormatting.YELLOW))
						.withStyle(net.minecraft.ChatFormatting.GRAY));
				tooltip.add(Component.translatable("item.crazyphone.crazy_phone_photo.lore_date",
								Component.literal(PHOTO_DATE_FORMATTER.format(Instant.ofEpochSecond(data.createdMinutes() * 60L)))
										.withStyle(net.minecraft.ChatFormatting.AQUA))
						.withStyle(net.minecraft.ChatFormatting.GRAY));
				// Only the vanilla item frame (FIXED display context) actually renders the pixel-art THUMBNAIL
				// by default (see CrazyPhonePhotoItemRenderer#renderFramedCard and ClientConfig#itemPreviewPixelated) -
				// the custom block-face frame (CrazyPhonePhotoFrameEntity, placed via right-click on a block)
				// deliberately always renders FULL resolution instead, since it's resizable up to 32 blocks and
				// a large mural at thumbnail resolution would just look like a blurry blown-up mess. Worded to
				// not overclaim the block-face frame's own look, matching that real difference.
				tooltip.add(Component.translatable("item.crazyphone.crazy_phone_photo.lore_display_hint")
						.withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
			}
		}
	}
}
