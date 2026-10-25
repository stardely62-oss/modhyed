package euf.enderobelisks;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

/** Sound events of the obelisk. They reuse vanilla recordings at other pitches (assets/ender_obelisks/sounds.json). */
public final class ObeliskSounds {
	public static final SoundEvent HUM = register("obelisk.hum");
	public static final SoundEvent PLACE = register("obelisk.place");
	public static final SoundEvent CHANNEL = register("obelisk.channel");
	public static final SoundEvent TELEPORT = register("obelisk.teleport");
	public static final SoundEvent CANCEL = register("obelisk.cancel");
	public static final SoundEvent UPGRADE = register("obelisk.upgrade");

	private ObeliskSounds() {
	}

	private static SoundEvent register(String name) {
		return Registry.register(Registries.SOUND_EVENT, EnderObelisks.id(name), SoundEvent.of(EnderObelisks.id(name)));
	}

	/** Forces class loading from the mod initializer, so the events are registered in time. */
	public static void init() {
	}
}
