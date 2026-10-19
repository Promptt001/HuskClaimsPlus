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

package net.william278.huskclaims.gui;

import de.exlll.configlib.YamlConfigurations;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.command.Command;
import net.william278.huskclaims.config.Locales;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.database.Database;
import net.william278.huskclaims.gui.model.MenuHost;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.gui.model.TextPrompt;
import net.william278.huskclaims.position.World;
import net.william278.huskclaims.user.CommandUser;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.position.ServerWorld;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.SavedUser;
import net.william278.huskclaims.user.User;
import net.william278.huskclaims.trust.UserGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Test harness for shared menus: a key-echo locale (every real en-gb template
 * replaced by the key followed by three visible placeholders), default GUI
 * settings, a recording host and a recording legacy bridge. Render tests assert
 * the exact item, name keys, lore keys and arguments; routing tests assert the
 * recorded calls, so menus can be tested without a server.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class GuiTestKit {

    public final Settings settings = defaultSettings();
    public final Locales locales = echoLocales();
    public final List<Command> commands = new ArrayList<>();
    public final RecordingHost host = new RecordingHost();
    /** Trust levels the fake plugin serves; tests install fixtures here before opening menus. */
    public final List<TrustLevel> trustLevels = new ArrayList<>();
    /** Names the fake host serves for UUIDs (cachedUserName); empty = "unknown (uuid8)" fallbacks. */
    public final Map<UUID, String> knownNames = new java.util.HashMap<>();
    /** Claim worlds the fake plugin serves; tests install fixtures here before opening menus. */
    public final java.util.HashMap<String, ClaimWorld> claimWorlds = new java.util.HashMap<>();
    public final RecordingDatabase database = new RecordingDatabase();
    /** userCreateClaim calls the fake editor received (bypass-path creates). */
    public final List<EditorCall> createdClaims = new ArrayList<>();
    /** userResizeClaim calls the fake editor received. */
    public final List<EditorCall> resizedClaims = new ArrayList<>();
    /** userResizeChildClaim calls the fake editor received. */
    public final List<EditorCall> resizedChildren = new ArrayList<>();

    /** One recorded editor call: who, in which world, for which region. */
    public record EditorCall(@NotNull OnlineUser user, @NotNull ClaimWorld world, @NotNull Region region) {
    }
    public final HuskClaims plugin;
    public final GuiController controller;

    public GuiTestKit() {
        this.plugin = (HuskClaims) Proxy.newProxyInstance(
                HuskClaims.class.getClassLoader(), new Class<?>[]{HuskClaims.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLocales" -> locales;
                    case "getSettings" -> settings;
                    case "getCommands" -> commands;
                    case "getTrustLevels" -> trustLevels;
                    case "getTrustLevel" -> trustLevels.stream()
                            .filter(l -> l.getId().equalsIgnoreCase(String.valueOf(args[0])))
                            .findFirst();
                    case "getClaimWorlds" -> claimWorlds;
                    case "getClaimWorld" -> {
                        // default methods on the interface; serve both overloads so
                        // ClaimRef.resolve and the menus' world checks work
                        final Object key = args != null && args.length > 0
                                ? args[0] instanceof World w ? w.getName() : args[0] : null;
                        yield key != null ? Optional.ofNullable(claimWorlds.get(String.valueOf(key)))
                                : Optional.empty();
                    }
                    case "getDatabase" -> database;
                    case "getCachedClaimBlocks" -> {
                        final Object who = args != null && args.length > 0 ? args[0] : null;
                        final UUID uuid = who instanceof OnlineUser u ? u.getUuid()
                                : who instanceof UUID id ? id : null;
                        final SavedUser saved = uuid != null ? database.byUuid.get(uuid) : null;
                        yield saved == null ? 0L : saved.getClaimBlocks();
                    }
                    case "userCreateClaim" -> {
                        createdClaims.add(new EditorCall((OnlineUser) args[0], (ClaimWorld) args[1], (Region) args[2]));
                        yield null;
                    }
                    case "userResizeClaim" -> {
                        resizedClaims.add(new EditorCall((OnlineUser) args[0], (ClaimWorld) args[1], (Region) args[3]));
                        yield null;
                    }
                    case "userResizeChildClaim" -> {
                        resizedChildren.add(new EditorCall((OnlineUser) args[0], (ClaimWorld) args[1], (Region) args[3]));
                        yield null;
                    }
                    case "getHook" -> Optional.empty();
                    case "runQueued" -> {
                        if (args != null && args.length > 0 && args[0] instanceof Runnable r) {
                            r.run();
                        }
                        yield null;
                    }
                    case "runAsync", "runSyncDelayed", "runSync" -> {
                        // run scheduled work inline so command dispatches are observable
                        if (args != null) {
                            for (Object a : args) {
                                if (a instanceof Runnable r) {
                                    r.run();
                                }
                            }
                        }
                        yield null;
                    }
                    case "isSafeStandingSpot" -> java.util.concurrent.CompletableFuture.completedFuture(true);
                    case "log" -> null;
                    default -> defaultValue(method.getReturnType());
                });
        this.controller = new GuiController(plugin, c -> host);
    }

    @NotNull
    private static Object defaultValue(@NotNull Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    @NotNull
    private static Settings defaultSettings() {
        try {
            final var ctor = Settings.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot build default Settings for the test kit", e);
        }
    }

    /**
     * Loads the real en-gb locale file, then replaces every template with the
     * key-echo form, so rendered text reveals exactly which key and arguments
     * produced it. Keys absent from the file stay absent (missing-key path).
     */
    @NotNull
    private static Locales echoLocales() {
        try (InputStream in = GuiTestKit.class.getClassLoader().getResourceAsStream("locales/en-gb.yml")) {
            if (in == null) {
                throw new IllegalStateException("locales/en-gb.yml not on the test classpath");
            }
            final Locales loaded = YamlConfigurations.read(in, Locales.class);
            final Field field = Locales.class.getDeclaredField("locales");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            final Map<String, String> raw = (Map<String, String>) field.get(loaded);
            raw.replaceAll((key, value) -> echo(key));
            return loaded;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build the key-echo locale", e);
        }
    }

    /* Key-echo expectations ------------------------------------------------ */

    /** The template every key resolves to: the key, then three visible placeholders. */
    @NotNull
    public static String echo(@NotNull String key) {
        return "<" + key + ">%1%|%2%|%3%";
    }

    /** What a name renders to for {@code key} with {@code args}, through the real text rules. */
    @NotNull
    public static String name(@NotNull String key, @NotNull String... args) {
        return GuiText.asName(GuiText.fill(GuiText.color(echo(key)), args));
    }

    /** What a single-line lore entry renders to. */
    @NotNull
    public static String lore(@NotNull String key, @NotNull String... args) {
        return GuiText.asLore(GuiText.fill(GuiText.color(echo(key)), args));
    }


    /* Claim fixtures --------------------------------------------------------- */

    /** The world every fixture claim lives in. */
    public static final World WORLD = World.of("world", UUID.randomUUID(), "normal");

    private static final UUID OWNER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID STRANGER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** A claim owned by the tester, registered in the claim world named "world". */
    @NotNull
    public Claim claimAt(int x1, int z1, int x2, int z2) {
        final Claim claim = newClaim(OWNER_UUID, Region.from(Region.Point.at(x1, z1), Region.Point.at(x2, z2)));
        addClaim("world", claim);
        return claim;
    }

    /** A claim owned by someone else at the given box, registered in "world". */
    @NotNull
    public Claim newStrangerClaimAt(int x1, int z1, int x2, int z2) {
        final Claim claim = Claim.create(STRANGER_UUID,
                Region.from(Region.Point.at(x1, z1), Region.Point.at(x2, z2)), plugin);
        addClaim("world", claim);
        return claim;
    }

    /** Registers a claim in the given claim world, creating the world if needed. */
    public void addClaim(@NotNull String worldName, @NotNull Claim claim) {
        claimWorlds.compute(worldName, (k, existing) -> {
            final ClaimWorld world = existing != null ? existing : ClaimWorld.convert(
                    new java.util.HashSet<>(), Map.of(), Set.of());
            world.addClaim(claim);
            return world;
        });
    }

    @NotNull
    private Claim newClaim(@Nullable UUID owner, @NotNull Region region) {
        return Claim.create(owner, region, plugin);
    }

    /* Fixtures ------------------------------------------------------------- */

    /**
     * A real registered command whose execution is recorded. Permission checks
     * run through the real Command logic against the fake user (always true).
     */
    public final class RecordingCommand extends Command {

        public final List<String> executedWith = new ArrayList<>();

        public RecordingCommand(@NotNull String name) {
            super(List.of(name), name, GuiTestKit.this.plugin);
        }

        @Override
        public void execute(@NotNull CommandUser executor, @NotNull String[] args) {
            executedWith.add(String.join("|", args));
        }
    }

    /**
     * A real in-claim command whose executions are recorded, for scoped flows.
     */
    public final class RecordingInClaimCommand extends net.william278.huskclaims.command.InClaimCommand {

        public final List<String> executedWith = new ArrayList<>();

        public RecordingInClaimCommand(@NotNull String name) {
            super(List.of(name), name, GuiTestKit.this.plugin);
        }

        @Override
        public void execute(@NotNull OnlineUser executor, @NotNull ClaimWorld world,
                            @NotNull Claim claim, @NotNull String[] args) {
            executedWith.add(String.join("|", args));
        }
    }

    /** Register an in-claim command the controller can find and run scoped. */
    @NotNull
    public RecordingInClaimCommand inClaimCommand(@NotNull String name) {
        final RecordingInClaimCommand c = new RecordingInClaimCommand(name);
        commands.add(c);
        return c;
    }

    /**
     * Install a database fixture for a user, so async name lookups resolve.
     * Also serves the name to the recording host's cache.
     */
    @NotNull
    public SavedUserFixture savedUser(@NotNull UUID uuid, @NotNull String name) {
        final User u = User.of(uuid, name);
        final SavedUser saved = SavedUser.createNew(u, plugin);
        database.byName.put(name, saved);
        database.byUuid.put(uuid, saved);
        host.names.put(uuid, name);
        return new SavedUserFixture(u);
    }

    /** Handle on an installed saved-user fixture. */
    public record SavedUserFixture(@NotNull User user) {
    }

    /** Register a command the controller can find. */
    @NotNull
    public RecordingCommand command(@NotNull String name) {
        final RecordingCommand c = new RecordingCommand(name);
        commands.add(c);
        return c;
    }

    @NotNull
    public FakeOnlineUser user() {
        return new FakeOnlineUser(plugin);
    }

    /**
     * A concrete online user for menu tests: permission always granted, never
     * on cooldown, messages ignored.
     */
    public static class FakeOnlineUser extends OnlineUser {

        public FakeOnlineUser(@NotNull HuskClaims plugin) {
            super("Tester", UUID.fromString("00000000-0000-0000-0000-000000000001"), plugin);
        }

        /** Where the fake user stands; tests may swap it (null = far away, but a claim world resolves). */
        public net.william278.huskclaims.position.Position position =
                net.william278.huskclaims.position.Position.at(9999, 64, 9999, WORLD);

        @Override
        public net.william278.huskclaims.position.Position getPosition() {
            return position;
        }

        @Override
        public void sendPluginMessage(@NotNull String channel, byte[] message) {
        }

        @Override
        public boolean hasPermission(@NotNull String permission, boolean isDefault) {
            return true;
        }

        @Override
        public boolean hasPermission(@NotNull String permission) {
            return true;
        }

        @Override
        public boolean isHolding(@NotNull net.william278.cloplib.listener.InspectorCallbackProvider.InspectionTool tool) {
            return false;
        }

        @Override
        public Optional<Long> getNumericalPermission(@NotNull String prefix) {
            return Optional.empty();
        }

        @Override
        public boolean isSneaking() {
            return false;
        }

        @Override
        public boolean canSee(@NotNull OnlineUser other) {
            return true;
        }

        @Override
        public net.kyori.adventure.audience.Audience getAudience() {
            return net.kyori.adventure.audience.Audience.empty();
        }

        /** Where this user has been teleported by menu actions. */
        public final java.util.List<net.william278.huskclaims.position.Position> teleports =
                new ArrayList<>();

        @Override
        public void teleport(@NotNull net.william278.huskclaims.position.Position position, boolean instant) {
            teleports.add(position);
        }
    }


    /* Recording database ---------------------------------------------------- */

    /** Records claim-world persistence; everything else is a no-op. */
    public static final class RecordingDatabase extends Database {

        public final List<ClaimWorld> savedWorlds = new ArrayList<>();
        /** name/uuid → saved user, served by getUser lookups (async flows). */
        public final Map<String, SavedUser> byName = new java.util.HashMap<>();
        public final Map<UUID, SavedUser> byUuid = new java.util.HashMap<>();

            protected RecordingDatabase() {
            super(null);
        }

        @Override
        protected void executeScript(@NotNull java.sql.Connection connection, @NotNull String name) {
        }

        @Override
        public void initialize() {
        }

        @Override
        public boolean isCreated() {
            return true;
        }

        @Override
        public int getSchemaVersion() {
            return 0;
        }

        @Override
        public void setSchemaVersion(int version) {
        }

        @Override
        public Optional<SavedUser> getUser(@NotNull UUID uuid) {
            return Optional.ofNullable(byUuid.get(uuid));
        }

        @Override
        public Optional<SavedUser> getUser(@NotNull String username) {
            return Optional.ofNullable(byName.get(username));
        }

        @Override
        public List<SavedUser> getInactiveUsers(long daysInactive) {
            return List.of();
        }

        @Override
        public void createUser(@NotNull SavedUser user) {
        }

        @Override
        public void updateUser(@NotNull SavedUser user) {
        }

        @Override
        public void createOrUpdateUser(@NotNull SavedUser user) {
        }

        @Override
        public Set<UserGroup> getUserGroups(@NotNull UUID uuid) {
            return Set.of();
        }

        @Override
        public Map<UUID, Set<UserGroup>> getAllUserGroups() {
            return Map.of();
        }

        @Override
        public void addUserGroup(@NotNull UserGroup group) {
        }

        @Override
        public void updateUserGroup(@NotNull UUID owner, @NotNull String name, @NotNull UserGroup newGroup) {
        }

        @Override
        public void deleteUserGroup(@NotNull UserGroup group) {
        }

        @Override
        public Map<World, ClaimWorld> getClaimWorlds(@NotNull String server) {
            return Map.of();
        }

        @Override
        public Map<ServerWorld, ClaimWorld> getAllClaimWorlds() {
            return Map.of();
        }

        @Override
        public ClaimWorld createClaimWorld(@NotNull World world) {
            return null;
        }

        @Override
        public void updateClaimWorld(@NotNull ClaimWorld claimWorld) {
            savedWorlds.add(claimWorld);
        }

        @Override
        public void close() {
        }
    }

    /* Recording host ------------------------------------------------------- */

    /**
     * Records everything a menu asks of the platform.
     */
    public static final class RecordingHost implements MenuHost {
        public final List<MenuView> opened = new ArrayList<>();
        public final List<MenuView> refreshed = new ArrayList<>();
        public final List<TextPrompt> prompts = new ArrayList<>();
        public final List<UUID> closed = new ArrayList<>();
        public int toolHandouts;

        @Override
        public void open(@NotNull OnlineUser u, @NotNull MenuView v) { opened.add(v); }

        @Override
        public void refresh(@NotNull OnlineUser u, @NotNull MenuView v) { refreshed.add(v); }

        @Override
        public void close(@NotNull OnlineUser u) { closed.add(u.getUuid()); }

        @Override
        public void prompt(@NotNull OnlineUser u, @NotNull TextPrompt p) { prompts.add(p); }

        @Override
        public void giveClaimTool(@NotNull OnlineUser u) { toolHandouts++; }

        @Override
        public boolean isItem(@NotNull String key) { return !key.endsWith(":not_an_item"); }

        @Override
        public void runFor(@NotNull OnlineUser u, @NotNull Runnable r) { r.run(); }

        @Override
        public boolean isOnline(@NotNull OnlineUser u) { return true; }

        public final Map<UUID, String> names = new java.util.HashMap<>();

        @Override
        @Nullable
        public String cachedUserName(@NotNull UUID uuid) { return names.get(uuid); }

        @NotNull
        public MenuView last() { return opened.get(opened.size() - 1); }
    }

}
