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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The animated parts of an obelisk, drawn from the lower half's block entity: the ender eye floating in the cage
 * (it turns to watch the nearest player, or idly looks around), blood running down the shaft (more with every
 * tier), and the name plate.
 */
public class ObeliskRenderer implements BlockEntityRenderer<ObeliskBlockEntity> {
	public static final Identifier EYE_MODEL = EnderObelisks.id("block/obelisk_eye");
	/** Blood overlays for tiers 1..4: a tier-0 obelisk is clean. */
	public static final Identifier[] BLOOD_MODELS = new Identifier[ObeliskTiers.MAX_TIER];
	private static final double EYE_Y = 26 / 16.0;
	private static final float EYE_SCALE = 1.3f;
	private static final double WATCH_DISTANCE = 12.0;
	private static final double NAME_DISTANCE = 8.0;
	private static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

	static {
		for (int i = 0; i < BLOOD_MODELS.length; i++) {
			BLOOD_MODELS[i] = EnderObelisks.id("block/obelisk_blood_" + (i + 1));
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

		// Blood: each upgrade adds another layer of drips. Lit like the world around it, it does not glow.
		for (int level = 1; level <= Math.min(entity.getTier(), ObeliskTiers.MAX_TIER); level++) {
			renderer.render(matrices.peek(), cutout, state, models.getModel(BLOOD_MODELS[level - 1]), 1.0f, 1.0f, 1.0f, light,
					OverlayTexture.DEFAULT_UV);
		}

		updateEye(entity, time);
		matrices.push();
		matrices.translate(0.5, EYE_Y + MathHelper.sin(time * 0.08f) * 0.03, 0.5);
		// Turn the model's front (-Z) onto the look direction, keeping the slit pupil upright
		Vector3f dir = entity.eyeDirection;
		matrices.multiply(new Quaternionf().lookAlong(dir.x, dir.y, dir.z, 0, 1, 0).invert());
		// A little bigger than the gap between the bars: the eye is wedged into its cage
		matrices.scale(EYE_SCALE, EYE_SCALE, EYE_SCALE);
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
			textRenderer.draw(name, -textRenderer.getWidth(name) / 2.0f, 0, 0xFFE6E8EA, false, matrix, vertexConsumers,
					TextRenderer.TextLayerType.NORMAL, background, FULL_BRIGHT);
			matrices.pop();
		}
	}

	/** The pupil turns smoothly toward the nearest player's eyes; with nobody near it slowly looks around. */
	private static void updateEye(ObeliskBlockEntity entity, float time) {
		Vec3d eye = Vec3d.ofBottomCenter(entity.getPos()).add(0, EYE_Y, 0);
		PlayerEntity player = entity.getWorld().getClosestPlayer(eye.x, eye.y, eye.z, WATCH_DISTANCE, false);
		Vector3f target;
		if (player != null) {
			Vec3d d = player.getEyePos().subtract(eye);
			target = new Vector3f((float) d.x, (float) d.y, (float) d.z);
		} else {
			target = new Vector3f(MathHelper.sin(time * 0.035f), MathHelper.sin(time * 0.05f) * 0.2f, -MathHelper.cos(time * 0.035f));
		}
		if (target.lengthSquared() < 1.0e-4f) {
			return;
		}
		target.normalize();
		// Never straight up or down, where "upright" is undefined
		target.y = MathHelper.clamp(target.y, -0.9f, 0.9f);
		entity.eyeDirection.lerp(target.normalize(), 0.12f).normalize();
	}

	@Override
	public boolean rendersOutsideBoundingBox(ObeliskBlockEntity entity) {
		return true;
	}
}
