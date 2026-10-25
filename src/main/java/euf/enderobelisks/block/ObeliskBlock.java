package euf.enderobelisks.block;

import euf.enderobelisks.ObeliskInfo;
import euf.enderobelisks.ObeliskService;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * Two blocks tall, like a door: the lower half carries the block entity and the registry entry, the upper half holds
 * the cage with the floating eye. Right click opens the teleport menu; only the owner (or an operator) can break it.
 */
public class ObeliskBlock extends Block implements BlockEntityProvider {
	public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
	public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;

	// Plinth and shaft below; tapered shaft and cage above (the cap above the cage is decoration only)
	private static final VoxelShape LOWER_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(0.5, 0, 0.5, 15.5, 3.5, 15.5),
			Block.createCuboidShape(3.5, 3.5, 3.5, 12.5, 16, 12.5));
	private static final VoxelShape UPPER_SHAPE = VoxelShapes.union(
			Block.createCuboidShape(4, 0, 4, 12, 7, 12),
			Block.createCuboidShape(3.5, 7, 3.5, 12.5, 16, 12.5));

	public ObeliskBlock(Settings settings) {
		super(settings);
		setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(HALF, DoubleBlockHalf.LOWER));
	}

	public static BlockPos lowerPos(BlockState state, BlockPos pos) {
		return state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(FACING, HALF);
	}

	@Nullable
	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return state.get(HALF) == DoubleBlockHalf.LOWER ? new ObeliskBlockEntity(pos, state) : null;
	}

	@Nullable
	@Override
	public BlockState getPlacementState(ItemPlacementContext ctx) {
		BlockPos pos = ctx.getBlockPos();
		World world = ctx.getWorld();
		if (pos.getY() < world.getTopY() - 1 && world.getBlockState(pos.up()).canReplace(ctx)) {
			return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
		}
		return null;
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), Block.NOTIFY_ALL);
		if (world instanceof ServerWorld serverWorld) {
			String name = stack.hasCustomName() ? stack.getName().getString() : null;
			ObeliskService.register(serverWorld, pos, placer instanceof PlayerEntity player ? player : null, name);
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
			WorldAccess world, BlockPos pos, BlockPos neighborPos) {
		DoubleBlockHalf half = state.get(HALF);
		// The two halves live and die together
		if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
			boolean partnerOk = neighborState.isOf(this) && neighborState.get(HALF) != half;
			return partnerOk ? state : Blocks.AIR.getDefaultState();
		}
		return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
	}

	@Override
	@SuppressWarnings("deprecation")
	public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		if (state.get(HALF) == DoubleBlockHalf.UPPER) {
			BlockState below = world.getBlockState(pos.down());
			return below.isOf(this) && below.get(HALF) == DoubleBlockHalf.LOWER;
		}
		return super.canPlaceAt(state, world, pos);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		if (!state.isOf(newState.getBlock()) && state.get(HALF) == DoubleBlockHalf.LOWER && world instanceof ServerWorld serverWorld) {
			ObeliskService.unregister(serverWorld, pos);
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	@Override
	@SuppressWarnings("deprecation")
	public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
		return mayBreak(state, player, world, pos) ? super.calcBlockBreakingDelta(state, player, world, pos) : 0.0f;
	}

	/** Owner or operator only. The client may not know the owner, so it digs as usual and the server decides. */
	public static boolean mayBreak(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
		if (!(world instanceof ServerWorld serverWorld)) {
			return true;
		}
		Optional<ObeliskInfo> info = ObeliskService.at(serverWorld, lowerPos(state, pos));
		return info.isEmpty() || ObeliskService.isOwner(info.get(), player);
	}

	@Override
	@SuppressWarnings("deprecation")
	public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
		if (world.isClient()) {
			return ActionResult.SUCCESS;
		}
		ServerWorld serverWorld = (ServerWorld) world;
		ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
		BlockPos lower = lowerPos(state, pos);
		// An obelisk set by command has no entry yet: the first player to use it claims it
		ObeliskInfo info = ObeliskService.at(serverWorld, lower).orElseGet(() -> ObeliskService.register(serverWorld, lower, player, null));
		if (!ObeliskService.canUse(info, player)) {
			player.sendMessage(Text.translatable("ender_obelisks.message.private", info.ownerName), true);
			return ActionResult.CONSUME;
		}
		ObeliskService.openMenu(serverPlayer, lower);
		return ActionResult.CONSUME;
	}

	@Override
	@SuppressWarnings("deprecation")
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPE : UPPER_SHAPE;
	}

	/** Idle animation: pale enchanting glyphs drawn into the cage from all sides. */
	@Override
	public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
		if (state.get(HALF) != DoubleBlockHalf.UPPER) {
			return;
		}
		for (int i = 0; i < 3; i++) {
			// ENCHANT particles fly from (pos + velocity) back to pos, so this pulls them into the eye
			world.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
					(random.nextDouble() - 0.5) * 2.0, (random.nextDouble() - 0.3) * 1.5, (random.nextDouble() - 0.5) * 2.0);
		}
	}

	@Override
	@SuppressWarnings("deprecation")
	public BlockState rotate(BlockState state, BlockRotation rotation) {
		return state.with(FACING, rotation.rotate(state.get(FACING)));
	}

	@Override
	@SuppressWarnings("deprecation")
	public BlockState mirror(BlockState state, BlockMirror mirror) {
		return state.rotate(mirror.getRotation(state.get(FACING)));
	}
}
