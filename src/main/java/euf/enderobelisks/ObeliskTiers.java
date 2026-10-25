package euf.enderobelisks;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Range and upgrade cost per obelisk tier, from the server config. */
public final class ObeliskTiers {
	public static final int MAX_TIER = 4;

	private ObeliskTiers() {
	}

	public static int range(int tier) {
		return ObeliskConfig.get().ranges[Math.max(0, Math.min(MAX_TIER, tier))];
	}

	/** Items consumed to go from {@code tier} to {@code tier + 1}; empty at the max tier. */
	public static List<ItemStack> upgradeCost(int tier) {
		if (tier < 0 || tier >= MAX_TIER) {
			return List.of();
		}
		List<ItemStack> cost = new ArrayList<>();
		for (ObeliskConfig.Cost entry : ObeliskConfig.get().upgradeCosts.get(tier)) {
			Item item = Registries.ITEM.get(Identifier.tryParse(entry.item == null ? "" : entry.item));
			if (item != Items.AIR && entry.count > 0) {
				cost.add(new ItemStack(item, entry.count));
			}
		}
		return cost;
	}
}
