package euf.enderobelisks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Server settings, read from config/ender_obelisks.json (written with the defaults on first start). Modpacks tune
 * the balance here. The client never reads this file: the server sends what the menu needs.
 */
public class ObeliskConfig {
	public static final class Cost {
		public String item;
		public int count;

		public Cost(String item, int count) {
			this.item = item;
			this.count = count;
		}
	}

	/** Teleport range per tier, 0..4. */
	public int[] ranges = {500, 1000, 2000, 3500, 5000};
	/** What it costs to go from tier i to tier i + 1. */
	public List<List<Cost>> upgradeCosts = List.of(
			List.of(new Cost("minecraft:gold_block", 1), new Cost("minecraft:ender_pearl", 4)),
			List.of(new Cost("minecraft:diamond", 4), new Cost("minecraft:ender_pearl", 8)),
			List.of(new Cost("minecraft:echo_shard", 8)),
			List.of(new Cost("minecraft:nether_star", 1)));
	public int cooldownSeconds = 30;
	/** Seconds of channelling before a jump; moving or taking damage cancels it. 0 jumps at once. */
	public int warmupSeconds = 3;
	/** Allow jumps between dimensions, from obelisks of at least this tier. */
	public boolean crossDimension = false;
	public int crossDimensionMinTier = 4;
	/** Only the owner (and operators) may break an obelisk. */
	public boolean ownerOnlyBreak = true;
	/** Tamed pets that are not sitting, and animals on your lead, jump with you. */
	public boolean takePets = true;
	public int petRadius = 12;
	/** Return shard: how long to hold it, its cooldown, and whether it may cross dimensions. */
	public int returnShardSeconds = 3;
	public int returnShardCooldownSeconds = 60;
	public boolean returnShardCrossDimension = true;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ObeliskConfig instance = new ObeliskConfig();

	public static ObeliskConfig get() {
		return instance;
	}

	public static void load(Path file) {
		ObeliskConfig loaded = null;
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				loaded = GSON.fromJson(reader, ObeliskConfig.class);
			} catch (IOException | RuntimeException e) {
				EnderObelisks.LOGGER.error("Could not read {}, using the defaults", file, e);
			}
		}
		instance = loaded == null ? new ObeliskConfig() : loaded.validated();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(instance), StandardCharsets.UTF_8);
		} catch (IOException e) {
			EnderObelisks.LOGGER.warn("Could not write {}", file, e);
		}
	}

	/** Falls back to the defaults for anything missing or out of range. */
	private ObeliskConfig validated() {
		ObeliskConfig defaults = new ObeliskConfig();
		if (ranges == null || ranges.length != ObeliskTiers.MAX_TIER + 1) {
			ranges = defaults.ranges;
		}
		for (int i = 0; i < ranges.length; i++) {
			ranges[i] = Math.max(1, ranges[i]);
		}
		if (upgradeCosts == null || upgradeCosts.size() != ObeliskTiers.MAX_TIER) {
			upgradeCosts = defaults.upgradeCosts;
		}
		cooldownSeconds = Math.max(0, cooldownSeconds);
		warmupSeconds = Math.max(0, warmupSeconds);
		crossDimensionMinTier = Math.max(0, Math.min(ObeliskTiers.MAX_TIER, crossDimensionMinTier));
		petRadius = Math.max(0, Math.min(32, petRadius));
		returnShardSeconds = Math.max(1, returnShardSeconds);
		returnShardCooldownSeconds = Math.max(0, returnShardCooldownSeconds);
		return this;
	}
}
