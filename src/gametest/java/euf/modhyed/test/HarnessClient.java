package euf.modhyed.test;

import euf.modhyed.Modhyed;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * With -Dmodhyed.harness=true (runHarness): joins the "harness" world, sets up a small scene by command,
 * places a block with a real click, checks the results, saves screenshots/harness.png and quits.
 * Writes harness-result.txt in the run directory; scripts/ci-harness.sh fails the build on anything but PASS.
 */
public class HarnessClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("modhyed-harness");
	// Flat world: grass at y=-61, the player stands at y=-60 and looks south (+Z)
	private static final BlockPos SHOWCASE = new BlockPos(0, -60, 3);
	private static final BlockPos CLICK_GROUND = new BlockPos(2, -61, 3);

	private final List<String> results = new ArrayList<>();
	private int ticks;
	private int shotTick = -1;

	@Override
	public void onInitializeClient() {
		if (Boolean.getBoolean("modhyed.harness")) {
			ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		}
	}

	private void tick(MinecraftClient client) {
		IntegratedServer server = client.getServer();
		if (client.player == null || client.world == null || server == null) {
			return;
		}
		ticks++;

		if (ticks == 40) {
			server.execute(() -> {
				for (String command : List.of(
						"gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set 6000", "weather clear",
						"gamemode creative @a", "tp @a 0.5 -60 0.5 0 20",
						"setblock -1 -60 3 modhyed:euf_block", "setblock 0 -60 3 modhyed:euf_block",
						"setblock 1 -60 3 modhyed:euf_block", "setblock 0 -59 3 modhyed:euf_block",
						"give @a modhyed:euf_gem 16", "give @a modhyed:euf_block 8",
						"summon item -0.5 -60 2 {Item:{id:\"modhyed:euf_gem\",Count:3b},PickupDelay:32767s}")) {
					server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
				}
			});
		}

		if (ticks == 80) {
			check("block_set_by_command", server.getOverworld().getBlockState(SHOWCASE).isOf(Modhyed.EUF_BLOCK));
			check("gem_in_inventory", client.player.getInventory().count(Modhyed.EUF_GEM) == 16);

			// Real click path: client interaction manager -> packet -> server use logic
			int slot = -1;
			for (int i = 0; i < 9; i++) {
				if (client.player.getInventory().getStack(i).isOf(Modhyed.EUF_BLOCK_ITEM)) {
					slot = i;
				}
			}
			check("block_item_in_hotbar", slot >= 0);
			if (slot >= 0) {
				client.player.getInventory().selectedSlot = slot;
				BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(CLICK_GROUND).add(0, 0.5, 0), Direction.UP, CLICK_GROUND, false);
				ActionResult result = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
				check("click_accepted", result.isAccepted());
			}
		}

		if (ticks == 100) {
			check("block_placed_by_click", server.getOverworld().getBlockState(CLICK_GROUND.up()).isOf(Modhyed.EUF_BLOCK));
		}

		// Screenshot once the terrain is built (or give up waiting after 30 s)
		if (shotTick < 0 && ticks >= 140 && (client.worldRenderer.isTerrainRenderComplete() || ticks > 600)) {
			ScreenshotRecorder.saveScreenshot(client.runDirectory, "harness.png", client.getFramebuffer(), message -> {});
			shotTick = ticks;
		}

		// The screenshot is written on an IO thread: give it a moment, then report and quit
		if (shotTick > 0 && ticks == shotTick + 40) {
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
	}

	private void check(String name, boolean ok) {
		String line = name + ": " + (ok ? "OK" : "FAIL");
		results.add(line);
		LOGGER.info("[harness] {}", line);
	}
}
