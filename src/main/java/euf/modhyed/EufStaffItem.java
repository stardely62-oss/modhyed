package euf.modhyed;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Right click: shoots a small fireball where the player looks, with a puff of purple sparks. */
public class EufStaffItem extends Item {
	public static final int COOLDOWN_TICKS = 15;

	public EufStaffItem(Settings settings) {
		super(settings);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (!world.isClient()) {
			Vec3d look = user.getRotationVec(1.0f);
			Vec3d muzzle = user.getEyePos().add(look.multiply(1.5));
			SmallFireballEntity fireball = new SmallFireballEntity(world, user, look.x, look.y, look.z);
			fireball.setPosition(muzzle);
			world.spawnEntity(fireball);
			((ServerWorld) world).spawnParticles(ParticleTypes.WITCH, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.02);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 0.8f, 1.3f);
		}
		user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
		user.swingHand(hand);
		return TypedActionResult.success(stack, world.isClient());
	}
}
