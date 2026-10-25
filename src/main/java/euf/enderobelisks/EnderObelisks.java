package euf.enderobelisks;

import euf.enderobelisks.block.ObeliskBlock;
import euf.enderobelisks.block.ObeliskBlockEntity;
import euf.enderobelisks.net.ObeliskPackets;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnderObelisks implements ModInitializer {
	public static final String MOD_ID = "ender_obelisks";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Obsidian-hard, explosion proof, pistons can't move it; the upper half (the caged eye) glows brighter
	public static final Block OBELISK = Registry.register(Registries.BLOCK, id("obelisk"),
			new ObeliskBlock(FabricBlockSettings.copyOf(Blocks.OBSIDIAN).nonOpaque().pistonBehavior(PistonBehavior.BLOCK)
					.luminance(state -> state.get(ObeliskBlock.HALF) == DoubleBlockHalf.UPPER ? 10 : 3)));
	public static final Item OBELISK_ITEM = Registry.register(Registries.ITEM, id("obelisk"),
			new ObeliskItem(OBELISK, new Item.Settings().maxCount(16)));
	public static final BlockEntityType<ObeliskBlockEntity> OBELISK_BLOCK_ENTITY = Registry.register(
			Registries.BLOCK_ENTITY_TYPE, id("obelisk"), FabricBlockEntityTypeBuilder.create(ObeliskBlockEntity::new, OBELISK).build());

	/** Crafted from redstone and emeralds; the crystal on top of every obelisk. */
	public static final Item TIME_SHARD = Registry.register(Registries.ITEM, id("time_shard"), new Item(new Item.Settings()));

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(OBELISK_ITEM));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries -> entries.add(TIME_SHARD));
		ObeliskConfig.load(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
		ObeliskSounds.init();
		ObeliskCriteria.init();
		ObeliskPackets.registerServerReceivers();
		ServerTickEvents.END_SERVER_TICK.register(ObeliskService::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ObeliskService.reset());
		// Taking damage breaks off a channelling jump
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player) {
				ObeliskService.onDamaged(player);
			}
			return true;
		});
		// Only the owner (or an operator) may break an obelisk; this also stops instant breaking in creative
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!state.isOf(OBELISK) || ObeliskBlock.mayBreak(state, player, world, pos)) {
				return true;
			}
			player.sendMessage(Text.translatable("ender_obelisks.message.not_owner"), true);
			return false;
		});
		LOGGER.info("Ender Obelisks loaded");
	}
}
