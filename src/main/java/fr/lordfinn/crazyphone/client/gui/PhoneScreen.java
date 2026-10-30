package fr.lordfinn.crazyphone.client.gui;

/** Marker implemented by every CrazyPhone screen, so shared code (e.g. auto-closing on item drop) doesn't need to know about each concrete screen class. */
public interface PhoneScreen {
	/** Zoom factor this screen is drawn at, relative to the vanilla GUI scale (see {@link PhoneScale}).
	 * 1 means "vanilla size"; a screen opts into the bigger phone by computing a factor in its init(). */
	default double phoneGuiScale() {
		return 1.0;
	}
}
