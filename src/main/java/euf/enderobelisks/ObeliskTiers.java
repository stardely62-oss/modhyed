package euf.enderobelisks;

import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Range and upgrade cost per obelisk tier. Shared by the server (rules) and the client (menu). */
public final class ObeliskTiers {
	public static final int MAX_TIER = 4;
	private static final int[] RANGE = {500, 1000, 2000, 3500, 5000};

	private ObeliskTiers() {
	}

	public static int range(int tier) {
		return RANGE[Math.max(0, Math.min(MAX_TIER, tier))];
	}

	/** Items consumed to go from {@code tier} to {@code tier + 1}; empty at the max tier. */
	public static List<ItemStack> upgradeCost(int tier) {
		return switch (tier) {
			case 0 -> List.of(new ItemStack(Items.GOLD_BLOCK), new ItemStack(Items.ENDER_PEARL, 4));
			case 1 -> List.of(new ItemStack(Items.DIAMOND, 4), new ItemStack(Items.ENDER_PEARL, 8));
			case 2 -> List.of(new ItemStack(Items.ECHO_SHARD, 8));
			case 3 -> List.of(new ItemStack(Items.NETHER_STAR));
			default -> List.of();
		};
	}
}
