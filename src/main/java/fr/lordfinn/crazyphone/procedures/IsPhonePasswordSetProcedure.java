package fr.lordfinn.crazyphone.procedures;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;

import fr.lordfinn.crazyphone.data.PhoneRegistrySavedData;
import fr.lordfinn.crazyphone.utils.NbtCompat;
import fr.lordfinn.crazyphone.utils.PhoneTagAccess;

/**
 * Whether the phone registered under this ItemStack's own number currently has a non-empty password on
 * file in the registry (the authoritative source - see RegisterNewPhoneFromFormProcedure, which is what
 * actually writes it). False for both an unregistered phone and one registered while
 * Config#requirePhonePassword was off and the password field was left empty.
 *
 * A passwordless phone can never be locked at all - there would be no way back in except an empty-field
 * prompt serving no purpose - so every call site that needs to know "can this specific phone be locked"
 * goes through this one check: CrazyPhoneLockProcedure (server-side no-op guard), the home screen's own
 * lock button (visually disabled via setLockButtonActive), CrazyPhoneSignInScreenScreen (redirects straight
 * past a login prompt it would otherwise show for such a phone), and the auto-lock-on-disconnect sweep
 * (CrazyPhoneHelper#applyAutoLockOnDisconnect, which must never lock what can't be unlocked again).
 */
public class IsPhonePasswordSetProcedure {
    public static boolean execute(LevelAccessor world, ItemStack phone) {
        String number = NbtCompat.getString(PhoneTagAccess.getTag(phone), "number");
        if (number.isEmpty())
            return false;
        return PhoneRegistrySavedData.get(world).phones.get(number) instanceof CompoundTag phoneData
                && !NbtCompat.getString(phoneData, "password").isEmpty();
    }
}
