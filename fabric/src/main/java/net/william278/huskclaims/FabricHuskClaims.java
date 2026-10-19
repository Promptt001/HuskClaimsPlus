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

package net.william278.huskclaims;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.primitives.Longs;
import com.google.gson.Gson;
import com.pokeskies.fabricpluginmessaging.PluginMessageEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import com.mojang.brigadier.CommandDispatcher;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.level.ServerPlayer;
import net.kyori.adventure.audience.Audience;
//#if MC>=12104
import net.kyori.adventure.platform.modcommon.MinecraftServerAudiences;
//#else
//$$ import net.kyori.adventure.platform.fabric.FabricServerAudiences;
//#endif
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.william278.cloplib.operation.OperationType;
import net.william278.desertwell.util.Version;
import net.william278.huskclaims.api.FabricHuskClaimsAPI;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.command.Command;
import net.william278.huskclaims.command.FabricCommand;
import de.exlll.configlib.YamlConfigurations;
import net.william278.huskclaims.config.FabricSettings;
import net.william278.huskclaims.config.Locales;
import net.william278.huskclaims.config.Server;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.config.TrustLevels;
import net.william278.huskclaims.database.Database;
import net.william278.huskclaims.event.FabricEventDispatcher;
import net.william278.huskclaims.highlighter.FabricBlockDisplayHighlighter;
import net.william278.huskclaims.highlighter.Highlighter;
import net.william278.huskclaims.hook.FabricHookProvider;
import net.william278.huskclaims.hook.Hook;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.SguiMenuHost;
import net.william278.huskclaims.gui.FabricClaimTool;
import net.william278.huskclaims.command.GuiProvider;
import net.william278.huskclaims.listener.ClaimsListener;
import net.william278.huskclaims.listener.FabricListener;
import net.william278.huskclaims.network.Broker;
import net.william278.huskclaims.network.PluginMessageBroker;
import com.pokeskies.fabricpluginmessaging.FabricPluginMessaging;
import net.william278.huskclaims.pet.FabricPetHandler;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.position.World;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.trust.TrustTag;
import net.william278.huskclaims.trust.UserGroup;
import net.william278.huskclaims.user.*;
import net.william278.huskclaims.util.*;
import net.william278.toilet.Toilet;
import net.william278.toilet.fabric.FabricToilet;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

@NoArgsConstructor
@Getter
public class FabricHuskClaims implements ModInitializer, HuskClaims, FabricTask.Supplier,
        FabricUserProvider, FabricBlockProvider, FabricSafeTeleportProvider, FabricPetHandler, FabricEventDispatcher,
        FabricHookProvider, GuiProvider {

    @Nullable
    private SguiMenuHost menuHost;
    @Nullable
    private GuiController guiController;

    public static final Logger LOGGER = LoggerFactory.getLogger("HuskClaims");
    public static final Identifier INITIAL_PHASE = Identifier.fromNamespaceAndPath("huskclaims", "on_enable");
    private final ModContainer mod = FabricLoader.getInstance().getModContainer("huskclaims")
            .orElseThrow(() -> new RuntimeException("Failed to get Mod Container"));
    private final Map<String, Boolean> permissions = Maps.newHashMap();

    //#if MC>=12104
    private MinecraftServerAudiences audiences;
    //#else
    //$$ private FabricServerAudiences audiences;
    //#endif
    @Nullable
    private MinecraftServer minecraftServer;
    @NotNull
    private FabricScheduler scheduler = FabricScheduler.start("idle");
    @Nullable
    private Path dataDirectory;
    private Toilet toilet;
    private volatile boolean sessionActive;
    private int sessionCount;

    private final Gson gson = getGsonBuilder().create();
    private final Set<TrustTag> trustTags = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<DroppedItem>> markedDrops = Maps.newHashMap();
    private final Map<UUID, Set<GroundStack>> trackedItems = Maps.newHashMap();
    private final ConcurrentMap<String, List<User>> globalUserList = Maps.newConcurrentMap();
    private final ConcurrentMap<UUID, ClaimSelection> claimSelections = Maps.newConcurrentMap();
    private final ConcurrentMap<UUID, OnlineUser> onlineUserMap = Maps.newConcurrentMap();
    private final ConcurrentMap<UUID, SavedUser> userCache = Maps.newConcurrentMap();
    private final ConcurrentMap<UUID, Highlighter> highlighterCache = Maps.newConcurrentMap();
    private final List<Command> commands = Lists.newArrayList();
    private final List<Highlighter> highlighters = Lists.newArrayList();
    private volatile boolean updateChecked = false;
    private final HashMap<String, ClaimWorld> claimWorlds = Maps.newHashMap();
    private final Queue<Task.Async> taskQueue = Queues.newConcurrentLinkedQueue();

    @Setter
    private Map<UUID, Set<UserGroup>> userGroups = Maps.newConcurrentMap();
    @Setter
    private Set<Hook> hooks = Sets.newHashSet();
    @Setter
    private ClaimsListener operationListener;
    @Setter
    private Database database;
    @Setter
    private Settings settings;
    @Setter
    private FabricSettings fabricSettings;
    @Setter
    private TrustLevels trustLevels;
    @Setter
    private Locales locales;
    @Setter
    @Nullable
    private Broker broker;
    @Setter
    @Nullable
    private Server serverName;

    @Override
    public void onInitialize() {
        // Settings, locales, trust levels and load-time hooks are read once here so the
        // command tree can be built when the first server asks for it. Each later
        // session reloads them in onServerStarting.
        this.load();
        this.loadFabricSettings();
        this.loadCommands();
        this.scheduler.drain(message -> { }); // the placeholder pool is never used

        // Fabric events cannot be unregistered, so the command callback and the
        // permission listener are registered exactly once.
        CommandRegistrationCallback.EVENT.register(this::onRegisterCommands);
        PermissionCheckEvent.EVENT.register(this::onPermissionCheck);

        ServerLifecycleEvents.SERVER_STARTING.addPhaseOrdering(Event.DEFAULT_PHASE, INITIAL_PHASE);
        ServerLifecycleEvents.SERVER_STARTING.register(INITIAL_PHASE, this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);

        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER) {
            // Proxy plugin messages only exist between a proxy and a dedicated server;
            // registering channel codecs on the client crashes resource reload.
            FabricPluginMessaging.initialize();
        }
    }

    /**
     * Load the Fabric-only settings from {@code fabric.yml} in the config directory.
     *
     * @since HuskClaimsPlus 1.0.0
     */
    private void loadFabricSettings() {
        this.fabricSettings = YamlConfigurations.update(
                getConfigDirectory().resolve("fabric.yml"),
                FabricSettings.class,
                YAML_CONFIGURATION_PROPERTIES.header(FabricSettings.CONFIG_HEADER).build()
        );
    }

    private void onServerStarting(@NotNull MinecraftServer server) {
        this.minecraftServer = server;
        this.dataDirectory = resolveDataDirectory(server);
        this.scheduler = FabricScheduler.start(server.isDedicatedServer() ? "dedicated" : "integrated");
        if (sessionCount++ > 0) {
            this.load(); // hooks were unloaded when the previous world stopped
            this.loadFabricSettings();
        }
        //#if MC>=12104
        this.audiences = MinecraftServerAudiences.of(minecraftServer);
        //#else
        //$$ this.audiences = FabricServerAudiences.of(minecraftServer);
        //#endif
        this.toilet = FabricToilet.create(getDumpOptions(), minecraftServer);
        this.sessionActive = true;
        startGui();
        this.enable();
    }

    private void onServerStopping(@NotNull MinecraftServer server) {
        if (!sessionActive) {
            return;
        }
        this.sessionActive = false;
        this.shutdown();   // hooks, broker, database (drains the scheduler first), tasks, API
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
        resetSessionState();
        this.minecraftServer = null;
    }

    @Override
    public void loadAPI() {
        FabricHuskClaimsAPI.register(this);
    }

    @Override
    public void loadMetrics() {
        // No metrics on Fabric
    }

    @Override
    public void disablePlugin() {
        if (minecraftServer != null) {
            onServerStopping(minecraftServer);
        }
    }

    /**
     * Drain background work before the database closes, so queued writes reach the
     * database instead of failing against a closed connection.
     */
    @Override
    public void closeDatabase() {
        scheduler.drain(message -> log(Level.WARNING, message));
        if (database != null) {
            database.close();
            database = null;
        }
    }

    /**
     * Forget everything that belongs to the world that just closed. The next world
     * starts from the same empty state a freshly started server has.
     */
    private void resetSessionState() {
        onlineUserMap.clear();
        userCache.clear();
        claimSelections.clear();
        highlighterCache.clear();
        highlighters.clear();
        claimWorlds.clear();
        markedDrops.clear();
        trackedItems.clear();
        globalUserList.clear();
        trustTags.clear();
        taskQueue.clear();
        commands.clear();
        userGroups = Maps.newConcurrentMap();
        broker = null;
        toilet = null;
        // Menu session state must be reset with everything else, or a menu left open
        // from the previous world keeps pointing at a closed session.
        if (menuHost != null) {
            menuHost.closeAll();
        }
        menuHost = null;
        guiController = null;
        FabricClaimTool.reset();
    }

    /**
     * Start the claim GUI for this session, when enabled in config. Called from the
     * session-start path after settings and locales load; the controller and host
     * belong to the session and are reset with it.
     *
     * @since HuskClaimsPlus 1.0.0
     */
    private void startGui() {
        if (!getSettings().getGui().isEnabled()) {
            return;
        }
        this.guiController = new GuiController(this, c -> this.menuHost = new SguiMenuHost(this, c));
    }

    @Override
    public boolean isGuiAvailable() {
        return guiController != null;
    }

    @Override
    public void openClaimGui(@NotNull OnlineUser user) {
        if (guiController != null) {
            guiController.openMainMenu(user);
        }
    }

    public boolean isSessionActive() {
        return sessionActive && minecraftServer != null;
    }

    @NotNull
    @Override
    public Audience getAudience(@NotNull UUID user) {
        return audiences.player(user);
    }

    @NotNull
    @Override
    public Version getPluginVersion() {
        return PluginVersions.plugin(mod.getMetadata().getVersion().getFriendlyString());
    }

    @Override
    @NotNull
    public Version getMinecraftVersion() {
        return PluginVersions.minecraft(requireServer().getServerVersion());
    }

    @Override
    @NotNull
    public String getServerType() {
        return "%s %s/%s".formatted("Fabric", FabricLoader.getInstance()
                .getModContainer("fabricloader").map(l -> l.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown"), requireServer().getServerVersion());
    }

    @NotNull
    @Override
    public List<World> getWorlds() {
        final List<World> worlds = Lists.newArrayList();
        requireServer().getAllLevels().forEach(world -> worlds.add(Adapter.adapt(world)));
        return worlds;
    }

    @Override
    public void checkForUpdates() {
        // The update check runs at the end of every enable(), which fires once per
        // world session; once per process is enough. The body is
        // inlined from MetaProvider#checkForUpdates because a default super call on
        // a redundant (indirectly inherited) interface does not compile.
        if (updateChecked) {
            return;
        }
        this.updateChecked = true;
        if (!hasUpdateSource() || !getSettings().isCheckForUpdates()) {
            return;
        }
        getUpdateChecker().check().thenAccept(checked -> {
            if (checked.isUpToDate()) {
                return;
            }
            log(Level.WARNING, String.format(
                    "A new version of HuskClaimsPlus is available: v%s (running v%s). Download: %s",
                    checked.getLatestVersion(), getPluginVersion(), RELEASES_URL)
            );
        });
    }

    @NotNull
    public MinecraftServer requireServer() {
        final MinecraftServer server = minecraftServer;
        if (server == null) {
            throw new IllegalStateException("No world is open");
        }
        return server;
    }

    @Override
    public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable... exceptions) {
        LoggingEventBuilder logEvent = LOGGER.makeLoggingEventBuilder(
                switch (level.getName()) {
                    case "WARNING" -> org.slf4j.event.Level.WARN;
                    case "SEVERE" -> org.slf4j.event.Level.ERROR;
                    default -> org.slf4j.event.Level.INFO;
                }
        );
        if (exceptions.length >= 1) {
            logEvent = logEvent.setCause(exceptions[0]);
        }
        logEvent.log(message);
    }

    @NotNull
    @Override
    public String getServerName() {
        final MinecraftServer server = minecraftServer;
        if (server != null && !server.isDedicatedServer()) {
            // Each singleplayer/LAN world is its own server for shared database setups,
            // so claims do not leak between worlds
            return "world-" + server.getWorldPath(LevelResource.ROOT).normalize().getFileName();
        }
        return serverName != null ? serverName.getName() : "server";
    }

    @NotNull
    @Override
    public List<TrustLevel> getTrustLevels() {
        return trustLevels.getTrustLevels();
    }

    @NotNull
    @Override
    public Set<OperationType> allowedOwnerOperations() {
        return trustLevels.getAllowedOwnerOperations();
    }

    @Override
    public InputStream getResource(@NotNull String name) {
        return this.mod.findPath(name)
                .map(path -> {
                    try {
                        return Files.newInputStream(path);
                    } catch (IOException e) {
                        log(Level.WARNING, "Failed to load resource: " + name, e);
                    }
                    return null;
                })
                .orElse(this.getClass().getClassLoader().getResourceAsStream(name));
    }

    @NotNull
    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("huskclaims");
    }

    /**
     * On integrated servers (singleplayer/Open-to-LAN), each world keeps its own claims
     * in {@code saves/<world>/huskclaims/}. Dedicated servers keep the config directory,
     * so databases created by upstream HuskClaims keep working.
     *
     * @return the directory holding this server's database, or the config dir before startup
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    @Override
    public Path getDataDirectory() {
        return dataDirectory != null ? dataDirectory : getConfigDirectory();
    }

    @NotNull
    private Path resolveDataDirectory(@NotNull MinecraftServer server) {
        final Path directory = server.isDedicatedServer()
                ? getConfigDirectory()
                : server.getWorldPath(LevelResource.ROOT).resolve("huskclaims").normalize();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create the data directory " + directory, e);
        }
        if (!server.isDedicatedServer() && !Files.exists(directory.resolve("HuskClaimsData.db"))) {
            log(Level.INFO, String.format(
                    "Claims for this world are stored in %s",
                    directory.resolve("HuskClaimsData.db").toAbsolutePath()));
        }
        return directory;
    }

    /**
     * A LAN world has no proxy behind it, so cross-server messaging is unavailable on
     * integrated servers even when the config asks for it.
     *
     * @return false while a singleplayer/LAN world is open
     * @since HuskClaimsPlus 1.0.0
     */
    @Override
    public boolean isCrossServerSupported() {
        final MinecraftServer server = minecraftServer;
        if (server == null || server.isDedicatedServer()) {
            return true;
        }
        if (getSettings().getCrossServer().isEnabled()) {
            log(Level.WARNING, "Cross-server messaging is not available on singleplayer/LAN worlds; "
                    + "the broker will not be started");
        }
        return false;
    }

    @Override
    public Optional<Broker> getBroker() {
        return Optional.ofNullable(broker);
    }

    @Override
    public void closeBroker() {
        if (broker != null) {
            broker.close();
        }
    }

    @Override
    public void registerCommands(@NotNull List<Command> toRegister) {
        // Called by loadCommands(): replaces the live command set. The brigadier tree
        // looks commands up by name when they run, so it always reaches this list.
        synchronized (commands) {
            commands.clear();
            commands.addAll(toRegister);
        }
    }

    private void onRegisterCommands(@NotNull CommandDispatcher<CommandSourceStack> dispatcher,
                                    @NotNull CommandBuildContext context,
                                    @NotNull Commands.CommandSelection selection) {
        if (commands.isEmpty()) {
            loadCommands();
        }
        permissions.clear();
        List.copyOf(commands).forEach(command -> new FabricCommand(command.getName(), this)
                .register(dispatcher, command));
    }

    @NotNull
    public Optional<Command> findCommand(@NotNull String name) {
        synchronized (commands) {
            return commands.stream().filter(c -> c.getName().equals(name)).findFirst();
        }
    }

    /**
     * Single global permission listener: reproduces the defaults for
     * operator-only additional permissions, so checks that pass no explicit default
     * still see them, and applies the LAN rule via {@link FabricPermissions}.
     */
    private TriState onPermissionCheck(@NotNull Object source, @NotNull String node) {
        if (!isSessionActive() || !Boolean.TRUE.equals(permissions.get(node))) {
            return TriState.DEFAULT;
        }
        if (source instanceof CommandSourceStack stack
                && stack.getEntity() instanceof ServerPlayer player) {
            return FabricPermissions.hasOperatorDefaults(this, player) ? TriState.TRUE : TriState.DEFAULT;
        }
        return TriState.DEFAULT;
    }

    @Override
    @NotNull
    public ConsoleUser getConsole() {
        return ConsoleUser.wrap(audiences.console());
    }


    @Override
    public void setupPluginMessagingChannels() {
        PluginMessageEvent.EVENT.register((payload, context) -> {
            if (broker instanceof PluginMessageBroker messenger &&
                    getSettings().getCrossServer().getBrokerType() == Broker.Type.PLUGIN_MESSAGE) {
                messenger.onReceive(
                        PluginMessageBroker.BUNGEE_CHANNEL_ID,
                        getOnlineUser(context.player()),
                        payload.getData()
                );
            }
        });
    }

    @Override
    public void setClaimWorlds(@NotNull HashMap<World, ClaimWorld> claimWorlds) {
        this.claimWorlds.clear();
        claimWorlds.forEach((world, claimWorld) -> this.claimWorlds.put(world.getName(), claimWorld));
    }

    @NotNull
    @Override
    public ClaimsListener createListener() {
        return new FabricListener(this);
    }

    @Override
    public void loadListeners() {
        if (operationListener == null) {
            // Fabric events cannot be unregistered, so the listener is created and
            // registered once; later sessions reuse it.
            setOperationListener(createListener());
            getOperationListener().register();
        } else {
            getOperationListener().setInspectorCallbacks(); // tool items may have changed in config
        }
    }

    @Override
    public void loadHighlighters() {
//        HuskClaims.super.loadHighlighters(); // todo - not yet implemented

        if (!getSettings().getHighlighter().isBlockDisplays()) {
            log(Level.WARNING, "Block display highlighting is disabled, but this is the only builtin " +
                    "highlighter supported on Fabric currently. Enabling anyway.");
        }
        registerHighlighter(new FabricBlockDisplayHighlighter(this));
    }

    // Pack change data into chunk sections for the ChunkDeltaUpdate packet
    @Override
    public void sendBlockUpdates(@NotNull OnlineUser user, @NotNull Map<Position, MaterialBlock> blocks) {
        throw new UnsupportedOperationException("Block updates not yet implemented in Fabric HuskClaims");
        /* todo

        // Get biome registry
        final net.minecraft.world.level.Level world = Adapter.adapt(user.getWorld(), minecraftServer);
        final Registry<Biome> biomes = world.getRegistryManager().getOrThrow(Registries.BIOME);

        // Calculate chunk sectors to update
        final Map<ChunkSectionPos, Pair<ShortSet, ChunkSection>> sections = Maps.newHashMap();
        for (Map.Entry<Position, MaterialBlock> entry : blocks.entrySet()) {
            final Position pos = entry.getKey();
            final BlockPos block = BlockPos.ofFloored(pos.getBlockX(), pos.getY(), pos.getBlockZ());
            final Pair<ShortSet, ChunkSection> sectionData = sections.computeIfAbsent(
                    ChunkSectionPos.from(block), ignored -> new Pair<>(new ShortArraySet(), new ChunkSection(biomes))
            );

            // Set data
            final short packed = ChunkSectionPos.packLocal(block);
            sectionData.getLeft().add(packed);
            sectionData.getRight().setBlockState(
                    ChunkSectionPos.unpackLocalX(packed),
                    ChunkSectionPos.unpackLocalY(packed),
                    ChunkSectionPos.unpackLocalZ(packed),
                    ((BlockMaterialBlock) entry.getValue()).getData().defaultBlockState()
            );
        }

        // Send packets for each pos
        final ServerPlayerEntity player = ((FabricUser) user).getFabricPlayer();
        sections.forEach((section, data) -> player.connection.sendPacket(
                new ChunkDeltaUpdateS2CPacket(section, data.getLeft(), data.getRight())));*/
    }

    @Override
    @NotNull
    public FabricHuskClaims getPlugin() {
        return this;
    }


    public static class Adapter {
        @NotNull
        public static Position adapt(@NotNull net.minecraft.world.level.Level world, @NotNull Vec3 pos,
                                     float yaw, float pitch) {
            return Position.at(pos.x(), pos.y(), pos.z(), yaw, pitch, adapt(world));
        }

        @NotNull
        public static Position adapt(@NotNull Location location) {
            return adapt(location.world(), location.pos(), location.yaw(), location.pitch());
        }

        @NotNull
        public static Location adapt(@NotNull Position position, @NotNull MinecraftServer server) {
            return new Location(
                    adapt(position.getWorld(), server),
                    position.getX(), position.getY(), position.getZ(),
                    position.getYaw(), position.getPitch()
            );
        }

        @NotNull
        public static ServerLevel adapt(@NotNull World world, @NotNull MinecraftServer server) {
            final Identifier id = Identifier.parse(world.getName());
            final ServerLevel found = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (found == null) {
                throw new IllegalArgumentException("World not found: %s".formatted(world.getName()));
            }
            return found;
        }

        @NotNull
        public static World adapt(@NotNull net.minecraft.world.level.Level world) {
            final MinecraftServer server = world.getServer();
            final long seed = server != null ? server.overworld().getSeed() : 0;
            final Identifier id = world.dimension().identifier();
            return World.of(
                    id.asMinimalString(),
                    UUID.nameUUIDFromBytes(ArrayUtils.addAll(Longs.toByteArray(seed),
                            id.toString().getBytes(StandardCharsets.UTF_8))),
                    world.dimensionTypeRegistration().unwrapKey().map(k -> k.identifier().asMinimalString())
                            .orElse("unknown")
            );
        }

        @NotNull
        public static Map<Location, Block> adapt(@NotNull Map<Position, BlockProvider.MaterialBlock> blocks,
                                                 @NotNull MinecraftServer server) {
            final Map<Location, Block> blockData = Maps.newHashMap();
            blocks.forEach((position, materialBlock) -> blockData.put(
                    adapt(position, server),
                    BuiltInRegistries.BLOCK.getValue(Identifier.parse(materialBlock.getMaterialKey()))
            ));
            return blockData;
        }

    }

}
