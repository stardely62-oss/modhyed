package euf.enderobelisks.client;

import euf.enderobelisks.net.ObeliskSync;
import java.util.ArrayList;
import java.util.List;

/** The obelisks this client may see, as last sent by the server; map integrations listen for changes. */
public final class ObeliskClientCache {
	private static List<ObeliskSync.Entry> entries = List.of();
	private static final List<Runnable> listeners = new ArrayList<>();

	private ObeliskClientCache() {
	}

	public static List<ObeliskSync.Entry> entries() {
		return entries;
	}

	public static void set(List<ObeliskSync.Entry> newEntries) {
		entries = List.copyOf(newEntries);
		listeners.forEach(Runnable::run);
	}

	public static void addListener(Runnable listener) {
		listeners.add(listener);
	}
}
