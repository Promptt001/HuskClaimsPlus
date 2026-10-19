/*
 * This file is part of HuskClaims, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.william278.huskclaims.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.william278.huskclaims.FabricHuskClaims;
import net.william278.huskclaims.api.FabricHuskClaimsAPI;
import net.william278.huskclaims.gui.GuiItemMarker;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.position.World;
import net.william278.huskclaims.highlighter.Highlightable;
import net.william278.huskclaims.user.User;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Level;
import java.util.List;

import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;

/**
 * End-to-end checks for HuskClaims on the integrated (singleplayer / open-to-LAN) server.
 *
 * <p>Runs two worlds in one JVM and asserts that each one gets its own working session:
 * the session is active, the commands are registered, the data lives inside the world
 * folder, and the second world starts clean. It then reopens the first world's save and
 * asserts its claims are still there.
 *
 * <p>{@code host-permission-policy} measures the host player's actual vanilla permission
 * level on the integrated server with Allow Commands on and the world published to LAN.
 * 26.2 has no FakePlayer, so the host player is what gets measured. Signatures are
 * checked against the merged deobfuscated 26.2 classes: {@code publishServer} takes a
 * {@code MultiplayerScope} first argument.
 *
 * <p>{@code admin-and-owner-claims} proves claim ownership and protection without a second
 * account: an admin claim is ownerless by construction, so it must deny the host's
 * BLOCK_BREAK, while a claim the host owns in the same world must allow it. Together they
 * prove the create, store and protect pipeline.
 *
 * <p>{@code claim-edge-protection} drives the server-side player actions a real client
 * triggers ({@code gameMode.useItemOn} with a real {@code BlockHitResult}, and
 * {@code gameMode.destroyBlock}) at the exact boundary coordinates of a claim: on each of
 * the four sides the first block outside the claim must be breakable and buildable, and the
 * claim's own edge must stay protected. {@code claim-outline-on-edge} asserts the outline
 * highlighter draws on the claim's own corner blocks.
 *
 * <p>{@code client-left-click-safe} looks at a nearby block, presses attack, and asserts the
 * client thread did not crash. {@code claim-menu-renders}, {@code crafting-not-tainted-by-tool}
 * and {@code furnace-refuses-tool} cover the menu and the handed-out claim tool: the menu
 * renders and leaves nothing behind, crafting is not tainted by a tool in the inventory,
 * and a handed-out tool cannot be moved into a furnace by hand or by shift-click.
 *
 * <p>Every check logs either {@code PASS <name> - <what>} or {@code FAIL <name> - <reason>}
 * with the prefix {@code [HuskClaims Gametest]}, so a single grep over the log tells a
 * release engineer which checks ran and which failed.
 */
public final class IntegratedServerGameTest implements FabricClientGameTest {

    @Override
    public void runTest(@NotNull ClientGameTestContext context) {
        final FabricHuskClaims plugin = huskClaims();
        plugin.log(Level.INFO, "[HuskClaims Gametest] starting the world and session suite (two worlds, one JVM)");

        // host-permission-policy runs FIRST: the host's measured vanilla permission
        // level on the integrated server with Allow Commands on, published to LAN.
        try (TestSingleplayerContext lanWorld = context.worldBuilder()
                .adjustSettings(state -> state.setAllowCommands(true))
                .create()) {
            //#if MC>=260300
            //$$ // 26.3 gametest-api 6.x: getClientLevel() moved to getConnection() (TestServerConnection)
            //$$ lanWorld.getConnection().waitForChunksRender();
            //#else
            lanWorld.getClientLevel().waitForChunksRender();
            //#endif
            lanWorld.getServer().runOnServer(server -> {
                final IntegratedServer integrated = (IntegratedServer) server;
                //#if MC>=260300
                //$$ // 26.3: publishServer dropped the GameType parameter (scope, enableCmds, port)
                //$$ final boolean published = integrated.publishServer(
                //$$         MinecraftServer.MultiplayerScope.LAN, true, 25565);
                //#else
                final boolean published = integrated.publishServer(
                        MinecraftServer.MultiplayerScope.LAN, GameType.CREATIVE, true, 25565);
                //#endif
                require(published, "world opened to LAN with commands allowed");

                final ServerPlayer host = server.getPlayerList().getPlayers().isEmpty()
                        ? null : server.getPlayerList().getPlayers().getFirst();
                require(host != null, "host player is online on the integrated server");
                final PermissionLevel hostLevel = server.getProfilePermissions(
                        new net.minecraft.server.players.NameAndId(host.getGameProfile())).level();
                plugin.log(Level.INFO, "[HuskClaims Gametest] host-permission-policy measured host vanilla level (cheats ON at creation, commands allowed, LAN published): "
                        + hostLevel + " (id " + hostLevel.id() + ")");

                // Does the claim-tool gate pass for this host? The world owner must get
                // ALLOWED; the verdict is logged so the evidence log carries the measured
                // answer rather than an assumption.
                final net.william278.huskclaims.user.OnlineUser hostUser =
                        plugin.getOnlineUser(host.getGameProfile().id());
                final boolean claimAllowed = hostUser != null
                        && hostUser.hasPermission("huskclaims.claim");
                require(claimAllowed, "host-permission-policy: the host's claim verdict is ALLOWED (the world owner keeps operator rights, and the claim tool works)");
                plugin.log(Level.INFO, "[HuskClaims Gametest] PASS host-permission-policy - the host's claim verdict is ALLOWED");

                integrated.unpublishServer();
            });
        }

        // session-starts + admin-and-owner-claims + client-left-click-safe, world 1: session active, data in world folder, claim created
        // and protected, client thread survives a left-click. The world's save handle
        // is captured for world-reopen-keeps-claims (reopen) before the context closes.
        final Path firstWorldRoot;
        final TestWorldSave firstWorldSave;
        try (TestSingleplayerContext world1 = context.worldBuilder().create()) {
            //#if MC>=260300
            //$$ // 26.3 gametest-api 6.x: getClientLevel() moved to getConnection() (TestServerConnection)
            //$$ world1.getConnection().waitForChunksRender();
            //#else
            world1.getClientLevel().waitForChunksRender();
            //#endif
            firstWorldRoot = world1.getServer().computeOnServer(server -> {
                require(plugin.isSessionActive(), "session is active (world 1)");
                require(!server.isDedicatedServer(), "running on the integrated server");
                require(server.getCommands().getDispatcher().getRoot().getChild("claim") != null,
                        "claim command is registered");
                final Path worldRoot = server.getWorldPath(LevelResource.ROOT).normalize();
                final Path dataDirectory = plugin.getDataDirectory().normalize();
                require(dataDirectory.equals(worldRoot.resolve("huskclaims")),
                        "data directory is inside the world folder (world 1)");
                require(Files.exists(dataDirectory.resolve("HuskClaimsData.db")),
                        "world 1 database created in its own data directory");
                require(plugin.getServerName().equals("world-" + worldRoot.getFileName()),
                        "world 1 has a unique server name");
                plugin.log(Level.INFO, "[HuskClaims Gametest] world 1 root: " + worldRoot
                        + ", data directory: " + dataDirectory);
                return worldRoot;
            });
            firstWorldSave = world1.getWorldSave();

            // admin-and-owner-claims: admin claim must deny the host's BLOCK_BREAK; host-owned claim
            // in the same world must allow it. Proves the full protection pipeline
            // through the same ClaimHandler.cancelOperation the live listeners use.
            world1.getServer().runOnServer(server -> {
                final ServerPlayer host = server.getPlayerList().getPlayers().getFirst();
                require(host != null, "host player online for admin-and-owner-claims");

                // Anchor both regions on the host's position (loaded chunks, real
                // coordinates; 26.2 has no getSharedSpawnPos on ServerLevel)
                final BlockPos anchor = host.blockPosition();
                final int bx = anchor.getX() + 64;
                final int bz = anchor.getZ() + 64;
                final ClaimWorld world = getFirstClaimWorld(plugin);
                final FabricHuskClaimsAPI api = FabricHuskClaimsAPI.getInstance();
                final World posWorld = worldOf(plugin, world);

                final Position adminPos = Position.at(bx + 16, 64, bz + 16, posWorld);
                final Region adminRegion = Region.from(
                        Region.Point.at(bx + 8, bz + 8), Region.Point.at(bx + 40, bz + 40));
                final Claim adminClaim = api.createAdminClaim(world, adminRegion).join();
                require(adminClaim != null, "admin-and-owner-claims: admin claim created");
                require(adminClaim.isAdminClaim(), "admin-and-owner-claims: admin claim is ownerless");

                // 9×9 = 81 surface area — within the 100 starting claim blocks the
                // host has on a fresh world (default settings)
                final Position hostPos = Position.at(bx + 76, 64, bz + 12, posWorld);
                final Region hostRegion = Region.from(
                        Region.Point.at(bx + 72, bz + 8), Region.Point.at(bx + 80, bz + 16));
                final Claim hostClaim = api.createClaim(world, hostRegion, hostUserOf(plugin, host)).join();
                require(hostClaim != null, "admin-and-owner-claims: host-owned claim created");

                final net.william278.huskclaims.user.OnlineUser hostUser =
                        plugin.getOnlineUser(host.getGameProfile().id());
                require(hostUser != null, "admin-and-owner-claims: host OnlineUser resolvable");

                final boolean adminDenied = !api.isOperationAllowed(hostUser,
                        net.william278.cloplib.operation.OperationType.BLOCK_BREAK, adminPos);
                require(adminDenied, "admin-and-owner-claims: admin claim denies the host's BLOCK_BREAK");

                final boolean hostAllowed = api.isOperationAllowed(hostUser,
                        net.william278.cloplib.operation.OperationType.BLOCK_BREAK, hostPos);
                require(hostAllowed, "admin-and-owner-claims: host-owned claim allows the host's BLOCK_BREAK");

                require(api.getExactClaimAt(adminPos).isPresent(), "admin-and-owner-claims: claim resolvable at position");
                plugin.log(Level.INFO, "[HuskClaims Gametest] PASS admin-and-owner-claims - an admin claim denies the host's break while the host's own claim allows it");
            });

            // client-left-click-safe: simulate an attack on the block under the player
            // via the client gameMode; a ClassCastException or any client-thread crash
            // fails the test.
            context.runOnClient(minecraft -> {
                final LocalPlayer player = minecraft.player;
                require(player != null, "client-left-click-safe: local player present");
                final BlockPos under = BlockPos.containing(
                        player.getX(), player.getY() - 1, player.getZ());
                require(minecraft.gameMode != null, "client-left-click-safe: gameMode present");
                require(minecraft.gameMode.startDestroyBlock(under, net.minecraft.core.Direction.UP),
                        "client-left-click-safe: startDestroyBlock invoked without client-thread crash");
            });
            context.waitTicks(5);
        }

        // Between worlds: the first session must have ended and been reset, but the
        // once-per-process update-check guard must NOT be reset with it.
        require(!plugin.isSessionActive(), "session inactive between worlds");
        require(plugin.isUpdateChecked(), "update check ran during the first session (guard tripped)");

        // second-world-clean: a second world in the same game session starts clean and fully working
        try (TestSingleplayerContext world2 = context.worldBuilder().create()) {
            //#if MC>=260300
            //$$ // 26.3 gametest-api 6.x: getClientLevel() moved to getConnection() (TestServerConnection)
            //$$ world2.getConnection().waitForChunksRender();
            //#else
            world2.getClientLevel().waitForChunksRender();
            //#endif
            world2.getServer().runOnServer(server -> {
                require(plugin.isSessionActive(), "second session is active");
                require(plugin.isUpdateChecked(), "update check guard still engaged in the second session");
                require(plugin.getScheduler().isRunning(), "background pool restarted");
                require(!plugin.getClaimWorlds().isEmpty(), "claim worlds loaded for world 2");
                final Path worldRoot = server.getWorldPath(LevelResource.ROOT).normalize();
                require(!worldRoot.equals(firstWorldRoot), "each world has its own data folder");
                final Path dataDirectory = plugin.getDataDirectory().normalize();
                require(dataDirectory.equals(worldRoot.resolve("huskclaims")),
                        "data directory is inside the world folder (world 2)");
                require(Files.exists(dataDirectory.resolve("HuskClaimsData.db")),
                        "world 2 database created in its own data directory");
                require(plugin.getServerName().equals("world-" + worldRoot.getFileName()),
                        "world 2 has a unique server name");
                require(plugin.getClaimWorlds().values().stream()
                                .mapToInt(w -> w.getClaims().size()).sum() == 0,
                        "no claims carried over from the first world");
                require(server.getCommands().getDispatcher().getRoot().getChild("claim") != null,
                        "claim command still registered in world 2");
            });
        }

        require(!plugin.isSessionActive(), "session inactive after closing world 2");
        require(plugin.isUpdateChecked(), "update check still ran only once for the whole process");
        plugin.log(Level.INFO, "[HuskClaims Gametest] PASS second-world-clean - a second world starts clean and fully working");

        // world-reopen-keeps-claims: reopening the first world finds its claims again.
        // This is persisting per-world data through a full session stop/start cycle,
        // implemented via TestWorldSave#open().
        try (TestSingleplayerContext reopened = firstWorldSave.open()) {
            //#if MC>=260300
            //$$ // 26.3 gametest-api 6.x: getClientLevel() moved to getConnection() (TestServerConnection)
            //$$ reopened.getConnection().waitForChunksRender();
            //#else
            reopened.getClientLevel().waitForChunksRender();
            //#endif
            reopened.getServer().runOnServer(server -> {
                require(plugin.isSessionActive(), "world-reopen-keeps-claims: reopened session is active");
                require(plugin.getClaimWorlds().values().stream()
                                .mapToInt(w -> w.getClaims().size()).sum() == 2,
                        "world-reopen-keeps-claims: reopened world loaded its 2 claims (admin + host)");
                final Path worldRoot = server.getWorldPath(LevelResource.ROOT).normalize();
                require(worldRoot.equals(firstWorldRoot), "world-reopen-keeps-claims: reopened on the same world root");
                require(plugin.getDataDirectory().normalize().equals(worldRoot.resolve("huskclaims")),
                        "world-reopen-keeps-claims: reopened data directory is inside the world folder");
            });
        }
        require(!plugin.isSessionActive(), "session inactive after world-reopen-keeps-claims close");
        plugin.log(Level.INFO, "[HuskClaims Gametest] PASS world-reopen-keeps-claims - a reopened world loads its claims again");

        // claim-edge-protection + claim-outline-on-edge: protection and outline at the exact claim edge, through the same
        // server-side player actions a real client triggers.
        try (TestSingleplayerContext edgeWorld = context.worldBuilder().create()) {
            //#if MC>=260300
            //$$ // 26.3 gametest-api 6.x: getClientLevel() moved to getConnection() (TestServerConnection)
            //$$ edgeWorld.getConnection().waitForChunksRender();
            //#else
            edgeWorld.getClientLevel().waitForChunksRender();
            //#endif
            edgeWorld.getServer().runOnServer(server -> {
                final ServerPlayer host = server.getPlayerList().getPlayers().getFirst();
                final ServerLevel level = (ServerLevel) host.level();
                // The claim must live in the host's own dimension: the "first" claim world
                // used by admin-and-owner-claims may be the nether or the end, and claim-edge-protection edits real blocks here
                final World posWorld = FabricHuskClaims.Adapter.adapt(level);
                final ClaimWorld claimWorld = plugin.getClaimWorld(posWorld)
                        .orElseThrow(() -> new AssertionError("claim-edge-protection: no claim world for " + posWorld.getName()));
                final FabricHuskClaimsAPI api = FabricHuskClaimsAPI.getInstance();

                // Arena: a stone platform floating above the host, claim in its middle
                final BlockPos anchor = host.blockPosition();
                final int y0 = anchor.getY() + 12;
                final int x1 = anchor.getX() + 4, x2 = x1 + 8;
                final int z1 = anchor.getZ() + 4, z2 = z1 + 8;
                for (int x = x1 - 4; x <= x2 + 4; x++) {
                    for (int z = z1 - 4; z <= z2 + 4; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, y0, z), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(new BlockPos(x, y0 + 1, z), Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(new BlockPos(x, y0 + 2, z), Blocks.AIR.defaultBlockState());
                    }
                }
                final Claim claim = api.createAdminClaim(claimWorld,
                        Region.from(Region.Point.at(x1, z1), Region.Point.at(x2, z2))).join();
                require(claim != null, "claim-edge-protection: admin claim created");

                host.setGameMode(GameType.SURVIVAL);
                host.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 64));

                // Collect every failure instead of stopping at the first, so one run shows
                // the whole pattern across sides and faces
                final List<String> failures = new java.util.ArrayList<>();
                final int midX = (x1 + x2) / 2, midZ = (z1 + z2) / 2;
                for (Direction outward : List.of(Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH)) {
                    final int edge = switch (outward) {
                        case WEST -> x1; case EAST -> x2; case NORTH -> z1; default -> z2;
                    };
                    final String side = "claim-edge-protection " + outward.getName() + ": ";

                    // Break: ring yes, edge no
                    final BlockPos ringFloor = column(outward, edge, midX, midZ, y0, 1);
                    final BlockPos edgeFloor = column(outward, edge, midX, midZ, y0, 0);
                    host.gameMode.destroyBlock(ringFloor);
                    expect(failures, level.getBlockState(ringFloor).isAir(), side + "ring block can be broken");
                    level.setBlockAndUpdate(ringFloor, Blocks.STONE.defaultBlockState());
                    host.gameMode.destroyBlock(edgeFloor);
                    expect(failures, !level.getBlockState(edgeFloor).isAir(), side + "edge block cannot be broken");
                    level.setBlockAndUpdate(edgeFloor, Blocks.STONE.defaultBlockState());

                    final BlockPos ringAbove = ringFloor.above();
                    final BlockPos edgeAbove = edgeFloor.above();
                    for (double jitter : new double[]{0.0, -1.0e-6}) {
                        final String tag = side + "(jitter " + jitter + ") ";

                        // Place on top of the ring floor: allowed
                        click(host, ringFloor, Direction.UP, jitter);
                        expect(failures, level.getBlockState(ringAbove).is(Blocks.STONE), tag + "place on top of ring allowed");
                        level.setBlockAndUpdate(ringAbove, Blocks.AIR.defaultBlockState());

                        // Place into the ring against a block further out: allowed
                        final BlockPos beyond = column(outward, edge, midX, midZ, y0 + 1, 2);
                        level.setBlockAndUpdate(beyond, Blocks.STONE.defaultBlockState());
                        click(host, beyond, outward.getOpposite(), jitter);
                        expect(failures, level.getBlockState(ringAbove).is(Blocks.STONE), tag + "place into ring from outside allowed");
                        level.setBlockAndUpdate(beyond, Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(ringAbove, Blocks.AIR.defaultBlockState());

                        // Place into the edge against a block in the ring: denied
                        level.setBlockAndUpdate(ringAbove, Blocks.STONE.defaultBlockState());
                        click(host, ringAbove, outward.getOpposite(), jitter);
                        expect(failures, level.getBlockState(edgeAbove).isAir(), tag + "place into claim edge denied");
                        level.setBlockAndUpdate(ringAbove, Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(edgeAbove, Blocks.AIR.defaultBlockState());
                
                        // Place onto the edge against the outward face of a claim block: denied
                        // (the "placed a block I can't break" case on the east and south sides)
                        final BlockPos innerWall = column(outward, edge, midX, midZ, y0 + 1, -1);
                        level.setBlockAndUpdate(innerWall, Blocks.STONE.defaultBlockState());
                        click(host, innerWall, outward, jitter);
                        expect(failures, level.getBlockState(edgeAbove).isAir(), tag + "place onto claim edge from outside denied");
                        level.setBlockAndUpdate(innerWall, Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(edgeAbove, Blocks.AIR.defaultBlockState());
                    }
                }

                // claim-outline-on-edge: the outline is drawn on the claim's own corner blocks
                final List<Region.Point> corners = List.of(Region.Point.at(x1, z1), Region.Point.at(x2, z1),
                        Region.Point.at(x1, z2), Region.Point.at(x2, z2));
                final Map<Region.Point, Highlightable.Type> points = new java.util.HashMap<>();
                corners.forEach(p -> points.put(p, Highlightable.Type.ADMIN_CORNER));
                final Position viewer = Position.at(midX + 0.5, y0 + 1, midZ + 0.5, posWorld);
                final java.util.Set<String> expected = new java.util.HashSet<>();
                corners.forEach(p -> expected.add(p.getBlockX() + "," + p.getBlockZ()));
                final java.util.Set<String> drawn = new java.util.HashSet<>();
                plugin.getSurfaceBlocksAt(points, posWorld, viewer).keySet().forEach(b ->
                        drawn.add(b.getPosition().getBlockX() + "," + b.getPosition().getBlockZ()));
                expect(failures, drawn.equals(expected), "claim-outline-on-edge: outline drawn on " + expected + " but was " + drawn);

                failures.forEach(f -> plugin.log(Level.WARNING, "[HuskClaims Gametest] FAIL claim-edge-protection - " + f));
                require(failures.isEmpty(), "claim-edge-protection/claim-outline-on-edge all edge checks pass (" + failures.size() + " failed, listed above)");
                plugin.log(Level.INFO, "[HuskClaims Gametest] PASS claim-edge-protection - break and place are correct on all four sides of the edge");
                plugin.log(Level.INFO, "[HuskClaims Gametest] PASS claim-outline-on-edge - the outline is drawn on the claim's own blocks");
            });
        }

        plugin.log(Level.INFO, "[HuskClaims Gametest] PASS suite-worlds - all world and session checks");

        // claim-menu-renders: the claim menu opens for the singleplayer host, renders the
        // main menu through sgui, shows no missing locale keys, and leaves no menu items
        // behind after closing.
        try (TestSingleplayerContext guiWorld = context.worldBuilder().create()) {
            guiWorld.getServer().runOnServer(server -> {
                final ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                final FabricHuskClaims plugin9 = huskClaims();
                final OnlineUser user = plugin9.getOnlineUser(player);
                require(plugin9.isGuiAvailable(), "claim-menu-renders GUI available on the integrated server");
                plugin9.openClaimGui(user);
            });
            context.waitTicks(3);   // open is deferred one tick by the host
            guiWorld.getServer().runOnServer(server -> {
                final ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                final AbstractContainerMenu menu = player.containerMenu;
                require(menu != player.inventoryMenu, "claim-menu-renders a menu is open for the host");
                final int myClaimsSlot = huskClaims().getSettings().getGui().getMainMenu().getMyClaimsSlot();
                final ItemStack icon = menu.getSlot(myClaimsSlot).getItem();
                require(!icon.isEmpty(), "claim-menu-renders my-claims icon renders at slot " + myClaimsSlot);
                require(GuiItemMarker.has(icon, GuiItemMarker.MENU_KEY), "claim-menu-renders menu icons are marked");
                require(!icon.getHoverName().getString().contains("<gui_"), "claim-menu-renders no missing locale keys in the item name");
                player.closeContainer();
            });
            context.waitTicks(2);
            guiWorld.getServer().runOnServer(server -> {
                final ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    require(!GuiItemMarker.has(player.getInventory().getItem(i), GuiItemMarker.MENU_KEY),
                            "claim-menu-renders no menu item left in the inventory");
                }
            });
        }

        plugin.log(Level.INFO, "[HuskClaims Gametest] PASS claim-menu-renders - the menu renders and leaves nothing behind");

        // crafting-not-tainted-by-tool: carrying a handed-out claim tool must not taint unrelated crafting.
        // A tool anywhere in the inventory must NOT mark crafted planks (the old mixin
        // scanned every slot of the menu, including the player inventory, and taintined
        // everything crafted); repairing two handed-out tools must re-mark the result,
        // at a crafting table AND in the 2×2 inventory grid.
        try (TestSingleplayerContext craftWorld = context.worldBuilder().create()) {
            craftWorld.getServer().runOnServer(server -> {
                final ServerPlayer host = server.getPlayerList().getPlayers().getFirst();
                final ServerLevel level = (ServerLevel) host.level();
                final BlockPos pos = host.blockPosition();

                // A handed-out tool sitting in the player's inventory (not in the grid)
                final ItemStack carriedTool = markedClaimTool();
                host.getInventory().add(carriedTool);

                // T10a: tool in the inventory only — planks must come out unmarked
                final CraftingMenu table = new CraftingMenu(
                        9001, host.getInventory(), ContainerLevelAccess.create(level, pos));
                table.getSlot(1).set(new ItemStack(Items.OAK_LOG));
                require(!table.getSlot(0).getItem().isEmpty(), "T10a: planks recipe resolves");
                require(!GuiItemMarker.has(table.getSlot(0).getItem(), GuiItemMarker.TOOL_KEY),
                        "T10a: result not marked when the tool is only in the inventory");

                // T10b: repairing two damaged handed-out tools re-marks the result (table)
                table.getSlot(1).set(damaged(markedClaimTool()));
                table.getSlot(2).set(damaged(markedClaimTool()));
                require(GuiItemMarker.has(table.getSlot(0).getItem(), GuiItemMarker.TOOL_KEY),
                        "T10b: crafting-table repair re-marked");

                // T10c: same repair in the 2×2 inventory grid
                final InventoryMenu grid = host.inventoryMenu;
                grid.getSlot(1).set(damaged(markedClaimTool()));
                grid.getSlot(2).set(damaged(markedClaimTool()));
                require(GuiItemMarker.has(grid.getSlot(0).getItem(), GuiItemMarker.TOOL_KEY),
                        "T10c: 2×2 inventory-grid repair re-marked");

                // Clean up the grids so later checks see a pristine inventory
                for (int i = 0; i <= 4; i++) {
                    table.getSlot(i).set(ItemStack.EMPTY);
                }
                for (int i = 0; i <= 4; i++) {
                    grid.getSlot(i).set(ItemStack.EMPTY);
                }
                host.getInventory().removeItem(carriedTool);
            });
        }

        plugin.log(Level.INFO, "[HuskClaims Gametest] PASS crafting-not-tainted-by-tool - tools in the inventory do not taint crafting, and repairs stay marked");

        // furnace-refuses-tool: a handed-out claim tool cannot be moved into a furnace at all — not by
        // hand (Slot#mayPlace) and not by shift-click (quickMoveStack → moveItemStackTo
        // → mayPlace). The block entity's canPlaceItem only stops hoppers.
        try (TestSingleplayerContext furnaceWorld = context.worldBuilder().create()) {
            furnaceWorld.getServer().runOnServer(server -> {
                final ServerPlayer host = server.getPlayerList().getPlayers().getFirst();
                final ServerLevel level = (ServerLevel) host.level();
                final BlockPos furnacePos = host.blockPosition().above(3);
                level.setBlockAndUpdate(furnacePos, net.minecraft.world.level.block.Blocks.FURNACE.defaultBlockState());
                final net.minecraft.world.level.block.entity.FurnaceBlockEntity furnace =
                        (net.minecraft.world.level.block.entity.FurnaceBlockEntity) level.getBlockEntity(furnacePos);
                require(furnace != null, "furnace-refuses-tool: furnace block entity present");

                final net.minecraft.world.inventory.FurnaceMenu menu = new net.minecraft.world.inventory.FurnaceMenu(
                        9002, host.getInventory(), furnace, new net.minecraft.world.inventory.SimpleContainerData(4));
                require(!menu.getSlot(0).mayPlace(markedClaimTool()),
                        "furnace-refuses-tool: input slot refuses the handed-out tool by hand");
                require(menu.getSlot(0).mayPlace(new ItemStack(Items.RAW_IRON)),
                        "furnace-refuses-tool: input slot still accepts ore");

                // shift-click from the player's inventory must not move the tool into the furnace
                host.getInventory().add(markedClaimTool());
                final int toolInvSlot = host.getInventory().findSlotMatchingItem(markedClaimTool());
                require(toolInvSlot >= 0, "furnace-refuses-tool: tool added to the inventory");
                // player inventory slots in the menu start after the furnace slots (3 furnace slots + data)
                final int menuSlotForTool = 3 + toolInvSlot;
                final ItemStack before = menu.getSlot(menuSlotForTool).getItem().copy();
                menu.quickMoveStack(host, menuSlotForTool);
                require(furnace.getItem(0).isEmpty(),
                        "furnace-refuses-tool: shift-click does not move the tool into the furnace");
                require(menu.getSlot(menuSlotForTool).getItem().getCount() == before.getCount(),
                        "furnace-refuses-tool: tool still in the inventory after shift-click");

                // cleanup
                for (int i = 0; i < host.getInventory().getContainerSize(); i++) {
                    if (GuiItemMarker.has(host.getInventory().getItem(i), GuiItemMarker.TOOL_KEY)) {
                        host.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
                level.removeBlock(furnacePos, false);
            });
        }

        plugin.log(Level.INFO, "[HuskClaims Gametest] PASS furnace-refuses-tool - the furnace refuses a handed-out tool by hand and by shift-click");
    }

    private static ItemStack markedClaimTool() {
        final ItemStack tool = new ItemStack(Items.GOLDEN_SHOVEL);
        tool.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, GuiItemMarker.TOOL_ITEM);
        return tool;
    }

    private static ItemStack damaged(@NotNull ItemStack tool) {
        final ItemStack copy = tool.copy();
        copy.setDamageValue(Math.max(1, copy.getMaxDamage() / 2));
        return copy;
    }

    // Record a failed expectation without stopping the test
    private static void expect(@NotNull List<String> failures, boolean condition, @NotNull String what) {
        if (!condition) {
            failures.add(what);
        }
    }

    // A block in the column `steps` blocks outward from a claim edge, on the edge's midline.
    // steps -1 = one block inside the edge, 0 = the edge itself (inside),
    // 1 = first block outside, 2 = second block outside.
    @NotNull
    private static BlockPos column(@NotNull Direction outward, int edge, int midX, int midZ, int y, int steps) {
        return outward.getAxis() == Direction.Axis.X
                ? new BlockPos(edge + outward.getStepX() * steps, y, midZ)
                : new BlockPos(midX, y, edge + outward.getStepZ() * steps);
    }

    // Right-click a face of a block the way a client does: the hit lies on the face itself,
    // or a hair inside the block when jitter is negative (float rounding of the cursor)
    private static void click(@NotNull ServerPlayer player, @NotNull BlockPos clicked,
                              @NotNull Direction face, double jitter) {
        final Vec3 onFace = Vec3.atCenterOf(clicked).add(
                face.getStepX() * (0.5 + jitter), face.getStepY() * (0.5 + jitter), face.getStepZ() * (0.5 + jitter));
        final BlockHitResult hit = new BlockHitResult(onFace, face, clicked, false);
        player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    // Resolve the first ClaimWorld and its matching World (the plugin's claim-world
    // map is keyed by world name) for claim creation and position building
    @NotNull
    private static ClaimWorld getFirstClaimWorld(@NotNull FabricHuskClaims plugin) {
        require(!plugin.getClaimWorlds().isEmpty(), "claim worlds loaded (admin-and-owner-claims)");
        return plugin.getClaimWorlds().values().iterator().next();
    }

    @NotNull
    private static World worldOf(@NotNull FabricHuskClaims plugin, @NotNull ClaimWorld claimWorld) {
        final String worldName = plugin.getClaimWorlds().entrySet().stream()
                .filter(e -> e.getValue().equals(claimWorld)).map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new AssertionError("claim world key not found"));
        return plugin.getWorlds().stream().filter(w -> w.getName().equals(worldName)).findFirst()
                .orElseThrow(() -> new AssertionError("world " + worldName + " not found"));
    }

    // Build a User record for the host (owner of the admin-and-owner-claims host claim)
    @NotNull
    private static User hostUserOf(@NotNull FabricHuskClaims plugin, @NotNull ServerPlayer host) {
        return User.of(host.getGameProfile().id(), host.getGameProfile().name());
    }

    @NotNull
    private static FabricHuskClaims huskClaims() {
        return FabricLoader.getInstance().getEntrypointContainers("main", ModInitializer.class).stream()
                .map(EntrypointContainer::getEntrypoint)
                .filter(FabricHuskClaims.class::isInstance)
                .map(FabricHuskClaims.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("HuskClaims entrypoint not found"));
    }

    private static void require(boolean condition, @NotNull String what) {
        if (!condition) {
            throw new AssertionError("Expected: " + what);
        }
    }

}
