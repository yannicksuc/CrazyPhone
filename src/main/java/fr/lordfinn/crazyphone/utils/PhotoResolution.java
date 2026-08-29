package fr.lordfinn.crazyphone.utils;

/** The two resolutions every captured photo is stored/served in - shared vocabulary across capture,
 * storage, network and rendering instead of a boolean sprinkled through all four. */
public enum PhotoResolution {
    THUMBNAIL,
    FULL
}
