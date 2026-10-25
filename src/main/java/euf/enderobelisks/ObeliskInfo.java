package euf.enderobelisks;

import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** One obelisk as the server registry knows it, loaded or not. {@code pos} is the lower half. */
public class ObeliskInfo {
	public final UUID id;
	public final RegistryKey<World> dimension;
	public final BlockPos pos;
	public final UUID owner;
	public String ownerName;
	public String name;
	public boolean isPublic;
	public int tier;

	public ObeliskInfo(UUID id, RegistryKey<World> dimension, BlockPos pos, UUID owner, String ownerName, String name,
			boolean isPublic, int tier) {
		this.id = id;
		this.dimension = dimension;
		this.pos = pos.toImmutable();
		this.owner = owner;
		this.ownerName = ownerName;
		this.name = name;
		this.isPublic = isPublic;
		this.tier = tier;
	}

	public int range() {
		return ObeliskTiers.range(tier);
	}

	public double distanceTo(ObeliskInfo other) {
		return Math.sqrt(pos.getSquaredDistance(other.pos));
	}

	public NbtCompound toNbt() {
		NbtCompound nbt = new NbtCompound();
		nbt.putUuid("id", id);
		nbt.putString("dim", dimension.getValue().toString());
		nbt.putLong("pos", pos.asLong());
		nbt.putUuid("owner", owner);
		nbt.putString("ownerName", ownerName);
		nbt.putString("name", name);
		nbt.putBoolean("public", isPublic);
		nbt.putInt("tier", tier);
		return nbt;
	}

	public static ObeliskInfo fromNbt(NbtCompound nbt) {
		return new ObeliskInfo(nbt.getUuid("id"),
				RegistryKey.of(RegistryKeys.WORLD, new Identifier(nbt.getString("dim"))),
				BlockPos.fromLong(nbt.getLong("pos")), nbt.getUuid("owner"), nbt.getString("ownerName"),
				nbt.getString("name"), nbt.getBoolean("public"), nbt.getInt("tier"));
	}
}
