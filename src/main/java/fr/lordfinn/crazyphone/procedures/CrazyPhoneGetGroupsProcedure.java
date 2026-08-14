package fr.lordfinn.crazyphone.procedures;

import net.minecraft.world.level.LevelAccessor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.CrazyPhoneHelper;

/**
 * Resolves the given phone's "groups" list (conversation ids of every group conversation it's a
 * participant in) into full group entries: the conversation id plus the OTHER participants' contact
 * details (number/name/uuid/skin), so the client can render a group entry in the Contacts screen the
 * same way it renders an individual contact - just with several member heads instead of one.
 */
public class CrazyPhoneGetGroupsProcedure {
    public static ListTag execute(LevelAccessor world, String owner) {
        ListTag groupEntries = new ListTag();
        if (owner == null)
            return groupEntries;

        Tag phoneTag = PhoneRegistrySavedData.get(world).phones.get(owner);
        if (!(phoneTag instanceof CompoundTag phoneCompoundTag))
            return groupEntries;

        Tag groupsTag = phoneCompoundTag.get("groups");
        if (!(groupsTag instanceof ListTag groupIds))
            return groupEntries;

        for (Tag idTag : groupIds) {
            if (!(idTag instanceof StringTag stringTag))
                continue;
            String conversationId = fr.lordfinn.crazyphone.utils.NbtCompat.asString(stringTag);

            CrazyPhoneHelper.GroupMeta meta = CrazyPhoneHelper.getGroupMeta(world, conversationId);
            ListTag members = new ListTag();
            for (String number : meta.members()) {
                if (number.equals(owner))
                    continue;
                Tag memberPhoneTag = PhoneRegistrySavedData.get(world).phones.get(number);
                if (!(memberPhoneTag instanceof CompoundTag memberPhoneCompound))
                    continue;
                CompoundTag member = memberPhoneCompound.copy();
                member.putString("number", number);
                members.add(member);
            }
            if (members.isEmpty())
                continue; // every other participant unregistered somehow - nothing left to show

            CompoundTag groupEntry = new CompoundTag();
            groupEntry.putString("conversationId", conversationId);
            groupEntry.putString("name", meta.name());
            groupEntry.put("icon", CrazyPhoneHelper.encodeItemStack(world, meta.icon()));
            groupEntry.putString("admin", meta.admin());
            groupEntry.put("members", members);
            groupEntries.add(groupEntry);
        }

        return groupEntries;
    }
}
