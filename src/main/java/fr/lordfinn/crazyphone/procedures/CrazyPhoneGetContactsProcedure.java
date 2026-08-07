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
		// contacts is already a fresh CompoundTag by construction (PhoneRegistrySavedData's field
		// initializer / read()), so it's never null and this get() never needs "repairing" - a previous
		// version reset it and broadcast the registry to every online player here, which fired on every
		// call while contacts was empty (e.g. the entire life of a fresh world) since this is a plain
		// getter invoked from hot paths like every menu open and every message notification.
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
