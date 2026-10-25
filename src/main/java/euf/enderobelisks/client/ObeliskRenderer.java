package euf.enderobelisks.client;

import euf.enderobelisks.EnderObelisks;
import euf.enderobelisks.ObeliskTiers;
import euf.enderobelisks.block.ObeliskBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * The animated parts of an obelisk, drawn from the lower half's block entity: the ender eye floating in the cage
 * (it turns to watch the nearest player, or idly spins), one glowing rune ring per tier, and the name plate.
 */
public class ObeliskRenderer implements BlockEntityRenderer<ObeliskBlockEntity> {
	public static final Identifier EYE_MODEL = EnderObelisks.id("block/obelisk_eye");
	public static final Identifier[] RUNE_MODELS = new Identifier[ObeliskTiers.MAX_TIER + 1];
	private static final double EYE_Y = 26 / 16.0;
	private static final double WATCH_DISTANCE = 12.0;
	private static final double NAME_DISTANCE = 8.0;
	private static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

	static {
		for (int i = 0; i < RUNE_MODELS.length; i++) {
			RUNE_MODELS[i] = EnderObelisks.id("block/obelisk_runes_" + i);
		}
	}

	private final BlockEntityRenderDispatcher dispatcher;
	private final TextRenderer textRenderer;

	public ObeliskRenderer(BlockEntityRendererFactory.Context context) {
		this.dispatcher = context.getRenderDispatcher();
		this.textRenderer = context.getTextRenderer();
	}

	@Override
	public void render(ObeliskBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
			int light, int overlay) {
		MinecraftClient client = MinecraftClient.getInstance();
		BlockState state = entity.getCachedState();
		BakedModelManager models = client.getBakedModelManager();
		BlockModelRenderer renderer = client.getBlockRenderManager().getModelRenderer();
		VertexConsumer cutout = vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout());
		float time = entity.getWorld().getTime() + tickDelta;

		// Rune rings: tier 0 lights one, each upgrade one more; full bright so they glow at night
		for (int ring = 0; ring <= Math.min(entity.getTier(), ObeliskTiers.MAX_TIER); ring++) {
			renderer.render(matrices.peek(), cutout, state, models.getModel(RUNE_MODELS[ring]), 1.0f, 1.0f, 1.0f, FULL_BRIGHT,
					OverlayTexture.DEFAULT_UV);
		}

		updateEye(entity, time);
		matrices.push();
		matrices.translate(0.5, EYE_Y + MathHelper.sin(time * 0.08f) * 0.03, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(entity.eyeYaw));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(entity.eyePitch));
		matrices.translate(-0.5, -0.5, -0.5);
		renderer.render(matrices.peek(), cutout, state, models.getModel(EYE_MODEL), 1.0f, 1.0f, 1.0f, FULL_BRIGHT,
				OverlayTexture.DEFAULT_UV);
		matrices.pop();

		String name = entity.getName();
		Vec3d center = Vec3d.ofCenter(entity.getPos()).add(0, 1, 0);
		if (!name.isEmpty() && client.player != null && client.player.getPos().isInRange(center, NAME_DISTANCE)) {
			matrices.push();
			matrices.translate(0.5, 2.5, 0.5);
			matrices.multiply(dispatcher.camera.getRotation());
			matrices.scale(-0.025f, -0.025f, 0.025f);
			Matrix4f matrix = matrices.peek().getPositionMatrix();
			int background = (int) (client.options.getTextBackgroundOpacity(0.25f) * 255.0f) << 24;
			textRenderer.draw(name, -textRenderer.getWidth(name) / 2.0f, 0, 0xFFE6CCFF, false, matrix, vertexConsumers,
					TextRenderer.TextLayerType.NORMAL, background, FULL_BRIGHT);
			matrices.pop();
		}
	}

	/** The pupil (model front, -Z) turns smoothly toward the nearest player's eyes; with nobody near it idly spins. */
	private static void updateEye(ObeliskBlockEntity entity, float time) {
		Vec3d eye = Vec3d.ofBottomCenter(entity.getPos()).add(0, EYE_Y, 0);
		PlayerEntity player = entity.getWorld().getClosestPlayer(eye.x, eye.y, eye.z, WATCH_DISTANCE, false);
		float targetYaw;
		float targetPitch;
		if (player != null) {
			Vec3d d = player.getEyePos().subtract(eye);
			targetYaw = (float) Math.toDegrees(Math.atan2(-d.x, -d.z));
			targetPitch = (float) Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		} else {
			targetYaw = time * 2.0f;
			targetPitch = MathHelper.sin(time * 0.05f) * 10.0f;
		}
		entity.eyeYaw += MathHelper.wrapDegrees(targetYaw - entity.eyeYaw) * 0.12f;
		entity.eyePitch += (targetPitch - entity.eyePitch) * 0.12f;
	}

	@Override
	public boolean rendersOutsideBoundingBox(ObeliskBlockEntity entity) {
		return true;
	}
}
