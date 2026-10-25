package euf.modhyed.test;

import euf.modhyed.EufGolemEntity;
import euf.modhyed.Modhyed;
import euf.modhyed.StatueBlock;
import euf.modhyed.StatueBlockEntity;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public class ModhyedGameTests implements FabricGameTest {
	@GameTest(templateName = EMPTY_STRUCTURE)
	public void blockPlacesAndDropsItself(TestContext ctx) {
		BlockPos pos = new BlockPos(1, 2, 1);
		ctx.setBlockState(pos, Modhyed.EUF_BLOCK);
		ctx.expectBlock(Modhyed.EUF_BLOCK, pos);

		ServerWorld world = ctx.getWorld();
		BlockPos abs = ctx.getAbsolutePos(pos);
		BlockState state = world.getBlockState(abs);
		check(state.getLuminance() == 7, "euf_block should glow at light level 7, got " + state.getLuminance());

		List<ItemStack> drops = Block.getDroppedStacks(state, world, abs, null, null, new ItemStack(Items.IRON_PICKAXE));
		check(drops.size() == 1 && drops.get(0).isOf(Modhyed.EUF_BLOCK_ITEM) && drops.get(0).getCount() == 1,
				"euf_block should drop exactly itself, got " + drops);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void recipesProduceTheRightItems(TestContext ctx) {
		expectRecipe(ctx, "euf_gem", new ItemStack(Modhyed.EUF_GEM, 2));
		expectRecipe(ctx, "euf_block", new ItemStack(Modhyed.EUF_BLOCK_ITEM));
		expectRecipe(ctx, "euf_gem_from_block", new ItemStack(Modhyed.EUF_GEM, 9));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void statuesHaveModelShapeFacingAndDrops(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		for (Block statue : List.of(Modhyed.EUF_STATUE, Modhyed.EUF_STATUE_CODEX)) {
			for (Direction facing : Direction.Type.HORIZONTAL) {
				BlockPos pos = new BlockPos(1, 2, 1);
				ctx.setBlockState(pos, statue.getDefaultState().with(StatueBlock.FACING, facing));
				BlockPos abs = ctx.getAbsolutePos(pos);
				BlockState state = world.getBlockState(abs);
				check(state.get(StatueBlock.FACING) == facing, statue + " lost its facing " + facing);

				// Not a full cube: pedestal 1..15 wide, head 4..12, both up to 16 high
				Box box = state.getOutlineShape(world, abs).getBoundingBox();
				check(box.minX == 1 / 16.0 && box.maxX == 15 / 16.0 && box.maxY == 1.0,
						statue + " outline should span the pedestal and head, got " + box);
				check(!state.isOpaqueFullCube(world, abs), statue + " must not hide its neighbours' faces");
				check(world.getBlockEntity(abs) instanceof StatueBlockEntity, statue + " needs its block entity (the spinning crystal)");

				List<ItemStack> drops = Block.getDroppedStacks(state, world, abs, null, null, new ItemStack(Items.IRON_PICKAXE));
				check(drops.size() == 1 && drops.get(0).isOf(statue.asItem()), statue + " should drop itself, got " + drops);
			}
		}
		expectRecipe(ctx, "euf_statue", new ItemStack(Modhyed.EUF_STATUE));
		expectRecipe(ctx, "euf_statue_codex", new ItemStack(Modhyed.EUF_STATUE_CODEX));
		expectRecipe(ctx, "euf_statue_from_codex", new ItemStack(Modhyed.EUF_STATUE));
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = "staff")
	public void staffShootsAFireballAndStartsItsCooldown(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos abs = ctx.getAbsolutePos(new BlockPos(1, 2, 1));
		PlayerEntity player = ctx.createMockCreativeServerPlayerInWorld();
		player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
		player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Modhyed.EUF_STAFF));

		Box area = new Box(abs).expand(6);
		int before = world.getEntitiesByClass(SmallFireballEntity.class, area, e -> true).size();
		player.getStackInHand(Hand.MAIN_HAND).use(world, player, Hand.MAIN_HAND);
		List<SmallFireballEntity> fireballs = world.getEntitiesByClass(SmallFireballEntity.class, area, e -> true);
		check(fireballs.size() == before + 1, "the staff should shoot one fireball, found " + (fireballs.size() - before));
		check(fireballs.stream().allMatch(f -> f.getOwner() == player), "the fireball should belong to the player");
		check(player.getItemCooldownManager().isCoolingDown(Modhyed.EUF_STAFF), "the staff should start its cooldown");

		fireballs.forEach(Entity::discard);
		world.removePlayer((ServerPlayerEntity) player, Entity.RemovalReason.DISCARDED);
		ctx.complete();
	}

	@GameTest(templateName = EMPTY_STRUCTURE)
	public void golemSpawnsAndLives(TestContext ctx) {
		EufGolemEntity golem = ctx.spawnEntity(Modhyed.EUF_GOLEM, new BlockPos(1, 2, 1));
		check(golem.getMaxHealth() == 20.0f, "golem max health should be 20, got " + golem.getMaxHealth());
		check(golem.getWidth() == 0.8f && golem.getHeight() == 1.4f, "golem should be 0.8 x 1.4, got " + golem.getDimensions(golem.getPose()));
		ctx.waitAndRun(40, () -> {
			check(golem.isAlive(), "golem died on its own");
			golem.discard();
			ctx.complete();
		});
	}

	private static void expectRecipe(TestContext ctx, String name, ItemStack expected) {
		ServerWorld world = ctx.getWorld();
		Recipe<?> recipe = world.getRecipeManager().get(Modhyed.id(name))
				.orElseThrow(() -> new GameTestException("missing recipe modhyed:" + name));
		ItemStack out = recipe.getOutput(world.getRegistryManager());
		check(ItemStack.areEqual(out, expected), "recipe modhyed:" + name + " gives " + out + ", expected " + expected);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new GameTestException(message);
		}
	}
}
