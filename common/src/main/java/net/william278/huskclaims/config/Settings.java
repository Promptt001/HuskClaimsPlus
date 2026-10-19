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

package net.william278.huskclaims.config;

import com.google.common.collect.Lists;
import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.*;
import net.kyori.adventure.text.format.NamedTextColor;
import net.william278.cloplib.listener.InspectorCallbackProvider;
import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.ClaimingMode;
import net.william278.huskclaims.database.Database;
import net.william278.huskclaims.highlighter.Highlightable;
import net.william278.huskclaims.network.Broker;
import net.william278.huskclaims.position.World;
import net.william278.huskclaims.util.BlockProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Pattern;

@SuppressWarnings("FieldMayBeFinal")
@Getter
@Configuration
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Settings {

    static final String CONFIG_HEADER = """
            ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
            ┃ HuskClaimsPlus - Config (26.2)┃
            ┃ Based on HuskClaims (Apache-2)┃
            ┃ by William278. Fork by Promptt001
            ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛
            ┣╸ HuskClaimsPlus: https://github.com/Promptt001/HuskClaimsPlus
            ┣╸ Config Help: https://william278.net/docs/huskclaims/config/
            ┗╸ Documentation: https://william278.net/docs/huskclaims/""";

    @Comment("Locale of the default language file to use. Docs: https://william278.net/docs/huskclaims/translations")
    private String language = Locales.DEFAULT_LOCALE;

    @Comment("Whether to automatically check for plugin updates on startup")
    private boolean checkForUpdates = true;

    @Comment("How many seconds players must wait between executing HuskClaims commands (floating point number)")
    private float commandCooldownSeconds = 0.5f;

    @Comment("Database settings")
    private DatabaseSettings database = new DatabaseSettings();

    @Getter
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class DatabaseSettings {

        @Comment("Type of database to use (SQLITE, MYSQL, MARIADB or MONGODB)")
        private Database.Type type = Database.Type.SQLITE;

        @Comment("Specify credentials here if you are using MYSQL, MARIADB or MONGODB")
        private DatabaseCredentials credentials = new DatabaseCredentials();

        @Comment({"MYSQL / MARIADB database Hikari connection pool properties",
                "Don't modify this unless you know what you're doing!"})
        private PoolOptions poolOptions = new PoolOptions();

        @Comment("Names of tables to use on your database. Don't modify this unless you know what you're doing!")
        private Map<Database.Table, String> tableNames = new TreeMap<>(Map.of(
                Database.Table.META_DATA, Database.Table.META_DATA.getDefaultName(),
                Database.Table.USER_DATA, Database.Table.USER_DATA.getDefaultName(),
                Database.Table.USER_GROUP_DATA, Database.Table.USER_GROUP_DATA.getDefaultName(),
                Database.Table.CLAIM_DATA, Database.Table.CLAIM_DATA.getDefaultName()
        ));

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class DatabaseCredentials {
            @Comment("Database host. Default port: 3306 for MySQL/MariaDB, 27017 for MongoDB")
            private String host = "localhost";
            @Comment("Database port. Default: 3306 for MySQL/MariaDB, 27017 for MongoDB")
            int port = 3306;
            String database = "huskclaims";
            String username = "root";
            String password = "pa55w0rd";
            @Comment("Connection parameters. For MySQL/MariaDB: JDBC parameters. For MongoDB: connection string options (e.g. ?authSource=admin&retryWrites=true)")
            String parameters = "?autoReconnect=true&useSSL=false&useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true";
        }

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class PoolOptions {
            private int size = 12;
            private int idle = 12;
            private long lifetime = 1800000;
            private long keepAlive = 30000;
            private long timeout = 20000;
        }

        @NotNull
        public String getTableName(@NotNull Database.Table tableName) {
            return Optional.ofNullable(tableNames.get(tableName)).orElse(tableName.getDefaultName());
        }
    }

    @Comment("Cross-server settings")
    private CrossServerSettings crossServer = new CrossServerSettings();

    @Getter
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CrossServerSettings {
        @Comment("Whether to enable cross-server mode")
        private boolean enabled = false;

        @Comment({"The cluster ID, for if you're networking multiple separate groups of HuskClaims-enabled servers.",
                "Do not change unless you know what you're doing"})
        private String clusterId = "main";

        @Comment("Type of network message broker to ues for data synchronization (PLUGIN_MESSAGE or REDIS)")
        private Broker.Type brokerType = Broker.Type.PLUGIN_MESSAGE;

        @Comment("Settings for if you're using REDIS as your message broker")
        private RedisSettings redis = new RedisSettings();

        @Getter
        @Configuration
        @NoArgsConstructor
        public static class RedisSettings {
            private String host = "localhost";
            private int port = 6379;
            @Comment("Password for your Redis server. Leave blank if you're not using a password.")
            private String password = "";
            private boolean useSsl = false;

            @Comment({"Settings for if you're using Redis Sentinels.",
                    "If you're not sure what this is, please ignore this section."})
            private SentinelSettings sentinel = new SentinelSettings();

            @Getter
            @Configuration
            @NoArgsConstructor
            public static class SentinelSettings {
                private String masterName = "";
                @Comment("List of host:port pairs")
                private List<String> nodes = Lists.newArrayList();
                private String password = "";
            }
        }
    }

    @Comment("Claim flags & world settings")
    private ClaimSettings claims = new ClaimSettings();

    @Getter
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ClaimSettings {
        @Comment("Default operation type flags for new regular claims")
        private List<OperationType> defaultFlags = List.of(
                OperationType.PLAYER_DAMAGE_MONSTER,
                OperationType.EXPLOSION_DAMAGE_ENTITY,
                OperationType.MONSTER_SPAWN,
                OperationType.PASSIVE_MOB_SPAWN
        );

        @Comment("Default operation type flags for new admin claims")
        private List<OperationType> adminFlags = List.of(
                OperationType.PLAYER_DAMAGE_MONSTER,
                OperationType.EXPLOSION_DAMAGE_ENTITY,
                OperationType.MONSTER_SPAWN,
                OperationType.PASSIVE_MOB_SPAWN
        );

        @Comment("List of enabled claim types. Must include at least the regular \"CLAIMS\" mode")
        private List<ClaimingMode> enabledClaimingModes = List.of(
                ClaimingMode.values() // Allow all claiming modes
        );

        @Comment({"Default operation type flags for the wilderness (outside claims)",
                "To modify existing worlds, use /claimflags set <flag> <true/false> while standing outside a claim."})
        private List<OperationType> wildernessRules = Lists.newArrayList(
                OperationType.getRegistered() // Allow all operation types
        );

        @Comment("List of worlds where users cannot claim")
        private List<String> unclaimableWorlds = List.of();

        @Comment("The number of claim blocks a user gets when they first join the server")
        private long startingClaimBlocks = 100;

        @Comment({"The number of claim blocks a user gets hourly.",
                "Override with the \"huskclaims.hourly_blocks.(amount)\" permission"})
        private long hourlyClaimBlocks = 100;

        @Comment({"The maximum number of claim blocks a user can have.",
                "Override with the \"huskclaims.max_claim_blocks.(amount)\" permission"})
        private long maximumClaimBlocks = 9999999;

        @Comment({"The maximum amount of land, in claim blocks, that can be affected at once by /claim, /extendclaim, /reduceclaim",
                "or the claim tool. Increasing this can affect performance when users claim lots of land at once. "})
        private long maximumClaimActionSize = 45000;

        @Comment({"The maximum radius, in blocks, that a user can claim at once using /claim <radius>"})
        private long maximumClaimRadius = 100;

        @Comment("Claim inspection tool (right click with this to inspect claims)")
        private String inspectionTool = "minecraft:stick";

        @Comment("Model data settings for the inspection tool")
        private ModelDataSettings inspectionToolModelData = new ModelDataSettings();

        @Comment("Claim creation & resize tool (right click with this to create/resize claims)")
        private String claimTool = "minecraft:golden_shovel";

        @Comment("Model data settings for the claim tool")
        private ModelDataSettings claimToolModelData = new ModelDataSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class ModelDataSettings {
            private boolean required = false;
            private int modelData = 0;
        }

        @Comment("Require players to hold the claim tool to use claim commands (e.g. /claim <radius>, /extendclaim, /reduceclaim)")
        private boolean requireToolForCommands = true;

        @Comment("Minimum size of claims along one edge. This does not affect child or admin claims.")
        private int minimumClaimSize = 5;

        @Comment("Max range of inspector tools")
        private int inspectionDistance = 64;

        @Comment("Whether to allow inspecting nearby claims by sneaking when using the inspection tool")
        private boolean allowNearbyClaimInspection = true;

        @Comment("Whether to require confirmation when deleting claims that have children")
        private boolean confirmDeletingParentClaims = true;

        @Comment("Whether to send a message when a player enters a claim")
        private boolean sendEntryMessage = false;

        @Comment("Whether to send a message when a player exits a claim")
        private boolean sendExitMessage = false;

        @Comment("Whether to enable the /trapped command. Install HuskHomes to require a warmup before teleporting.")
        private boolean trappedCommand = true;

        @Comment("Settings for automatically removing claims made by now-inactive users")
        private InactivityPruningSettings inactivityPruning = new InactivityPruningSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class InactivityPruningSettings {

            @Comment("Whether to delete all claims made by users marked as inactive. (Warning: Dangerous!)")
            private boolean enabled = false;

            @Comment("The number of days a user must not log on for to be marked as inactive (Minimum: 1)")
            private long inactiveDays = 60;

            @Comment("List of worlds to exclude from being pruned.")
            private List<String> excludedWorlds = Lists.newArrayList();

            @Comment("List of users (by either UUID or username) to exclude from inactive claim pruning")
            private List<String> excludedUsers = Lists.newArrayList();

        }

        @Comment("Settings for banning users from claims")
        private BanSettings bans = new BanSettings();

        @Comment({"Settings for public claim warps (teleport anchors set by claim owners, visited via /visit)",
                "Setting a warp is an explicit opt-in by the claim owner; claims without a warp behave as stock."})
        private ClaimWarpSettings warps = new ClaimWarpSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class ClaimWarpSettings {

            @Comment("Whether to enable claim warps (/claimwarp set/rename/unset, /visit, /visits)")
            private boolean enabled = true;

            @Comment("The maximum length of a warp name")
            private int maxNameLength = 32;

            @Comment("Whether warp names must be unique across the whole server (recommended; /visit lookup is by name)")
            private boolean enforceUniqueNames = true;

            @Comment("Command cooldown, in seconds, between warp visits per player (0 to disable)")
            private float visitCooldownSeconds = 3.0f;

        }

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class BanSettings {

            @Comment({"Whether to let users ban others from their claims (prevent them from entering) using /claimban",
                    "Also requires the MANAGE_BANS privilege (by default, restricted to those with 'manage' trust)"})
            private boolean enabled = false;

            @Comment({"Whether to let users set their claim claims to private (prevent others from entering) using /claimprivate",
                    "Also requires the MANAGE_BANS privilege (by default, restricted to those with 'manage' trust)"})
            private boolean privateClaims = false;

        }

        public boolean isWorldUnclaimable(@NotNull World world) {
            return unclaimableWorlds.stream().anyMatch(pattern -> world.getName().matches(
                    "(?i)" + Pattern.quote(pattern).replace("*", "\\E.*\\Q")
            ));
        }

        @NotNull
        public InspectorCallbackProvider.InspectionTool getInspectionToolData() {
            return new InspectorCallbackProvider.InspectionTool(
                    inspectionTool, inspectionToolModelData.isRequired(), inspectionToolModelData.getModelData()
            );
        }

        @NotNull
        public InspectorCallbackProvider.InspectionTool getClaimToolData() {
            return new InspectorCallbackProvider.InspectionTool(
                    claimTool, claimToolModelData.isRequired(), claimToolModelData.getModelData()
            );
        }

    }

    @Comment("Groups of operations that can be toggled on/off in claims")
    public List<OperationGroup> operationGroups = List.of(
            OperationGroup.builder()
                    .name("Claim Explosions")
                    .description("Toggle whether explosions can damage terrain in claims")
                    .allowedOperations(Set.of(
                            OperationType.EXPLOSION_DAMAGE_TERRAIN,
                            OperationType.MONSTER_DAMAGE_TERRAIN
                    ))
                    .toggleCommandAliases(List.of("claimexplosions"))
                    .build()
    );

    @Getter
    @Builder
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class OperationGroup {
        private String name;
        private String description;
        private List<String> toggleCommandAliases;
        private Set<OperationType> allowedOperations;
    }

    @Comment("Settings for user groups, letting users quickly manage trust for groups of multiple players at once")
    private UserGroupSettings userGroups = new UserGroupSettings();

    @Getter
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class UserGroupSettings {
        @Comment("Whether to enable user groups")
        private boolean enabled = true;

        @Comment("The prefix to use when specifying a group in a trust command (e.g. /trust @groupname)")
        private String groupSpecifierPrefix = "@";

        @Comment("Whether to restrict group names with a regex filter")
        private boolean restrictGroupNames = true;

        @Comment("Regex for group names")
        private String groupNameRegex = "[a-zA-Z0-9-_]*";

        @Comment("Max members per group")
        private int maxMembersPerGroup = 10;

        @Comment("Max groups per player")
        private int maxGroupsPerPlayer = 3;
    }

    @Comment("Settings for trust tags, special representations of things you can trust in a claim")
    private TrustTagSettings trustTags = new TrustTagSettings();

    @Getter
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class TrustTagSettings {
        @Comment("Whether to enable trust tags")
        private boolean enabled = true;

        @Comment("The prefix to use when specifying a trust tag in a trust command (e.g. /trust #tagname)")
        private String tagSpecifierPrefix = "#";

        @Comment("The name of the default public access tag (to let anyone access certain claim levels)")
        private String publicAccessName = "public";

    }

    @Comment("Settings for the claim inspection/creation highlighter")
    private HighlighterSettings highlighter = new HighlighterSettings();

    @Getter
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class HighlighterSettings {
        @Comment("Whether to use block display entities for glowing (requires Paper 1.19.4+)")
        private boolean blockDisplays = true;

        @Comment("If using block displays, whether highlights should use a glow effect (requires Paper 1.19.4+)")
        private boolean glowEffect = true;

        @Comment("The blocks to use when highlighting claims")
        private Map<Highlightable.Type, String> blockTypes = new TreeMap<>(Map.of(
                Highlightable.Type.EDGE, "minecraft:gold_block",
                Highlightable.Type.CORNER, "minecraft:glowstone",
                Highlightable.Type.ADMIN_CORNER, "minecraft:glowstone",
                Highlightable.Type.ADMIN_EDGE, "minecraft:pumpkin",
                Highlightable.Type.CHILD_CORNER, "minecraft:iron_block",
                Highlightable.Type.CHILD_EDGE, "minecraft:white_wool",
                Highlightable.Type.OVERLAP_CORNER, "minecraft:red_nether_bricks",
                Highlightable.Type.OVERLAP_EDGE, "minecraft:netherrack",
                Highlightable.Type.SELECTION, "minecraft:diamond_block"
        ));

        @NotNull
        public BlockProvider.MaterialBlock getBlock(@NotNull Highlightable.Type type,
                                                    @NotNull HuskClaims plugin) {
            return Optional.ofNullable(blockTypes.get(type))
                    .map(plugin::getBlockFor)
                    .orElse(plugin.getBlockFor("minecraft:yellow_concrete"));
        }

        @Comment("The color of the glow effect used for blocks when highlighting claims")
        private Map<Highlightable.Type, GlowColor> glowColors = new TreeMap<>(Map.of(
                Highlightable.Type.EDGE, GlowColor.YELLOW,
                Highlightable.Type.CORNER, GlowColor.YELLOW,
                Highlightable.Type.ADMIN_CORNER, GlowColor.ORANGE,
                Highlightable.Type.ADMIN_EDGE, GlowColor.ORANGE,
                Highlightable.Type.CHILD_CORNER, GlowColor.SILVER,
                Highlightable.Type.CHILD_EDGE, GlowColor.SILVER,
                Highlightable.Type.OVERLAP_CORNER, GlowColor.RED,
                Highlightable.Type.OVERLAP_EDGE, GlowColor.RED,
                Highlightable.Type.SELECTION, GlowColor.AQUA
        ));

        @NotNull
        public Settings.HighlighterSettings.GlowColor getGlowColor(@NotNull Highlightable.Type type) {
            return Optional.ofNullable(glowColors.get(type)).orElse(GlowColor.WHITE);
        }

        @Getter
        public enum GlowColor {

            WHITE(16777215),
            SILVER(12632256),
            GRAY(8421504),
            BLACK(0),
            RED(16711680),
            MAROON(8388608),
            YELLOW(16776960),
            OLIVE(8421376),
            LIME(65280),
            GREEN(32768),
            AQUA(65535),
            TEAL(32896),
            BLUE(255),
            NAVY(128),
            FUCHSIA(16711935),
            PURPLE(8388736),
            ORANGE(16753920);

            private final int rgb;

            GlowColor(int rgb) {
                this.rgb = rgb;
            }

            public int getArgb() {
                return (0xFF << 24) | rgb;
            }
        }
    }

    @Comment("Settings for protecting tamed animals (pets). Docs: https://william278.net/docs/huskclaims/pets")
    private PetSettings pets = new PetSettings();

    @Getter
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class PetSettings {

        @Comment("Whether to enable protecting tamed animals to only be harmed/used by their owner")
        private boolean enabled = true;

    }

    @Comment("Settings for moderation tools")
    private ModerationSettings moderation = new ModerationSettings();

    @Getter
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ModerationSettings {

        private SignSettings signs = new SignSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class SignSettings {
            @Comment("Whether to notify users with /signspy on when signs are placed/edited. "
                    + "Requires Minecraft 1.19.4+")
            private boolean notifyModerators = true;

            @Comment("Whether to filter messages")
            private boolean filterMessages = false;

            @Comment("Whether sign notifications should be limited to just filtered signs")
            private boolean onlyNotifyIfFiltered = false;

            @Comment("Single character to replace filtered message content with")
            private char replacementCharacter = '#';

            @Comment("List of words to filter out of signs")
            private List<String> filteredWords = List.of();
        }

        private DropSettings drops = new DropSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class DropSettings {
            @Comment("Whether to lock ground items dropped by players when they die from being picked up by others")
            private boolean lockItems = true;

            @Comment("Whether to also prevent death drops from being destroyed by lava, fire, cacti, etc.")
            private boolean preventDestruction = true;
        }

    }

    @Comment({"Settings for the claim GUI, opened with /claimgui (or /huskclaimgui)",
            "Slots count from 0, left to right and top to bottom: the top row is 0-8, the next row 9-17, and so on.",
            "See https://github.com/Promptt001/HuskClaimsPlus/blob/main/docs/Claim-Menu.md for every menu, icon key and permission."})
    private GuiSettings gui = new GuiSettings();

    @Getter
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class GuiSettings {

        /**
         * The layout version this build's defaults belong to. Raise it whenever a default slot,
         * row count or icon changes, so {@link #migrateLayout()} brings older files up to date.
         */
        public static final int CURRENT_LAYOUT_VERSION = 5;

        @Comment({"Layout version of the menu settings below. Don't edit this.",
                "When a plugin update changes the default menu layout, it resets the slots, rows and icons below",
                "and saves your previous config.yml next to this one first.",
                "Whether the GUI is enabled, timeouts, cooldowns and the public access rules are always kept."})
        // A file written before layout versions existed has no value here and reads as 0, which is always older
        private int layoutVersion = 0;

        @Comment("Whether to enable the claim GUI and its /claimgui and /huskclaimgui commands")
        private boolean enabled = true;

        @Comment({"Seconds a player must wait between claim tools from the Claim Menu (0 for no wait)",
                "The wait carries on if the player leaves and rejoins"})
        private int claimToolCooldownSeconds = 60;

        @Comment({"Whether player names typed into GUI prompts may start with one extra character",
                "Turn this on if Bedrock players join through Geyser and Floodgate with a name prefix, such as .Steve"})
        private boolean allowNamePrefix = false;

        @Comment("Seconds before an unanswered chat prompt, such as \"type a player name\", is cancelled (at least 30)")
        private int inputTimeoutSeconds = 60;

        @Comment("Item that fills the empty slots of every menu")
        private String fillerMaterial = "minecraft:black_stained_glass_pane";

        @Comment("The confirmation screen shown before anything that changes a claim or spends claim blocks")
        private ConfirmMenuSettings confirmMenu = new ConfirmMenuSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class ConfirmMenuSettings {
            @Comment("Rows in the confirmation screen (1-6). It grows if a slot below needs more.")
            private int rows = 3;

            @Comment("Slot of the item that describes what will happen")
            private int infoSlot = 13;

            @Comment("Slot of the Confirm button")
            private int confirmSlot = 11;

            @Comment("Slot of the Cancel button, which returns to the previous menu")
            private int cancelSlot = 15;
        }

        @Comment({"The Claim Menu that /claimgui opens",
                "By default its five buttons form a square, like the five on a die, with My Claims in the centre"})
        private MainMenuSettings mainMenu = new MainMenuSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class MainMenuSettings {
            @Comment("Rows in the Claim Menu (1-6). It grows if a slot below needs more.")
            private int rows = 3;

            @Comment("Slot of the Public Claims button (top left of the square), shown while claim warps are enabled")
            private int publicClaimsSlot = 3;

            @Comment("Slot of the Friends & Trust button (top right)")
            private int trustSlot = 5;

            @Comment("Slot of the My Claims button (centre)")
            private int myClaimsSlot = 13;

            @Comment("Slot of the button that hands out a claim tool (bottom left)")
            private int claimToolSlot = 21;

            @Comment("Slot of the Claim Blocks button (bottom right)")
            private int blocksSlot = 23;
        }

        @Comment("The Claim Blocks menu: balance, buying and gifting")
        private BlocksMenuSettings blocksMenu = new BlocksMenuSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class BlocksMenuSettings {
            @Comment("Rows in the Claim Blocks menu (1-6). It grows if a slot below needs more.")
            private int rows = 3;
            @Comment("Slot of the balance button")
            private int balanceSlot = 11;
            @Comment("Slot of the buy button (needs an economy plugin)")
            private int buySlot = 13;
            @Comment("Slot of the gift button")
            private int giftSlot = 15;
            @Comment("Slot of the Back button")
            private int backSlot = 22;
        }

        @Comment("The My Claims menu: one icon per claim you own. It always has six rows; claims fill the top five.")
        private ClaimListMenuSettings claimListMenu = new ClaimListMenuSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class ClaimListMenuSettings {
            @Comment("Slot of the message shown when you have no claims yet")
            private int emptySlot = 22;
            @Comment("Slot of the Back button")
            private int backSlot = 45;
            @Comment("Slot of the previous page button")
            private int prevSlot = 47;
            @Comment("Slot of the Create Claim button")
            private int createSlot = 49;
            @Comment("Slot of the next page button")
            private int nextSlot = 51;
            @Comment("Slot of the sort button")
            private int sortSlot = 53;
        }

        @Comment("The menu for one claim: name, privacy, trust, public permissions, warp, resizing and unclaiming")
        private ClaimSettingsMenuSettings claimSettingsMenu = new ClaimSettingsMenuSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class ClaimSettingsMenuSettings {
            @Comment("Rows in the claim menu (1-6). It grows if a slot below needs more.")
            private int rows = 6;
            @Comment("Slot of the claim's name and details")
            private int infoSlot = 4;
            @Comment("Slot of the Rename button")
            private int renameSlot = 10;
            @Comment("Slot of the private claim switch")
            private int privacySlot = 12;
            @Comment("Slot of the Friends & Trust button")
            private int publicAccessSlot = 14;
            @Comment("Slot of the Public Permissions button")
            private int publicPermissionsSlot = 16;
            @Comment("Slot of the trusted players list")
            private int trusteesSlot = 19;
            @Comment("Slot of the child claims button, shown when the claim has child claims")
            private int childrenSlot = 21;
            @Comment("Slot of the Resize button")
            private int resizeSlot = 23;
            @Comment("Slot of the claim warp button")
            private int warpSlot = 25;
            @Comment("Slot of the Back button")
            private int backSlot = 45;
            @Comment("Slot of the Unclaim button, which asks for confirmation")
            private int unclaimSlot = 53;
        }

        @Comment("The list of players, groups and tags trusted in a claim. It always has six rows.")
        private TrusteesMenuSettings trusteesMenu = new TrusteesMenuSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class TrusteesMenuSettings {
            @Comment("Slot of the message shown when nobody is trusted")
            private int emptySlot = 22;
            @Comment("Slot of the previous page button")
            private int prevSlot = 47;
            @Comment("Slot of the Add Player button")
            private int addSlot = 49;
            @Comment("Slot of the next page button")
            private int nextSlot = 51;
            @Comment("Slot of the Back button")
            private int backSlot = 45;
        }

        @Comment("Which trust level players may give to everyone (the #public tag) in Friends & Trust")
        private PublicAccessSettings publicAccess = new PublicAccessSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class PublicAccessSettings {
            @Comment({"The highest trust level, by id, that players may give to everyone",
                    "Levels that carry privileges, such as managing trustees, can never be given to everyone"})
            private String maxLevel = "container";
        }

        @Comment("The Public Permissions menu: single actions a claim owner may allow for every player")
        private PublicPermissionsSettings publicPermissions = new PublicPermissionsSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class PublicPermissionsSettings {
            @Comment("Whether the Public Permissions menu is available")
            private boolean enabled = true;
            @Comment({"Actions players may allow for everyone, in the order the menu shows them",
                    "Use operation type names. Only list actions a visitor performs, not world events like explosions."})
            private List<String> allowed = List.of(
                    "PLAYER_DAMAGE_MONSTER",       // Killing monsters (allowed by default in new claims)
                    "PLAYER_DAMAGE_ENTITY",        // Hurting and killing animals
                    "BLOCK_INTERACT",              // Using doors, trapdoors and gates
                    "REDSTONE_INTERACT",           // Using levers, buttons and pressure plates
                    "CONTAINER_OPEN",              // Opening chests and other containers
                    "ENTITY_INTERACT",             // Trading with villagers, using item frames and armour stands
                    "FARM_BLOCK_INTERACT",         // Using crops and farmland
                    "FARM_BLOCK_BREAK",            // Harvesting crops
                    "FARM_BLOCK_PLACE",            // Planting crops
                    "ENDER_PEARL_TELEPORT",        // Throwing ender pearls into the claim
                    "PLACE_VEHICLE",               // Placing boats and minecarts
                    "BREAK_VEHICLE"                // Breaking boats and minecarts
            );
            @Comment("Actions that show a confirmation screen before they are allowed for everyone")
            private List<String> confirmOnEnable = List.of("CONTAINER_OPEN", "PLAYER_DAMAGE_PLAYER", "FARM_BLOCK_BREAK");
        }

        @Comment("The Create Claim and Resize menus")
        private CreateClaimSettings createClaim = new CreateClaimSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class CreateClaimSettings {
            @Comment({"Whether claims can be created and resized from the GUI without holding the claim tool,",
                    "even when claims.require_tool_for_commands is on"})
            private boolean bypassToolRequirement = true;
            @Comment("How far the three - and + buttons move an edge, in blocks (shift-click moves five times as far)")
            private List<Integer> steps = List.of(1, 5, 10);
        }

        @Comment({"The item each GUI icon uses. Remove an entry to use the built-in icon.",
                "https://github.com/Promptt001/HuskClaimsPlus/blob/main/docs/Claim-Menu.md lists every icon key, including ones not shown here."})
        private Map<String, String> iconMaterials = Map.ofEntries(
                Map.entry("main.my_claims", "minecraft:grass_block"),
                Map.entry("main.public_claims", "minecraft:ender_eye"),
                Map.entry("main.trust", "minecraft:diamond"),
                Map.entry("main.blocks", "minecraft:gold_ingot"),
                Map.entry("main.claim_tool", "minecraft:golden_shovel"),
                Map.entry("blocks.balance", "minecraft:gold_nugget"),
                Map.entry("blocks.buy", "minecraft:emerald"),
                Map.entry("blocks.gift", "minecraft:player_head"),
                Map.entry("blocks.back", "minecraft:arrow"),
                Map.entry("trust.level.access", "minecraft:oak_door"),
                Map.entry("trust.level.container", "minecraft:chest"),
                Map.entry("trust.level.build", "minecraft:bricks"),
                Map.entry("trust.level.manage", "minecraft:golden_helmet")
        );

        /**
         * Brings the menu layout up to date when the file was written for an older layout.
         * <p>
         * Slots, rows, icons and the filler item are replaced with this version's defaults.
         * Everything that is a server rule rather than a layout choice is kept: whether the GUI
         * is enabled, timeouts, cooldowns, the name prefix option, the tool bypass, and the
         * public access and public permission rules.
         *
         * @return whether anything was reset, in which case the caller saves config.yml
         */
        public boolean migrateLayout() {
            if (layoutVersion >= CURRENT_LAYOUT_VERSION) {
                return false;
            }
            final GuiSettings defaults = new GuiSettings();
            fillerMaterial = defaults.fillerMaterial;
            confirmMenu = defaults.confirmMenu;
            mainMenu = defaults.mainMenu;
            blocksMenu = defaults.blocksMenu;
            claimListMenu = defaults.claimListMenu;
            claimSettingsMenu = defaults.claimSettingsMenu;
            trusteesMenu = defaults.trusteesMenu;
            // The step sizes are layout, but the tool bypass is a server rule
            final boolean bypass = createClaim.bypassToolRequirement;
            createClaim = defaults.createClaim;
            createClaim.bypassToolRequirement = bypass;
            iconMaterials = defaults.iconMaterials;
            layoutVersion = CURRENT_LAYOUT_VERSION;
            return true;
        }
    }

    @Comment("Settings for integration hooks with other plugins")
    private HookSettings hooks = new HookSettings();

    @Getter
    @Configuration
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class HookSettings {

        @Getter(AccessLevel.NONE)
        private LuckPermsHookSettings luckperms = new LuckPermsHookSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class LuckPermsHookSettings {
            @Comment("Whether to hook into LuckPerms for permission group trust tags")
            private boolean enabled = true;

            @Comment("Require users to have the \"huskclaims.trust.luckperms\" permission to use LuckPerms trust tags")
            private boolean trustTagUsePermission = true;

            @Comment("The prefix to use when specifying a LuckPerms group trust tag (e.g. /trust #role/groupname)")
            private String trustTagPrefix = "role/";
        }

        private PlanHookSettings plan = new PlanHookSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class PlanHookSettings {
            @Comment("Whether to hook into Plan to display claim analytics")
            private boolean enabled = true;
        }

        @Getter(AccessLevel.NONE)
        private HuskHomesHookSettings huskhomes = new HuskHomesHookSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class HuskHomesHookSettings {
            @Comment("Whether to hook into HuskHomes for claim teleportation and home restriction")
            private boolean enabled = true;

            @Comment("Whether to register the huskhomes:set_home operation type for restricting home setting in claims")
            private boolean restrictSetHome = true;
        }

        @Getter(AccessLevel.NONE)
        private HuskTownsHookSettings husktowns = new HuskTownsHookSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class HuskTownsHookSettings {
            @Comment("Whether to hook into HuskTowns to disable claiming over town claims")
            private boolean enabled = true;
        }

        private EconomyHookSettings economy = new EconomyHookSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class EconomyHookSettings {
            @Comment("Whether to hook into an economy plugin to allow buying claim blocks")
            private boolean enabled = true;

            @Comment("The cost of buying 1 claim block")
            private double costPerBlock = 1.0;
        }

        private PlaceholderSettings placeholders = new PlaceholderSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class PlaceholderSettings {
            @Comment("Whether to hook into PlaceholderAPI to provide a HuskClaims placeholder expansion")
            private boolean enabled = true;
        }

        @Getter(AccessLevel.NONE)
        private WorldGuardSettings worldguard = new WorldGuardSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class WorldGuardSettings {
            @Comment("Whether to hook into WorldGuard to provide a flag to deny claiming in WorldGuard regions")
            private boolean enabled = true;
        }

        private GeyserSettings geyser = new GeyserSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class GeyserSettings {
            @Comment("Whether to hook into Geyser to let Bedrock users highlight claims. Requires Floodgate installed.")
            private boolean enabled = true;
        }

        private MapHookSettings map = new MapHookSettings();

        @Getter
        @Configuration
        @NoArgsConstructor(access = AccessLevel.PRIVATE)
        public static class MapHookSettings {
            @Comment("Whether to hook into Dynmap, BlueMap, or Pl3xMap to show claims on the map")
            private boolean enabled = true;

            @Comment("What colors to use for types of claims on the map. Remove a pairing to hide claims of that type.")
            private Map<ClaimingMode, String> colors = new TreeMap<>(Map.of(
                    ClaimingMode.CLAIMS, NamedTextColor.YELLOW.asHexString(),
                    ClaimingMode.ADMIN_CLAIMS, NamedTextColor.DARK_RED.asHexString(),
                    ClaimingMode.CHILD_CLAIMS, NamedTextColor.WHITE.asHexString()
            ));

            @Comment("The name of the marker set key")
            private String markerSetName = "Claims";

            @Comment("The label format for markers. '%s' will be replaced with the claim owner's name")
            private String labelFormat = "Claim by %s";
        }

        @NotNull
        public HuskHomesHookSettings getHuskHomes() {
            return huskhomes;
        }

        @NotNull
        public HuskTownsHookSettings getHuskTowns() {
            return husktowns;
        }

        @NotNull
        public WorldGuardSettings getWorldGuard() {
            return worldguard;
        }

        @NotNull
        public LuckPermsHookSettings getLuckPerms() {
            return luckperms;
        }

    }
}
