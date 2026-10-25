package euf.enderobelisks;

/** The marker colours an owner can pick for an obelisk (menu map, JourneyMap, name plate). */
public final class ObeliskColors {
	public static final int[] RGB = {
			0x5FD3A0, // ender green (default)
			0x7DBEFF, // sky
			0xE6E8EA, // bone
			0xFFD24A, // gold
			0xE0703A, // ember
			0xC0384A, // blood
			0xA77CE8, // amethyst
			0x8A9A5B, // moss
	};

	private ObeliskColors() {
	}

	public static int rgb(int index) {
		return RGB[Math.floorMod(index, RGB.length)];
	}
}
