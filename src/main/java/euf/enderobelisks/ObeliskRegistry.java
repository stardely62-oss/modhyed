package euf.enderobelisks;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/** Every obelisk on the server, saved with the overworld, so the map can list obelisks in unloaded chunks. */
public class ObeliskRegistry extends PersistentState {
	private static final String KEY = EnderObelisks.MOD_ID;
	private final Map<UUID, ObeliskInfo> byId = new LinkedHashMap<>();

	public static ObeliskRegistry get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(ObeliskRegistry::fromNbt, ObeliskRegistry::new, KEY);
	}

	public Collection<ObeliskInfo> all() {
		return byId.values();
	}

	public Optional<ObeliskInfo> byId(UUID id) {
		return Optional.ofNullable(byId.get(id));
	}

	public Optional<ObeliskInfo> at(RegistryKey<World> dimension, BlockPos pos) {
		return byId.values().stream().filter(o -> o.dimension.equals(dimension) && o.pos.equals(pos)).findFirst();
	}

	public void put(ObeliskInfo info) {
		byId.put(info.id, info);
		markDirty();
	}

	public void remove(UUID id) {
		if (byId.remove(id) != null) {
			markDirty();
		}
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtList list = new NbtList();
		byId.values().forEach(o -> list.add(o.toNbt()));
		nbt.put("obelisks", list);
		return nbt;
	}

	private static ObeliskRegistry fromNbt(NbtCompound nbt) {
		ObeliskRegistry registry = new ObeliskRegistry();
		for (NbtElement element : nbt.getList("obelisks", NbtElement.COMPOUND_TYPE)) {
			ObeliskInfo info = ObeliskInfo.fromNbt((NbtCompound) element);
			registry.byId.put(info.id, info);
		}
		return registry;
	}
}
