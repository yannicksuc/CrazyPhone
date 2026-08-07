package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

public class CrazyPhoneGetContactsProcedure {
	public static ListTag execute(LevelAccessor world, String owner) {
		if (owner == null)
			return new ListTag();
		ListTag numbers;
		ListTag contacts;
		Tag tmp_element;
		CompoundTag contact;
		contacts = new ListTag();
		if (PhoneRegistrySavedData.get(world).contacts.isEmpty()) {
			PhoneRegistrySavedData.get(world).contacts = new CompoundTag();
			PhoneRegistrySavedData.get(world).syncToAll(world);
		}
		tmp_element = PhoneRegistrySavedData.get(world).contacts.get(owner);
		if (tmp_element == null) {
			return contacts;
		}
		numbers = tmp_element instanceof ListTag _listTag ? _listTag.copy() : new ListTag();
		if (!numbers.isEmpty()) {
			for (Tag dataelementiterator : numbers) {
				tmp_element = dataelementiterator;
				if (tmp_element == null) {
					continue;
				}
				tmp_element = PhoneRegistrySavedData.get(world).phones.get((tmp_element instanceof StringTag _stringTag ? _stringTag.getAsString() : ""));
				if (tmp_element == null) {
					continue;
				}
				contact = tmp_element instanceof CompoundTag _compoundTag ? _compoundTag.copy() : new CompoundTag();
				contact.put("number", dataelementiterator);
				contacts.addTag(0, contact);
			}
		}
		return contacts;
	}
}
