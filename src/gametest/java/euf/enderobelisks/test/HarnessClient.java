package euf.enderobelisks.test;

import euf.enderobelisks.EnderObelisks;
import euf.enderobelisks.ObeliskInfo;
import euf.enderobelisks.ObeliskRegistry;
import euf.enderobelisks.ObeliskService;
import euf.enderobelisks.client.screen.ObeliskScreen;
import euf.enderobelisks.net.MenuData;
import euf.enderobelisks.net.ObeliskPackets;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * With -Dender_obelisks.harness=true (runHarness): plays the obelisk scenario in a real client on a generated world.
 * It places an obelisk with a real click, builds a few more around it, photographs the model, opens the menu,
 * teleports, checks range, cooldown, ownership and upgrades, and photographs the map. Writes harness-result.txt;
 * scripts/ci-harness.sh fails the build unless it says PASS and every screenshot exists.
 */
public class HarnessClient implements ClientModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("ender_obelisks-harness");

	private record Step(int waitAfter, boolean needsTerrain, Consumer<MinecraftClient> action) {}

	private final Deque<Step> steps = new ArrayDeque<>();
	private final List<String> results = new ArrayList<>();
	private int ticks;
	private int nextStepTick = 60;

	// Obelisk sites, worked out on the server in the first step
	private volatile BlockPos home;
	private volatile BlockPos village;
	private volatile BlockPos outpost;
	private volatile BlockPos hideout;
	private volatile BlockPos secret;

	@Override
	public void onInitializeClient() {
		if (!Boolean.getBoolean("ender_obelisks.harness")) {
			return;
		}
		step(20, false, c -> {
			commands(c, "gamerule sendCommandFeedback false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
					"gamerule doMobSpawning false", "time set 1000", "weather clear", "gamemode creative @a",
					"give @a ender_obelisks:obelisk 4");
			onServer(c, server -> {
				ServerWorld world = server.getOverworld();
				BlockPos spawn = world.getSpawnPos();
				home = prepareSite(world, spawn.getX() + 4, spawn.getZ());
				village = prepareSite(world, home.getX() + 300, home.getZ() + 40);
				outpost = prepareSite(world, home.getX() + 1400, home.getZ() - 300);
				hideout = prepareSite(world, home.getX() - 220, home.getZ() + 260);
				secret = prepareSite(world, home.getX() + 150, home.getZ() - 200);
				return null;
			});
		});
		// Stand west of the home site looking east, then place the obelisk with a real click on the ground
		step(40, true, c -> {
			c.player.getAbilities().flying = false;
			tp(c, Vec3d.ofBottomCenter(home).add(-2.5, 0, 0), Vec3d.ofCenter(home));
		});
		step(20, false, c -> check("obelisk_item_in_hotbar", select(c)));
		step(20, false, c -> {
			BlockPos ground = home.down();
			BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(ground).add(0, 0.5, 0), Direction.UP, ground, false);
			check("place_click_accepted", c.interactionManager.interactBlock(c.player, Hand.MAIN_HAND, hit).isAccepted());
		});
		step(20, false, c -> {
			UUID me = c.player.getUuid();
			check("placed_two_halves", onServer(c, s -> s.getOverworld().getBlockState(home).isOf(EnderObelisks.OBELISK)
					&& s.getOverworld().getBlockState(home.up()).isOf(EnderObelisks.OBELISK)));
			check("registered_with_owner", onServer(c, s -> ObeliskService.at(s.getOverworld(), home)
					.map(o -> o.owner.equals(me)).orElse(false)));
			// The rest of the network: a public village in range, a public outpost out of range,
			// my own private hideout, and someone else's private obelisk that must stay hidden
			onServer(c, s -> {
				ServerWorld world = s.getOverworld();
				build(world, village, null, "Деревня", "Steve", true);
				build(world, outpost, null, "Далёкий форпост", "Alex", true);
				build(world, hideout, c.player.getUuid(), "Мой тайник", c.player.getGameProfile().getName(), false);
				build(world, secret, UUID.randomUUID(), "Чужой секрет", "Notch", false);
				return null;
			});
		});

		// The model by day: the eye watches the player, so the pupil looks into the camera
		step(60, true, c -> {
			c.options.hudHidden = true;
			tp(c, Vec3d.ofBottomCenter(home).add(-3.2, 0, 0.6), Vec3d.ofBottomCenter(home).add(0, 1.2, 0));
		});
		step(40, true, c -> screenshot(c, "obelisk_front"));
		step(40, false, c -> fly(c, Vec3d.ofBottomCenter(home).add(-1.4, 1.0, -1.4), Vec3d.ofBottomCenter(home).add(0, 1.6, 0)));
		step(30, true, c -> screenshot(c, "obelisk_cage"));
		// Walk around it: the eye should have turned to follow
		step(40, false, c -> fly(c, Vec3d.ofBottomCenter(home).add(1.6, 1.4, 2.0), Vec3d.ofBottomCenter(home).add(0, 1.6, 0)));
		step(30, true, c -> screenshot(c, "obelisk_eye_follows"));
		step(30, false, c -> {
			commands(c, "time set 18000");
			fly(c, Vec3d.ofBottomCenter(home).add(-3.5, 0.4, 1.5), Vec3d.ofBottomCenter(home).add(0, 1.2, 0));
		});
		step(60, true, c -> screenshot(c, "obelisk_night"));

		// Open the menu with a real right click on the obelisk
		step(20, false, c -> {
			commands(c, "time set 1000");
			c.player.getAbilities().flying = false;
			c.player.sendAbilitiesUpdate();
			c.options.hudHidden = false;
			tp(c, Vec3d.ofBottomCenter(home).add(-2.5, 0, 0.5), Vec3d.ofCenter(home));
		});
		step(40, true, c -> rightClick(c, home));
		step(40, false, c -> {
			ObeliskScreen screen = screen(c);
			check("menu_opened", screen != null);
			if (screen != null) {
				MenuData data = screen.getData();
				check("menu_lists_visible_obelisks", names(data).containsAll(List.of("Деревня", "Далёкий форпост", "Мой тайник")));
				check("menu_hides_foreign_private", !names(data).contains("Чужой секрет"));
			}
			screenshot(c, "menu");
		});
		step(10, false, c -> selectByName(c, "Деревня"));
		step(20, false, c -> screenshot(c, "menu_selected"));

		// Out of range: the outpost is ~1430 blocks away, tier 0 reaches 500
		step(10, false, c -> {
			selectByName(c, "Далёкий форпост");
			ObeliskScreen screen = screen(c);
			if (screen != null) {
				screen.teleportSelected();
			}
		});
		step(40, false, c -> check("too_far_refused", c.player.getPos().isInRange(Vec3d.ofCenter(home), 6)));

		// A real jump to the village
		step(10, false, c -> selectByName(c, "Деревня"));
		step(5, false, c -> {
			ObeliskScreen screen = screen(c);
			if (screen != null) {
				screen.teleportSelected();
			}
		});
		step(60, true, c -> {
			check("teleported_to_village", c.player.getPos().isInRange(Vec3d.ofCenter(village), 4));
			c.options.hudHidden = true;
			screenshot(c, "arrived");
		});
		// Straight back is refused: the cooldown is running. The village is not mine, so no upgrades there either.
		step(10, false, c -> {
			c.options.hudHidden = false;
			look(c, Vec3d.ofCenter(village));
		});
		step(10, false, c -> rightClick(c, village));
		step(30, false, c -> {
			ObeliskScreen screen = screen(c);
			check("menu_at_village", screen != null);
			if (screen != null) {
				selectByName(c, nameAt(c, home));
				screen.teleportSelected();
			}
		});
		step(40, false, c -> {
			check("cooldown_blocks_jump", c.player.getPos().isInRange(Vec3d.ofCenter(village), 4));
			ObeliskScreen screen = screen(c);
			if (screen != null) {
				screen.upgrade();
			}
		});
		step(30, false, c -> {
			check("stranger_cannot_upgrade", onServer(c, s -> ObeliskService.at(s.getOverworld(), village).map(o -> o.tier == 0).orElse(false)));
			onServer(c, s -> {
				ObeliskService.clearCooldown(s.getPlayerManager().getPlayer(c.player.getUuid()));
				return null;
			});
		});
		step(10, false, c -> {
			ObeliskScreen screen = screen(c);
			if (screen != null) {
				selectByName(c, nameAt(c, home));
				screen.teleportSelected();
			}
		});
		step(60, true, c -> check("teleported_home", c.player.getPos().isInRange(Vec3d.ofCenter(home), 4)));

		// Home is mine: rename, toggle access, upgrade to the top tier (free in creative)
		step(10, false, c -> look(c, Vec3d.ofCenter(home)));
		step(10, false, c -> {
			sendRename(c, home, "Дом");
			sendSimple(c, ObeliskPackets.TOGGLE_PUBLIC, home);
		});
		step(20, false, c -> {
			check("renamed", onServer(c, s -> ObeliskService.at(s.getOverworld(), home).map(o -> o.name.equals("Дом")).orElse(false)));
			check("made_private", onServer(c, s -> ObeliskService.at(s.getOverworld(), home).map(o -> !o.isPublic).orElse(false)));
			sendSimple(c, ObeliskPackets.TOGGLE_PUBLIC, home);
			for (int i = 0; i < 5; i++) {
				sendSimple(c, ObeliskPackets.UPGRADE, home);
			}
		});
		step(30, false, c -> {
			check("upgraded_to_max", onServer(c, s -> ObeliskService.at(s.getOverworld(), home).map(o -> o.tier == 4 && o.isPublic).orElse(false)));
			if (c.currentScreen != null) {
				c.currentScreen.close();
			}
			c.options.hudHidden = true;
			tp(c, Vec3d.ofBottomCenter(home).add(-3.2, 0, 0.6), Vec3d.ofBottomCenter(home).add(0, 1.2, 0));
		});
		step(40, true, c -> screenshot(c, "obelisk_tier4"));
		step(10, false, c -> {
			c.options.hudHidden = false;
			rightClick(c, home);
		});
		step(30, false, c -> {
			ObeliskScreen screen = screen(c);
			check("menu_after_upgrade", screen != null && screen.getData().tier() == 4);
			if (screen != null) {
				screen.zoomToRange();
				selectByName(c, "Далёкий форпост");
			}
		});
		step(20, false, c -> screenshot(c, "menu_tier4"));
		step(10, false, c -> {
			ObeliskScreen screen = screen(c);
			if (screen != null) {
				screen.zoomTo(1.2);
				screen.select(null);
			}
		});
		step(20, false, c -> screenshot(c, "menu_zoomed"));
		step(40, false, this::finish);

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	// ---- server-side helpers (run on the integrated server thread) ----

	/** A 7x7 polished deepslate pad on the surface with room above it; returns the block above the pad's centre. */
	private static BlockPos prepareSite(ServerWorld world, int x, int z) {
		// Heightmaps of unloaded chunks read as the bottom of the world: generate the chunk first
		world.getChunk(x >> 4, z >> 4);
		BlockPos top = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				world.setBlockState(top.add(dx, -1, dz), Blocks.POLISHED_DEEPSLATE.getDefaultState());
				for (int dy = 0; dy < 4; dy++) {
					world.setBlockState(top.add(dx, dy, dz), Blocks.AIR.getDefaultState());
				}
			}
		}
		return top;
	}

	private static void build(ServerWorld world, BlockPos lower, UUID owner, String name, String ownerName, boolean isPublic) {
		ObeliskGameTests.setObelisk(world, lower, Direction.WEST);
		ObeliskInfo registered = ObeliskService.register(world, lower, null, name);
		ObeliskInfo info = owner == null ? registered
				: new ObeliskInfo(registered.id, registered.dimension, lower, owner, ownerName, name, isPublic, 0);
		info.ownerName = ownerName;
		info.isPublic = isPublic;
		ObeliskRegistry.get(world.getServer()).put(info);
	}

	private static <T> T onServer(MinecraftClient client, Function<MinecraftServer, T> action) {
		MinecraftServer server = client.getServer();
		return server.submit(() -> action.apply(server)).join();
	}

	private static void commands(MinecraftClient client, String... commands) {
		onServer(client, server -> {
			for (String command : commands) {
				server.getCommandManager().executeWithPrefix(server.getCommandSource(), command);
			}
			return null;
		});
	}

	// ---- client-side helpers ----

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
		if (next.needsTerrain() && !client.worldRenderer.isTerrainRenderComplete() && ticks < nextStepTick + 400) {
			return;
		}
		steps.poll();
		try {
			next.action().accept(client);
		} catch (RuntimeException e) {
			LOGGER.error("[harness] step failed", e);
			check("step_without_exception", false);
		}
		nextStepTick = ticks + next.waitAfter();
	}

	/** Teleports the player (server side) to {@code from}, looking at {@code at}. */
	private static void tp(MinecraftClient client, Vec3d from, Vec3d at) {
		float[] angles = angles(from.add(0, client.player.getStandingEyeHeight(), 0), at);
		commands(client, String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", from.x, from.y, from.z, angles[0], angles[1]));
	}

	/** Like tp, but hovering in creative flight so the camera can sit anywhere. */
	private static void fly(MinecraftClient client, Vec3d from, Vec3d at) {
		client.player.getAbilities().flying = true;
		client.player.sendAbilitiesUpdate();
		tp(client, from, at);
	}

	private static void look(MinecraftClient client, Vec3d at) {
		float[] angles = angles(client.player.getEyePos(), at);
		client.player.setYaw(angles[0]);
		client.player.setPitch(angles[1]);
	}

	private static float[] angles(Vec3d eye, Vec3d at) {
		Vec3d d = at.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
		return new float[] {yaw, pitch};
	}

	private static boolean select(MinecraftClient client) {
		for (int i = 0; i < 9; i++) {
			if (client.player.getInventory().getStack(i).isOf(EnderObelisks.OBELISK_ITEM)) {
				client.player.getInventory().selectedSlot = i;
				return true;
			}
		}
		return false;
	}

	private static void rightClick(MinecraftClient client, BlockPos lower) {
		Vec3d eye = client.player.getEyePos();
		Vec3d center = Vec3d.ofCenter(lower);
		Direction side = Direction.getFacing(eye.x - center.x, 0, eye.z - center.z);
		BlockHitResult hit = new BlockHitResult(center.add(Vec3d.of(side.getVector()).multiply(0.3)), side, lower, false);
		client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, hit);
	}

	private static ObeliskScreen screen(MinecraftClient client) {
		return client.currentScreen instanceof ObeliskScreen screen ? screen : null;
	}

	private static List<String> names(MenuData data) {
		return data.entries().stream().map(MenuData.Entry::name).toList();
	}

	private static String nameAt(MinecraftClient client, BlockPos pos) {
		return onServer(client, s -> ObeliskService.at(s.getOverworld(), pos).map(o -> o.name).orElse("?"));
	}

	private void selectByName(MinecraftClient client, String name) {
		ObeliskScreen screen = screen(client);
		Optional<MenuData.Entry> entry = screen == null ? Optional.empty()
				: screen.getData().entries().stream().filter(e -> e.name().equals(name)).findFirst();
		entry.ifPresent(e -> screen.select(e.id()));
		if (entry.isEmpty()) {
			LOGGER.error("[harness] no obelisk named {} in the menu", name);
		}
	}

	private static void sendRename(MinecraftClient client, BlockPos source, String name) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(source);
		buf.writeString(name);
		ClientPlayNetworking.send(ObeliskPackets.RENAME, buf);
	}

	private static void sendSimple(MinecraftClient client, Identifier channel, BlockPos source) {
		PacketByteBuf buf = PacketByteBufs.create();
		buf.writeBlockPos(source);
		ClientPlayNetworking.send(channel, buf);
	}

	private void screenshot(MinecraftClient client, String name) {
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name + ".png", client.getFramebuffer(), message -> {});
		LOGGER.info("[harness] screenshot {}.png", name);
	}

	private void finish(MinecraftClient client) {
		boolean pass = !results.isEmpty() && results.stream().allMatch(line -> line.endsWith(": OK"));
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
