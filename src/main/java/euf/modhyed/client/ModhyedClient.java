package euf.modhyed.client;

import euf.modhyed.Modhyed;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.entity.player.PlayerEntity;

public class ModhyedClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(EufGolemRenderer.LAYER, EufGolemModel::getTexturedModelData);
		EntityRendererRegistry.register(Modhyed.EUF_GOLEM, EufGolemRenderer::new);
		BlockEntityRendererFactories.register(Modhyed.STATUE_BLOCK_ENTITY, StatueCrystalRenderer::new);
		// The crystal model is not used by any blockstate, so it has to be loaded explicitly
		ModelLoadingPlugin.register(context -> context.addModels(StatueCrystalRenderer.MODEL));
		// 0..1 while the staff recharges: the item model switches to its dim "cooldown" variant (models/item/euf_staff.json)
		FabricModelPredicateProviderRegistry.register(Modhyed.EUF_STAFF, Modhyed.id("cooldown"), (stack, world, entity, seed) ->
				entity instanceof PlayerEntity player ? player.getItemCooldownManager().getCooldownProgress(stack.getItem(), 0.0f) : 0.0f);
	}
}
