package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

public class CrazyPhoneRemoveContactFromPhoneProcedure {
	public static void execute(LevelAccessor world, String contact, String owner) {
		if (contact == null || owner == null)
			return;
		ListTag numbers;
		double indexToRemove = 0;
		double i = 0;
		numbers = (PhoneRegistrySavedData.get(world).contacts.get(owner)) instanceof ListTag _listTag ? _listTag.copy() : new ListTag();
		indexToRemove = -1;
		i = 0;
		if (!numbers.isEmpty()) {
			for (Tag dataelementiterator : numbers) {
				if ((contact).equals(dataelementiterator instanceof StringTag _stringTag ? _stringTag.getAsString() : "")) {
					indexToRemove = i;
				}
				i = i + 1;
			}
		}
		if (indexToRemove > -1) {
			numbers.remove((int) indexToRemove);
			PhoneRegistrySavedData.get(world).contacts.put(owner, numbers);
		}
	}
}
