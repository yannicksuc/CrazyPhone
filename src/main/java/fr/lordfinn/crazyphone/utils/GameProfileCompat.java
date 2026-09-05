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

    public static java.util.UUID id(GameProfile profile) {
        //? if <1.21.10 {
        return profile.getId();
        //? } else {
        /*return profile.id();
        *///?}
    }

    public static PropertyMap properties(GameProfile profile) {
        //? if <1.21.10 {
        return profile.getProperties();
        //? } else {
        /*return profile.properties();
        *///?}
    }

    /** Adds a "textures" property to a freshly-constructed synthetic GameProfile (a custom player-head icon,
     *  never a real player's own profile). Pre-1.21.10 PropertyMap is mutable, so this just mutates in place
     *  and returns the same instance; on 1.21.10 GameProfile became an immutable record and its PropertyMap
     *  (PropertyMap.EMPTY for the 2-arg GameProfile constructor) throws UnsupportedOperationException on any
     *  mutation, so this instead builds a fresh mutable multimap with the property already in it and returns
     *  a brand new GameProfile wrapping it. Callers must use the returned profile, not assume the one passed
     *  in was mutated. */
    public static GameProfile withTextureProperty(GameProfile profile, String textureValue) {
        //? if <1.21.10 {
        profile.getProperties().put("textures", new com.mojang.authlib.properties.Property("textures", textureValue));
        return profile;
        //? } else {
        /*com.google.common.collect.Multimap<String, com.mojang.authlib.properties.Property> multimap =
                com.google.common.collect.HashMultimap.create(profile.properties());
        multimap.put("textures", new com.mojang.authlib.properties.Property("textures", textureValue));
        return new GameProfile(profile.id(), profile.name(), new PropertyMap(multimap));
        *///?}
    }
}
