// Compile-only stand-in for JourneyMap API 2.0 (journeymap-api 1.20.1_2.0): the real classes come with JourneyMap.
// Only the members Ender Obelisks uses, with the exact signatures; never packaged.
package journeymap.api.v2.client.display;

import journeymap.api.v2.client.model.TextProperties;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public abstract class Overlay extends Displayable {
	public Overlay setDimension(RegistryKey<World> dimension) {
		throw new UnsupportedOperationException();
	}

	public Overlay setTitle(String title) {
		throw new UnsupportedOperationException();
	}

	public Overlay setLabel(String label) {
		throw new UnsupportedOperationException();
	}

	public Overlay setTextProperties(TextProperties textProperties) {
		throw new UnsupportedOperationException();
	}
}
