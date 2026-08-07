package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * Unregisters a phone number: removes its entry from the registry (freeing the number back up), its own
 * contact list, and any reference to it in OTHER phones' contact lists (a dangling contact pointing at a
 * deleted number would otherwise show up as broken/unresolvable data). Message history in
 * ConversationSavedData is left untouched - deleting a phone number shouldn't silently wipe conversation
 * history other players may still care about.
 */
public class CrazyPhoneDeletePhoneByNumberProcedure {
    public static boolean execute(LevelAccessor world, String number) {
        if (number == null || number.isEmpty())
            return false;

        PhoneRegistrySavedData registry = PhoneRegistrySavedData.get(world);
        if (!registry.phones.contains(number))
            return false;

        registry.phones.remove(number);
        registry.contacts.remove(number);

        for (String owner : List.copyOf(registry.contacts.getAllKeys())) {
            Tag tag = registry.contacts.get(owner);
            if (!(tag instanceof ListTag numbers))
                continue;

            List<Tag> remaining = new ArrayList<>();
            boolean changed = false;
            for (Tag entry : numbers) {
                if (entry instanceof StringTag stringTag && number.equals(stringTag.getAsString())) {
                    changed = true;
                    continue;
                }
                remaining.add(entry);
            }

            if (changed) {
                ListTag updated = new ListTag();
                updated.addAll(remaining);
                registry.contacts.put(owner, updated);
            }
        }

        registry.syncToAll(world);
        return true;
    }
}
