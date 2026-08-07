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
			if ((contact).equals(dataelementiterator instanceof StringTag _stringTag ? _stringTag.getAsString() : "")) {
				alreadyPresent = true;
			}
		}
		if (!alreadyPresent) {
			numbers.addTag(0, StringTag.valueOf(contact));
			PhoneRegistrySavedData.get(world).contacts.put(owner, numbers);
		}
		PhoneRegistrySavedData.get(world).syncToAll(world);
	}
}
