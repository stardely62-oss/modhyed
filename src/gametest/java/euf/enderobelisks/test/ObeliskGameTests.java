package euf.enderobelisks.test;

import euf.enderobelisks.EnderObelisks;
import euf.enderobelisks.ObeliskConfig;
import euf.enderobelisks.ObeliskInfo;
import euf.enderobelisks.ObeliskService;
import euf.enderobelisks.ObeliskService.TeleportResult;
import euf.enderobelisks.ObeliskTiers;
import euf.enderobelisks.block.ObeliskBlock;
import euf.enderobelisks.block.ObeliskBlockEntity;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.FacingBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class ObeliskGameTests implements FabricGameTest {
	/** Sets both halves the way placing does (upper first, so the lower half never sees air above it). */
	static void setObelisk(ServerWorld world, BlockPos lower, Direction facing) {
		BlockState state = EnderObelisks.OBELISK.getDefaultState().with(ObeliskBlock.FACING, facing);
		world.setBlockState(lower.up(), state.with(ObeliskBlock.HALF, DoubleBlockHalf.UPPER));
		world.setBlockState(lower, state.with(ObeliskBlock.HALF, DoubleBlockHalf.LOWER));
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void placingRegistersAndBreakingTheTopRemovesEverything(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos lower = ctx.getAbsolutePos(new BlockPos(2, 2, 2));
		setObelisk(world, lower, Direction.NORTH);
		ObeliskInfo info = ObeliskService.register(world, lower, null, "Test");

		check(ObeliskService.at(world, lower).isPresent(), "obelisk not in the registry");
		check(info.name.equals("Test") && info.tier == 0 && info.range() == 500 && info.isPublic,
				"new obelisk should be public, tier 0, 500 blocks, named Test: " + info.name + " " + info.tier);
		check(world.getBlockEntity(lower) instanceof ObeliskBlockEntity entity && info.id.equals(entity.getObeliskId())
				&& entity.getName().equals("Test"), "the lower half should carry the synced block entity");
		check(world.getBlockEntity(lower.up()) == null, "the upper half should have no block entity");
		check(ObeliskService.register(world, lower, null, "Again").id.equals(info.id), "registering twice should keep one entry");

		world.breakBlock(lower.up(), false);
		check(world.getBlockState(lower).isAir(), "breaking the top half should remove the bottom half");
		check(ObeliskService.at(world, lower).isEmpty(), "a broken obelisk should leave the registry");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void tiersGrowFrom500To5000(TestContext ctx) {
		int[] expected = {500, 1000, 2000, 3500, 5000};
		for (int tier = 0; tier <= ObeliskTiers.MAX_TIER; tier++) {
			check(ObeliskTiers.range(tier) == expected[tier], "tier " + tier + " range " + ObeliskTiers.range(tier));
		}
		check(ObeliskTiers.range(99) == 5000 && ObeliskTiers.range(-1) == 500, "range must clamp");
		check(ObeliskTiers.upgradeCost(3).get(0).isOf(Items.NETHER_STAR), "the last upgrade costs a nether star");
		check(ObeliskTiers.upgradeCost(ObeliskTiers.MAX_TIER).isEmpty(), "no upgrade past the max tier");
		check(ObeliskService.clampName("  " + "x".repeat(50) + " ").length() == ObeliskService.MAX_NAME_LENGTH, "names are clamped");
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void recipesUseTheTimeShard(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		Recipe<?> shard = world.getRecipeManager().get(EnderObelisks.id("time_shard"))
				.orElseThrow(() -> new GameTestException("no time shard recipe"));
		check(shard.getOutput(world.getRegistryManager()).isOf(EnderObelisks.TIME_SHARD), "the time shard recipe makes a time shard");
		check(shard.getIngredients().stream().anyMatch(i -> i.test(new ItemStack(Items.REDSTONE)))
				&& shard.getIngredients().stream().anyMatch(i -> i.test(new ItemStack(Items.EMERALD))), "time shard = redstone + emeralds");
		Recipe<?> obelisk = world.getRecipeManager().get(EnderObelisks.id("obelisk"))
				.orElseThrow(() -> new GameTestException("no obelisk recipe"));
		for (ItemStack needed : List.of(new ItemStack(EnderObelisks.TIME_SHARD), new ItemStack(Items.DIAMOND),
				new ItemStack(Items.OBSIDIAN), new ItemStack(Items.ENDER_PEARL))) {
			check(obelisk.getIngredients().stream().anyMatch(i -> i.test(needed)), "the obelisk recipe needs " + needed.getItem());
		}
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "player")
	public void teleportMovesThePlayerAndStartsTheCooldown(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(6, 2, 6));
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				world.setBlockState(a.add(x, -1, z), Blocks.STONE.getDefaultState());
				world.setBlockState(b.add(x, -1, z), Blocks.STONE.getDefaultState());
			}
		}
		setObelisk(world, a, Direction.SOUTH);
		setObelisk(world, b, Direction.NORTH);
		ObeliskInfo infoA = ObeliskService.register(world, a, null, "A");
		ObeliskInfo infoB = ObeliskService.register(world, b, null, "B");

		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.refreshPositionAndAngles(a.getX() + 0.5, a.getY(), a.getZ() + 1.5, 0, 0);
		ObeliskService.clearCooldown(player);

		check(ObeliskService.teleport(player, a, infoA.id) == TeleportResult.SAME, "jumping to yourself is refused");
		TeleportResult first = ObeliskService.teleport(player, a, infoB.id);
		check(first == TeleportResult.OK, "teleport A -> B failed: " + first);
		check(player.getPos().isInRange(b.toCenterPos(), 2.5), "player should land next to B, is at " + player.getPos());
		check(ObeliskService.cooldownLeft(player) > 0, "a jump should start the cooldown");
		check(ObeliskService.teleport(player, b, infoA.id) == TeleportResult.COOLDOWN, "a second jump during the cooldown is refused");
		check(ObeliskService.teleport(player, a, infoB.id) == TeleportResult.NO_SOURCE, "you must stand at the source obelisk");

		ObeliskService.clearCooldown(player);
		check(ObeliskService.teleport(player, b, infoA.id) == TeleportResult.OK, "after the cooldown B -> A works");

		world.removePlayer(player, Entity.RemovalReason.DISCARDED);
		world.breakBlock(a, false);
		world.breakBlock(b, false);
		ctx.complete();
	}

	/** A floor of stone under a 3x3 around each spot, so players and pets have somewhere to land. */
	static void floor(ServerWorld world, BlockPos... spots) {
		for (BlockPos spot : spots) {
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					world.setBlockState(spot.add(x, -1, z), Blocks.STONE.getDefaultState());
				}
			}
		}
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "warmup", tickLimit = 300)
	public void warmupChannelsThenJumpsAndMovingCancelsIt(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(6, 2, 6));
		floor(world, a, b);
		setObelisk(world, a, Direction.SOUTH);
		setObelisk(world, b, Direction.NORTH);
		ObeliskService.register(world, a, null, "A");
		ObeliskInfo infoB = ObeliskService.register(world, b, null, "B");
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.refreshPositionAndAngles(a.getX() + 0.5, a.getY(), a.getZ() + 1.5, 0, 0);
		ObeliskService.clearCooldown(player);
		int warmup = ObeliskConfig.get().warmupSeconds * 20;

		// First try: the player steps away halfway through, so the jump is called off
		check(ObeliskService.startTeleport(player, a, infoB.id) == TeleportResult.OK, "the warmup should start");
		check(ObeliskService.isChanneling(player), "the player should be channelling");
		ctx.waitAndRun(warmup / 2, () -> {
			player.refreshPositionAndAngles(a.getX() + 0.5, a.getY(), a.getZ() + 2.5, 0, 0);
			ctx.waitAndRun(2, () -> {
				check(!ObeliskService.isChanneling(player), "moving should cancel the warmup");
				check(player.getPos().isInRange(a.toCenterPos(), 3), "the player should still be at A");
				// Second try: stand still until it fires
				player.refreshPositionAndAngles(a.getX() + 0.5, a.getY(), a.getZ() + 1.5, 0, 0);
				check(ObeliskService.startTeleport(player, a, infoB.id) == TeleportResult.OK, "the second warmup should start");
				ctx.waitAndRun(warmup + 5, () -> {
					check(player.getPos().isInRange(b.toCenterPos(), 2.5), "after the warmup the player should be at B, is at " + player.getPos());
					world.removePlayer(player, Entity.RemovalReason.DISCARDED);
					world.breakBlock(a, false);
					world.breakBlock(b, false);
					ctx.complete();
				});
			});
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "pets")
	public void petsAndLeashedAnimalsComeAlong(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos a = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		BlockPos b = ctx.getAbsolutePos(new BlockPos(6, 2, 6));
		floor(world, a, b);
		setObelisk(world, a, Direction.SOUTH);
		setObelisk(world, b, Direction.NORTH);
		ObeliskService.register(world, a, null, "A");
		ObeliskInfo infoB = ObeliskService.register(world, b, null, "B");
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		player.refreshPositionAndAngles(a.getX() + 0.5, a.getY(), a.getZ() + 1.5, 0, 0);
		ObeliskService.clearCooldown(player);

		WolfEntity wolf = EntityType.WOLF.create(world);
		wolf.refreshPositionAndAngles(a.getX() + 1.5, a.getY(), a.getZ() + 1.5, 0, 0);
		wolf.setOwner(player);
		wolf.setSitting(false);
		world.spawnEntity(wolf);
		WolfEntity sitter = EntityType.WOLF.create(world);
		sitter.refreshPositionAndAngles(a.getX() - 0.5, a.getY(), a.getZ() + 1.5, 0, 0);
		sitter.setOwner(player);
		sitter.setSitting(true);
		world.spawnEntity(sitter);
		CowEntity cow = EntityType.COW.create(world);
		cow.refreshPositionAndAngles(a.getX() + 0.5, a.getY(), a.getZ() + 2.5, 0, 0);
		world.spawnEntity(cow);
		cow.attachLeash(player, true);

		check(ObeliskService.teleport(player, a, infoB.id) == TeleportResult.OK, "teleport failed");
		check(wolf.getPos().isInRange(b.toCenterPos(), 3), "a following pet should come along, is at " + wolf.getPos());
		check(cow.getPos().isInRange(b.toCenterPos(), 3), "an animal on the lead should come along, is at " + cow.getPos());
		check(sitter.getPos().isInRange(a.toCenterPos(), 3), "a pet told to sit should stay");

		wolf.discard();
		sitter.discard();
		cow.discard();
		world.removePlayer(player, Entity.RemovalReason.DISCARDED);
		world.breakBlock(a, false);
		world.breakBlock(b, false);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void pistonsCannotMoveIt(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos lower = ctx.getAbsolutePos(new BlockPos(4, 2, 3));
		setObelisk(world, lower, Direction.NORTH);
		ObeliskService.register(world, lower, null, "Pushed");
		BlockPos piston = lower.west();
		world.setBlockState(piston, Blocks.PISTON.getDefaultState().with(FacingBlock.FACING, Direction.EAST));
		world.setBlockState(piston.west(), Blocks.REDSTONE_BLOCK.getDefaultState());
		ctx.waitAndRun(10, () -> {
			check(world.getBlockState(lower).isOf(EnderObelisks.OBELISK) && world.getBlockState(lower.up()).isOf(EnderObelisks.OBELISK),
					"a piston must not move or break the obelisk");
			check(ObeliskService.at(world, lower).isPresent(), "the obelisk should still be registered");
			world.breakBlock(lower, false);
			ctx.complete();
		});
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "player")
	public void strangersCannotBreakItEvenInCreative(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos lower = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
		setObelisk(world, lower, Direction.NORTH);
		ObeliskService.register(world, lower, null, "Not yours");
		ServerPlayerEntity stranger = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		boolean op = stranger.hasPermissionLevel(2);
		boolean broke = stranger.interactionManager.tryBreakBlock(lower.up());
		check(broke == op, "a creative stranger broke someone else's obelisk");
		check(world.getBlockState(lower).isOf(EnderObelisks.OBELISK) || op, "the obelisk should still stand");
		world.removePlayer(stranger, Entity.RemovalReason.DISCARDED);
		world.breakBlock(lower, false);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "player")
	public void rangeAndAccessRules(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = (ServerPlayerEntity) ctx.createMockCreativeServerPlayerInWorld();
		ObeliskService.clearCooldown(player);
		boolean op = player.hasPermissionLevel(2);
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
		UUID stranger = UUID.randomUUID();

		ObeliskInfo source = new ObeliskInfo(UUID.randomUUID(), world.getRegistryKey(), origin, player.getUuid(), "me", "src", true, 0);
		ObeliskInfo near = new ObeliskInfo(UUID.randomUUID(), world.getRegistryKey(), origin.add(400, 0, 0), stranger, "x", "near", true, 0);
		ObeliskInfo far = new ObeliskInfo(UUID.randomUUID(), world.getRegistryKey(), origin.add(700, 0, 0), stranger, "x", "far", true, 0);
		ObeliskInfo secret = new ObeliskInfo(UUID.randomUUID(), world.getRegistryKey(), origin.add(100, 0, 0), stranger, "x", "secret", false, 0);

		check(ObeliskService.check(player, source, near) == TeleportResult.OK, "400 blocks is within tier 0");
		check(ObeliskService.check(player, source, far) == TeleportResult.TOO_FAR, "700 blocks is beyond tier 0");
		source.tier = 1;
		check(ObeliskService.check(player, source, far) == TeleportResult.OK, "700 blocks is within tier 1 (1000)");
		check(ObeliskService.check(player, source, secret) == (op ? TeleportResult.OK : TeleportResult.NO_ACCESS),
				"someone else's private obelisk is closed");
		secret.friends.put(player.getUuid(), "me");
		check(ObeliskService.check(player, source, secret) == TeleportResult.OK, "a friend may use a private obelisk");
		ObeliskInfo nether = new ObeliskInfo(UUID.randomUUID(), World.NETHER, origin, stranger, "x", "nether", true, 4);
		check(ObeliskService.check(player, source, nether) == TeleportResult.OTHER_DIMENSION,
				"jumps between dimensions are off by default");

		// Breaking: only the owner (or an operator)
		BlockPos lower = ctx.getAbsolutePos(new BlockPos(3, 2, 3));
		setObelisk(world, lower, Direction.NORTH);
		ObeliskInfo owned = ObeliskService.register(world, lower, null, "Owned by nobody here");
		check(ObeliskBlock.mayBreak(world.getBlockState(lower), player, world, lower) == op, "a stranger may not break it");
		check(world.getBlockState(lower).calcBlockBreakingDelta(player, world, lower) == 0.0f || op,
				"mining someone else's obelisk makes no progress");
		check(owned.isPublic, "obelisks start public");

		world.removePlayer(player, Entity.RemovalReason.DISCARDED);
		world.breakBlock(lower, false);
		ctx.complete();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}
