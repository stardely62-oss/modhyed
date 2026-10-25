package euf.enderobelisks;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.object.builder.v1.advancement.CriterionRegistry;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;

/**
 * Advancement triggers: "ender_obelisks:teleported" (optional "min_distance" in blocks) and "ender_obelisks:upgraded"
 * (optional "min_tier").
 */
public final class ObeliskCriteria {
	public static final NumberCriterion TELEPORTED = CriterionRegistry.register(new NumberCriterion(EnderObelisks.id("teleported"), "min_distance"));
	public static final NumberCriterion UPGRADED = CriterionRegistry.register(new NumberCriterion(EnderObelisks.id("upgraded"), "min_tier"));

	private ObeliskCriteria() {
	}

	public static void init() {
	}

	/** A trigger that fires with a number and matches when it is at least the condition's minimum. */
	public static class NumberCriterion extends AbstractCriterion<NumberCriterion.Conditions> {
		private final Identifier id;
		private final String field;

		public NumberCriterion(Identifier id, String field) {
			this.id = id;
			this.field = field;
		}

		@Override
		public Identifier getId() {
			return id;
		}

		@Override
		protected Conditions conditionsFromJson(JsonObject json, LootContextPredicate player, AdvancementEntityPredicateDeserializer deserializer) {
			return new Conditions(id, field, player, JsonHelper.getDouble(json, field, 0));
		}

		public void trigger(ServerPlayerEntity player, double value) {
			trigger(player, conditions -> value >= conditions.minimum);
		}

		public static class Conditions extends AbstractCriterionConditions {
			private final String field;
			private final double minimum;

			public Conditions(Identifier id, String field, LootContextPredicate player, double minimum) {
				super(id, player);
				this.field = field;
				this.minimum = minimum;
			}

			@Override
			public JsonObject toJson(AdvancementEntityPredicateSerializer serializer) {
				JsonObject json = super.toJson(serializer);
				json.addProperty(field, minimum);
				return json;
			}
		}
	}
}
