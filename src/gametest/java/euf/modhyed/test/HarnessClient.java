package euf.modhyed.test;

import euf.modhyed.Modhyed;
import euf.modhyed.StatueBlock;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Item;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * With -Dmodhyed.harness=true (runHarness): joins the "harness" world and plays a queue of steps: scene setup by
 * command, real clicks, checks, and screenshots from several cameras, then quits. Writes harness-result.txt in the
 * run directory; scripts/ci-harness.sh fails the build unless it says PASS and every screenshot exists.
 */
public class HarnessClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("modhyed-harness");
	// Flat world: grass at y=-61, the player stands at y=-60 and looks south (+Z)
	private static final BlockPos SHOWCASE = new BlockPos(0, -60, 3);
	private static final BlockPos CLICK_BLOCK_GROUND = new BlockPos(2, -61, 3);
	private static final BlockPos CLICK_STATUE_GROUND = new BlockPos(3, -61, 2);
	private static final BlockPos STATUE_HAND = new BlockPos(-1, -59, 3);
	private static final BlockPos STATUE_CODEX = new BlockPos(1, -59, 3);

	/** waitAfter: ticks before the next step; needsTerrain: hold the step until the chunks are rendered. */
	private record Step(int waitAfter, boolean needsTerrain, Consumer<MinecraftClient> action) {}

	private final Deque<Step> steps = new ArrayDeque<>();
	private final List<String> results = new ArrayList<>();
	private int ticks;
	private int nextStepTick = 40;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("modhyed.harness")) {
			return;
		}
		step(40, false, c -> commands(c,
				"gamerule sendCommandFeedback false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
				"time set 6000", "weather clear", "gamemode creative @a", "tp @a 0.5 -60 0.5 0 5",
				"setblock -1 -60 3 modhyed:euf_block", "setblock 0 -60 3 modhyed:euf_block",
				"setblock 1 -60 3 modhyed:euf_block", "setblock 0 -59 3 modhyed:euf_block",
				"setblock -1 -59 3 modhyed:euf_statue[facing=north]",
				"setblock 1 -59 3 modhyed:euf_statue_codex[facing=north]",
				"give @a modhyed:euf_gem 16", "give @a modhyed:euf_block 8",
				"give @a modhyed:euf_statue 4", "give @a modhyed:euf_statue_codex 4",
				"summon item -0.5 -60 2 {Item:{id:\"modhyed:euf_gem\",Count:3b},PickupDelay:32767s}"));

		step(5, false, c -> {
			check("block_set_by_command", blockAt(c, SHOWCASE).isOf(Modhyed.EUF_BLOCK));
			check("statues_set_by_command", blockAt(c, STATUE_HAND).isOf(Modhyed.EUF_STATUE)
					&& blockAt(c, STATUE_CODEX).isOf(Modhyed.EUF_STATUE_CODEX));
			check("gem_in_inventory", c.player.getInventory().count(Modhyed.EUF_GEM) == 16);
			check("click_block_accepted", click(c, Modhyed.EUF_BLOCK_ITEM, CLICK_BLOCK_GROUND));
		});
		// The player looks south, so a statue placed by hand must face north, toward the player
		step(20, false, c -> check("click_statue_accepted", click(c, Modhyed.EUF_STATUE.asItem(), CLICK_STATUE_GROUND)));
		step(20, false, c -> {
			check("block_placed_by_click", blockAt(c, CLICK_BLOCK_GROUND.up()).isOf(Modhyed.EUF_BLOCK));
			BlockState placed = blockAt(c, CLICK_STATUE_GROUND.up());
			check("statue_placed_by_click", placed.isOf(Modhyed.EUF_STATUE));
			check("statue_faces_player", placed.isOf(Modhyed.EUF_STATUE) && placed.get(StatueBlock.FACING) == Direction.NORTH);
		});

		// Cameras. The overview keeps the HUD (hotbar shows the item models); the rest hide it like F1.
		step(40, true, c -> screenshot(c, "overview"));
		step(40, false, c -> {
			c.options.hudHidden = true;
			commands(c, "gamemode spectator @a", "tp @a 3.2 -59.3 1.3 50.8 13");
		});
		step(40, true, c -> screenshot(c, "angle"));
		step(40, false, c -> commands(c, "tp @a -0.5 -59.6 2.0 0 22"));
		step(40, true, c -> screenshot(c, "statue_hand"));
		step(40, false, c -> commands(c, "tp @a 1.5 -59.6 2.0 0 22"));
		step(40, true, c -> screenshot(c, "statue_codex"));
		// Screenshots are written on an IO thread: the last wait gives them time before quitting
		step(0, false, this::finish);

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void step(int waitAfter, boolean needsTerrain, Consumer<MinecraftClient> action) {
		steps.add(new Step(waitAfter, needsTerrain, action));
	}

	private void tick(MinecraftClient client) {
		if (client.player == null || client.world == null || client.getServer() == null || steps.isEmpty()) {
			return;
		}
		ticks++;
		if (ticks < nextStepTick) {
			return;
		}
		Step next = steps.peek();
		// Wait up to 20 s for the terrain, then go ahead anyway
		if (next.needsTerrain() && !client.worldRenderer.isTerrainRenderComplete() && ticks < nextStepTick + 400) {
			return;
		}
		steps.poll();
		next.action().accept(client);
		nextStepTick = ticks + next.waitAfter();
	}

	private static void commands(MinecraftClient client, String... commands) {
		IntegratedServer server = client.getServer();
		server.execute(() -> {
			for (String command : commands) {
				server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
			}
		});
	}

	private static BlockState blockAt(MinecraftClient client, BlockPos pos) {
		return client.getServer().getOverworld().getBlockState(pos);
	}

	/** Real click path: select the item in the hotbar, then click the top of the ground block (packet -> server). */
	private static boolean click(MinecraftClient client, Item item, BlockPos ground) {
		for (int i = 0; i < 9; i++) {
			if (client.player.getInventory().getStack(i).isOf(item)) {
				client.player.getInventory().selectedSlot = i;
				BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(ground).add(0, 0.5, 0), Direction.UP, ground, false);
				return client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit).isAccepted();
			}
		}
		LOGGER.error("[harness] {} is not in the hotbar", item);
		return false;
	}

	private void screenshot(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name + ".png", client.getFramebuffer(), message -> {});
		LOGGER.info("[harness] screenshot {}.png", name);
	}

	private void finish(MinecraftClient client) {
		boolean pass = results.stream().allMatch(line -> line.endsWith(": OK"));
		results.add("RESULT " + (pass ? "PASS" : "FAIL"));
		try {
			Files.write(client.runDirectory.toPath().resolve("harness-result.txt"), results, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.error("[harness] could not write the result file", e);
		}
		LOGGER.info("[harness] {}", results.get(results.size() - 1));
		client.scheduleStop();
	}

	private void check(String name, boolean ok) {
		String line = name + ": " + (ok ? "OK" : "FAIL");
		results.add(line);
		LOGGER.info("[harness] {}", line);
	}
}
