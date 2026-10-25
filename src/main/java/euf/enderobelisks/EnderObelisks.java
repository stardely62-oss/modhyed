package euf.enderobelisks;

import euf.enderobelisks.block.ObeliskBlock;
import euf.enderobelisks.block.ObeliskBlockEntity;
import euf.enderobelisks.net.ObeliskPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnderObelisks implements ModInitializer {
	public static final String MOD_ID = "ender_obelisks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Obsidian-hard and explosion proof; the upper half (the caged eye) glows brighter
	public static final Block OBELISK = Registry.register(Registries.BLOCK, id("obelisk"),
			new ObeliskBlock(FabricBlockSettings.copyOf(Blocks.OBSIDIAN).nonOpaque()
					.luminance(state -> state.get(ObeliskBlock.HALF) == DoubleBlockHalf.UPPER ? 10 : 3)));
	public static final Item OBELISK_ITEM = Registry.register(Registries.ITEM, id("obelisk"),
			new BlockItem(OBELISK, new Item.Settings().maxCount(16)));
	public static final BlockEntityType<ObeliskBlockEntity> OBELISK_BLOCK_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, id("obelisk"), FabricBlockEntityTypeBuilder.create(ObeliskBlockEntity::new, OBELISK).build());

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(OBELISK_ITEM));
		ObeliskPackets.registerServerReceivers();
		LOGGER.info("Ender Obelisks loaded");
	}
}
