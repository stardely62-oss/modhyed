package euf.modhyed.test;

import euf.modhyed.Modhyed;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Recipe;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.GameTestException;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;

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
