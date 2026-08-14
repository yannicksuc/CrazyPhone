package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

public class CrazyPhoneAddContactToPhoneProcedure {
	public static void execute(LevelAccessor world, String contact, String owner) {
		if (contact == null || owner == null)
			return;
		ListTag numbers;
		boolean alreadyPresent = false;
		numbers = (PhoneRegistrySavedData.get(world).contacts.get(owner)) instanceof ListTag _listTag ? _listTag.copy() : new ListTag();
		alreadyPresent = false;
		if (numbers.isEmpty()) {
			numbers = new ListTag();
		}
		for (Tag dataelementiterator : numbers) {
			if ((contact).equals(dataelementiterator instanceof StringTag _stringTag ? fr.lordfinn.crazyphone.utils.NbtCompat.asString(_stringTag) : "")) {
				alreadyPresent = true;
			}
		}
		if (!alreadyPresent) {
			numbers.addTag(0, StringTag.valueOf(contact));
			PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
			registry.contacts.put(owner, numbers);
			// Only the owner's own client ever reads this list, and only via a fresh server-side fetch
			// (CrazyPhoneGetContactsProcedure) whenever they open the Contacts screen - never from a
			// locally-synced copy - so this only needs a disk-persistence mark, not a network broadcast.
			registry.setDirty();
		}
	}
}
