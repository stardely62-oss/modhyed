package euf.enderobelisks.block;

import euf.enderobelisks.EnderObelisks;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Lives in the lower half. Keeps a copy of the registry entry's name and tier for rendering (name plate, lit rune
 * rings) and, on the client, the eye's current look direction.
 */
public class ObeliskBlockEntity extends BlockEntity {
	@Nullable
	private UUID obeliskId;
	private String name = "";
	private int tier;
	private int color;

	/** Client-side animation state, never saved: where the eye's pupil currently points (unit vector). */
	public final Vector3f eyeDirection = new Vector3f(0, 0, -1);

	public ObeliskBlockEntity(BlockPos pos, BlockState state) {
		super(EnderObelisks.OBELISK_BLOCK_ENTITY, pos, state);
	}

	public void update(UUID id, String name, int tier, int color) {
		this.obeliskId = id;
		this.name = name;
		this.tier = tier;
		this.color = color;
		markDirty();
		if (world != null) {
			world.updateListeners(pos, getCachedState(), getCachedState(), 3);
		}
	}

	@Nullable
	public UUID getObeliskId() {
		return obeliskId;
	}

	public String getName() {
		return name;
	}

	public int getTier() {
		return tier;
	}

	public int getColor() {
		return color;
	}

	@Override
	protected void writeNbt(NbtCompound nbt) {
		super.writeNbt(nbt);
		if (obeliskId != null) {
			nbt.putUuid("id", obeliskId);
		}
		nbt.putString("name", name);
		nbt.putInt("tier", tier);
		nbt.putInt("color", color);
	}

	@Override
	public void readNbt(NbtCompound nbt) {
		super.readNbt(nbt);
		obeliskId = nbt.containsUuid("id") ? nbt.getUuid("id") : null;
		name = nbt.getString("name");
		tier = nbt.getInt("tier");
		color = nbt.getInt("color");
	}

	@Override
	public NbtCompound toInitialChunkDataNbt() {
		return createNbt();
	}

	@Override
	public Packet<ClientPlayPacketListener> toUpdatePacket() {
		return BlockEntityUpdateS2CPacket.create(this);
	}
}
