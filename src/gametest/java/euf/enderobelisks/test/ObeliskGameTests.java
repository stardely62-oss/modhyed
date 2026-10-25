package euf.enderobelisks.test;

import euf.enderobelisks.EnderObelisks;
import euf.enderobelisks.ObeliskInfo;
import euf.enderobelisks.ObeliskService;
import euf.enderobelisks.ObeliskService.TeleportResult;
import euf.enderobelisks.ObeliskTiers;
import euf.enderobelisks.block.ObeliskBlock;
import euf.enderobelisks.block.ObeliskBlockEntity;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

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
