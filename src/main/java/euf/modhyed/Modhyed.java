package euf.modhyed;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
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

	public static final BlockEntityType<StatueBlockEntity> STATUE_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
			id("statue"), FabricBlockEntityTypeBuilder.create(StatueBlockEntity::new, EUF_STATUE, EUF_STATUE_CODEX).build());

	private static Block registerStatue(String name) {
		Block block = Registry.register(Registries.BLOCK, id(name),
				new StatueBlock(FabricBlockSettings.copyOf(Blocks.STONE_BRICKS).nonOpaque().luminance(4)));
		Registry.register(Registries.ITEM, id(name), new BlockItem(block, new Item.Settings()));
		return block;
	}

	public static final Item EUF_STAFF = Registry.register(Registries.ITEM, id("euf_staff"),
			new EufStaffItem(new Item.Settings().maxCount(1)));

	public static final EntityType<EufGolemEntity> EUF_GOLEM = Registry.register(Registries.ENTITY_TYPE, id("euf_golem"),
			FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, EufGolemEntity::new)
					.dimensions(EntityDimensions.fixed(0.8f, 1.4f)).trackRangeBlocks(8).build());
	public static final Item EUF_GOLEM_SPAWN_EGG = Registry.register(Registries.ITEM, id("euf_golem_spawn_egg"),
			new SpawnEggItem(EUF_GOLEM, 0x6B6580, 0xB83CDC, new Item.Settings()));

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(EUF_GEM));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.add(EUF_BLOCK_ITEM));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(EUF_STAFF));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(EUF_GOLEM_SPAWN_EGG));
		FabricDefaultAttributeRegistry.register(EUF_GOLEM, EufGolemEntity.createAttributes());
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			entries.add(EUF_STATUE);
			entries.add(EUF_STATUE_CODEX);
		});
		LOGGER.info("Modhyed loaded");
	}
}
