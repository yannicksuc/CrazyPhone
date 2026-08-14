package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

public class CrazyPhoneRemoveContactFromPhoneProcedure {
	public static void execute(LevelAccessor world, String contact, String owner) {
		if (contact == null || owner == null)
			return;
		PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
		ListTag numbers = (registry.contacts.get(owner)) instanceof ListTag _listTag ? _listTag.copy() : new ListTag();
		// Removes every matching entry (not just the last one found) in case duplicates ever exist.
		boolean removedAny = numbers.removeIf(tag -> tag instanceof StringTag stringTag && contact.equals(fr.lordfinn.crazyphone.utils.NbtCompat.asString(stringTag)));
		if (removedAny) {
			registry.contacts.put(owner, numbers);
			// No network sync needed - see CrazyPhoneAddContactToPhoneProcedure.
			registry.setDirty();
		}
	}
}
