package euf.enderobelisks.block;

import euf.enderobelisks.EnderObelisks;
import euf.enderobelisks.ObeliskCriteria;
import euf.enderobelisks.ObeliskService;
import euf.enderobelisks.ObeliskSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The broken stump of an obelisk, found in ruins around the world. Right click it with a time shard and it rises
 * again as a working obelisk that belongs to you.
 */
public class RuinedObeliskBlock extends Block {
	private static final VoxelShape SHAPE = VoxelShapes.union(
			Block.createCuboidShape(0.5, 0, 0.5, 15.5, 2.5, 15.5),
			Block.createCuboidShape(2.5, 2.5, 2.5, 13.5, 4, 13.5),
			Block.createCuboidShape(5, 4, 5, 11, 10, 11));

	public RuinedObeliskBlock(Settings settings) {
		super(settings);
	}

	@Override
	@SuppressWarnings("deprecation")
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		ItemStack held = player.getStackInHand(hand);
		if (!held.isOf(EnderObelisks.TIME_SHARD)) {
			player.sendMessage(Text.translatable("ender_obelisks.ruin.hint"), true);
			return ActionResult.CONSUME;
		}
		restore((ServerWorld) world, pos, player, held);
		return ActionResult.CONSUME;
	}

	/** Turns the ruin back into an obelisk owned by the player, using up one time shard (none in creative). */
	public static boolean restore(ServerWorld world, BlockPos pos, PlayerEntity player, ItemStack shard) {
		if (!world.getBlockState(pos.up()).isReplaceable()) {
			player.sendMessage(Text.translatable("ender_obelisks.ruin.no_space"), true);
			return false;
		}
		Direction facing = player.getHorizontalFacing().getOpposite();
		BlockState obelisk = EnderObelisks.OBELISK.getDefaultState().with(ObeliskBlock.FACING, facing);
		world.setBlockState(pos.up(), obelisk.with(ObeliskBlock.HALF, DoubleBlockHalf.UPPER));
		world.setBlockState(pos, obelisk.with(ObeliskBlock.HALF, DoubleBlockHalf.LOWER));
		ObeliskService.register(world, pos, player, null);
		if (!player.getAbilities().creativeMode) {
			shard.decrement(1);
		}
		world.spawnParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 80, 0.4, 0.9, 0.4, 0.05);
		world.playSound(null, pos, ObeliskSounds.UPGRADE, SoundCategory.BLOCKS, 1.0f, 0.8f);
		world.playSound(null, pos, ObeliskSounds.PLACE, SoundCategory.BLOCKS, 1.0f, 0.7f);
		player.sendMessage(Text.translatable("ender_obelisks.ruin.restored"), true);
		if (player instanceof ServerPlayerEntity serverPlayer) {
			ObeliskCriteria.RESTORED.trigger(serverPlayer, 1);
		}
		return true;
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	/** Ash drifts up from the broken stump. */
	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (random.nextInt(3) == 0) {
			world.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.65,
					pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
		}
	}
}
