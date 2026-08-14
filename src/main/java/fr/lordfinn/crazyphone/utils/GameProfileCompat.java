package fr.lordfinn.crazyphone.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;

/** Single choke point for GameProfile accessors that changed shape when authlib turned GameProfile into a
 *  record as of the authlib version bundled with 1.21.10: getName/getId/getProperties became the record
 *  accessors name/id/properties. */
public final class GameProfileCompat {
    private GameProfileCompat() {
    }

    public static String name(GameProfile profile) {
        //? if <1.21.10 {
        return profile.getName();
        //? } else {
        /*return profile.name();
        *///?}
    }

    public static PropertyMap properties(GameProfile profile) {
        //? if <1.21.10 {
        return profile.getProperties();
        //? } else {
        /*return profile.properties();
        *///?}
    }
}
