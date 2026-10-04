package io.github.autyism.easysuppressions;

/** The two kinds of blocks (and their matching selectors). */
public enum SuppressorKind {
	PORTAL_CUTTER("portal_cutter"),
	LIGHT_SUPPRESSOR("light_suppressor");

	private final String id;

	SuppressorKind(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** Line colour of the zone box, active (deep) and inactive (pale), ARGB. */
	public int color(boolean active) {
		if (this == PORTAL_CUTTER) {
			return active ? 0xFFB13CFF : 0xFFD9B8FF;
		}
		return active ? 0xFFFF9500 : 0xFFFFD68A;
	}
}
