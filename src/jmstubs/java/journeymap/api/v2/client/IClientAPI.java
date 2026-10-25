// Compile-only stand-in for JourneyMap API 2.0 (journeymap-api 1.20.1_2.0): the real classes come with JourneyMap.
// Only the members Ender Obelisks uses, with the exact signatures; never packaged.
package journeymap.api.v2.client;

import journeymap.api.v2.client.display.Displayable;
import journeymap.api.v2.common.CommonAPI;

public interface IClientAPI extends CommonAPI {
	void show(Displayable displayable) throws Exception;

	void remove(Displayable displayable);

	void removeAll(String modId);

	boolean exists(Displayable displayable);
}
