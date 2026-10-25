package euf.modhyed;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Modhyed implements ModInitializer {
	public static final String MOD_ID = "modhyed";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final Item EUF_GEM = Registry.register(Registries.ITEM, id("euf_gem"), new Item(new Item.Settings()));

	public static final Block EUF_BLOCK = Registry.register(Registries.BLOCK, id("euf_block"),
			new Block(FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).luminance(7)));
	public static final Item EUF_BLOCK_ITEM = Registry.register(Registries.ITEM, id("euf_block"),
			new BlockItem(EUF_BLOCK, new Item.Settings()));

	// Same model, two texture sets: drawn by a script (hand) and generated with Codex
	public static final Block EUF_STATUE = registerStatue("euf_statue");
	public static final Block EUF_STATUE_CODEX = registerStatue("euf_statue_codex");

	private static Block registerStatue(String name) {
		Block block = Registry.register(Registries.BLOCK, id(name),
				new StatueBlock(FabricBlockSettings.copyOf(Blocks.STONE_BRICKS).nonOpaque().luminance(4)));
		Registry.register(Registries.ITEM, id(name), new BlockItem(block, new Item.Settings()));
		return block;
	}

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(EUF_GEM));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.add(EUF_BLOCK_ITEM));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(EUF_STATUE);
			entries.add(EUF_STATUE_CODEX);
		});
		LOGGER.info("Modhyed loaded");
	}
}
