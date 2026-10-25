package euf.modhyed;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Holds no data: it exists so the client can draw the statue's spinning crystal every frame. */
public class StatueBlockEntity extends BlockEntity {
	public StatueBlockEntity(BlockPos pos, BlockState state) {
		super(Modhyed.STATUE_BLOCK_ENTITY, pos, state);
	}
}
