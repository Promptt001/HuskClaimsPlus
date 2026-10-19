# Changelog

All notable changes to HuskClaimsPlus. HuskClaimsPlus has its own version line; 1.0.0 is
based on HuskClaims 1.5.11 (upstream `master` at `51e80fd`; the latest upstream release is
1.5.10).

## 1.0.0 — 2026-10-19

First release of HuskClaimsPlus, for **Paper 26.2** and **Fabric 26.2** (dedicated
servers, singleplayer and Open to LAN). Drop-in upgrade from HuskClaims 1.5.x and from
HuskClaims with Visits 1.5.11-visits.1: same plugin name, data folder, database, commands
and permission nodes.

Every change from upstream, file by file:
[compare 51e80fd...1.0.0](https://github.com/Promptt001/HuskClaimsPlus/compare/51e80fd...1.0.0).
Tests: 250 automated test methods (upstream has 3), plus a Fabric game test that runs a
real singleplayer world and an Open to LAN session.

### Added: Claim Menu (Paper and Fabric)
- `/claimgui` and `/huskclaimgui` open the Claim Menu (`gui.enabled`, default on;
  permission `huskclaims.command.huskclaimgui`, everyone).
- Main menu: Public Claims (while warps are enabled), Friends & Trust, My Claims, Claim
  tool, Claim Blocks.
- My Claims: paged, sortable, child claims, Create Claim. Claim settings: rename, privacy,
  Friends & Trust, Public Permissions, trusted players, child claims, resize (edge picker
  and stepper), claim warp, unclaim. Friends & Trust hub, trusted-players list and
  trust-level picker; in the menu, trusting everyone is capped at
  `gui.public_access.max_level` (default `container`; the `/trust` command is unchanged).
  Public Permissions: single actions for everyone (each needs
  `huskclaims.flag.<action>` and `huskclaims.gui.public_permissions`) and the warp's
  public/trusted-only switch. Claim Blocks: balance, buy, gift. Public Claims: warp
  browser. A confirmation screen before anything that changes a claim or spends blocks.
- Create and resize from the menu with a live claim-block cost; by default without
  holding the claim tool (`gui.create_claim.bypass_tool_requirement`); the `claim`
  command permission is always required.
- Claim tool button (`huskclaims.gui.claim_tool`): 60 s cooldown that survives relogging
  (`gui.claim_tool_cooldown_seconds`), never while you already carry one; a handed-out
  tool cannot be smelted, used as fuel, or repaired into an unmarked tool.
- Every button runs the real HuskClaims command or claim editor with the same permission,
  cooldown and trust-privilege checks, against the claim shown in the menu, looked up
  again on every click. Buttons do not ask for input when you lack the permission the
  command will check.
- Configurable layout: slots, rows, icons (`gui.icon_materials`) and filler. A layout
  version backs up `config.yml` before new defaults replace changed layout settings.
- Paper: inventory menus, chat prompts (`gui.input_timeout_seconds`; `gui.allow_name_prefix`
  for Geyser/Floodgate name prefixes). Fabric: server-side screens (sgui) and anvil
  prompts; vanilla clients need nothing.
- 373 new message keys (350 for the menu) in all 7 locale files, English text
  for now. Existing message files gain missing keys automatically; when the bundled menu
  texts change (`gui_locale_version`), menu texts are refreshed after a backup. Other
  customised messages are never touched.

### Added: claim warps
- `/claimwarp set <name>`, `/claimwarp rename <name>`, `/claimwarp unset`: claim owner or
  `MANAGE_WARP` trust (`huskclaims.command.claimwarp`; admin override
  `huskclaims.command.claimwarp.other`). A warp set inside a child claim belongs to the
  parent claim.
- `/visit <name>` (`huskclaims.command.visit`) and `/visits [page]`
  (`huskclaims.command.visits`) for public warps. The Public Claims menu lists public warps
  plus the trusted-only warps the player may use.
- Safe-landing check when a warp is set and before every visit; ban, private-claim and
  trusted-only checks before every visit; a warp left outside its claim after a resize is
  refused; per-player visit cooldown (`claims.warps.visit_cooldown_seconds`, 3 s).
- Public or trusted-only warps; server-unique names made of letters, numbers, `_`, `.` and
  `-` (`claims.warps.max_name_length` 32, `claims.warps.enforce_unique_names` on).
- New trust privilege `MANAGE_WARP`, included in the default Manage level.
- Stored with the claim (`claim_warp`); claims without a warp are unchanged.
- `claims.warps.enabled` (default on) removes `/claimwarp`, `/visit` and `/visits` when off.

### Added: claim names
- Top-level player claims get default names (`<owner>_<NN>`) you can rename from the menu;
  setting a warp names the claim after it unless you chose a name. Stored in claim metadata.

### Added: Fabric for Minecraft 26.2
- `fabric/26.2` build node on Mojang-named 26.x (a `fabric/26.3` node exists but is not
  released); Java 25; Fabric Loader 0.19.3+; Fabric API. Loads on 26.2.x hotfixes (`~26.2`).
- Runs on dedicated servers **and** on the singleplayer client (`environment: "*"`):
  - a per-world database on integrated servers (`saves/<world>/huskclaims/`), one shared
    config per installation;
  - LAN permission policy: the world owner always has operator rights in HuskClaims; LAN
    guests never inherit them from "Allow Commands" (`fabric.yml`:
    `lan_guests_follow_operator_level`, default false);
  - a full session reset between worlds (listeners, commands, hooks, scheduler, caches,
    menus), with background work drained before the database closes;
  - cross-server messaging is off on singleplayer/LAN worlds.
- Bundles a ClopLib fork for 26.2 as a nested jar
  ([Promptt001/ClopLib 2.0.13.1](https://github.com/Promptt001/ClopLib/releases/tag/2.0.13.1)).

### Added: Paper 26.2
- Builds against Paper API 26.2 (Java 25).
- Vendored MineDown, patched for Adventure 5 (clickable chat messages crashed on 26.2).

### Changed
- New installs: the default Access and Container trust levels no longer allow placing or
  breaking vehicles (Build and Manage still do). Existing `trust_levels.yml` files are not
  changed.
- Update checks use HuskClaimsPlus's GitHub releases; no bStats metrics are sent.
- Display name HuskClaimsPlus in descriptors, logs, config headers and the about screen;
  plugin name, mod id, data folders, commands and permission nodes are unchanged. Support
  links point to this repository.
- Removed the Fabric 1.21.1/1.21.4/1.21.5/1.21.7 build nodes (use upstream for those).
- Upstream's CI and publishing workflows are replaced by a build-and-test workflow.

### Fixed (defects inherited from upstream)
- SQLite: every claim world created in one session got database id 1 (the row count was
  used as the id), so claim worlds could overwrite each other's rows. Now uses `RETURNING id`.
- Fabric: item placement (blocks, buckets, vehicles, spawn eggs) was checked at the
  player's position instead of the targeted block, letting players outside a claim place
  into it (and blocking players inside from placing anywhere).
- Fabric: block placement checked the wrong block for clicks on east, south and top faces.
- Fabric: the claim outline was drawn one block towards west/north.
- Fabric: seeds and crops on a block click were checked as block placement, not farming.
- `/huskclaims status` crashed on server versions such as `26.2.build.133-stable`; plugin
  versions with build metadata (`1.0.0+mc.26.2`) are parsed safely (upstream's Fabric
  version parsing fails on a release-shaped version).
- Build: version tokens in `plugin.yml` went stale across builds and failed on Gradle 9
  (non-string token map).

### Included from upstream HuskClaims since 1.5.10
The latest HuskClaims release is 1.5.10 (July 2025). HuskClaimsPlus 1.0.0 is built on
upstream `master` at `51e80fd` (August 2026), so servers coming from 1.5.10 also get 36
upstream commits that no HuskClaims release contains yet. The ones a server owner or player
will notice (upstream pull request numbers in brackets):

- Added `/reduceclaim <blocks>`, the opposite of `/extendclaim`
  (`huskclaims.command.reduceclaim`, everyone; `.other` for other players' claims) [#480].
- Added MongoDB as a database type [#474].
- Added `claims.maximum_claim_radius` (default 100) for `/claim <radius>` [#456].
- `/ignoreclaims [on|off]` takes an explicit state, and what it bypasses is now controlled
  by permissions: `huskclaims.command.ignoreclaims.bans`, `.private` and
  `.operations.<operation_type>` (or `.operations.*`) [#439]. See the upgrade notes.
- Transferring a claim also moves its claim-block balance to the new owner [#447].
- Claims carry a persistent metadata map [#498] (HuskClaimsPlus stores claim names in it).
- `claims.unclaimable_worlds` accepts wildcards such as `speedrun_*` [#505].
- PlaceholderAPI: formatted and accrued claim-block placeholders [#446, #492].
- Player names in commands are case-insensitive [#485]; vanished players are hidden from
  tab completion [#467].
- Folia support [#340, #468] (not tested by HuskClaimsPlus).
- API: `BukkitHourlyClaimBlocksEvent` [#495].
- Fixed: end crystal explosions ignored the `explosion_damage_terrain` flag [#494];
  the `/transferclaim` permission check was inverted [#488]; `/extendclaim` showed the
  total blocks instead of the blocks still needed [#483]; water flow and similar operations
  were blocked between two claims of the same owner [#482]; the highest hourly claim-block
  permission was not always the one applied [#481]; SQLite concurrency and a database lookup
  race [#491, #496]; a claim scheduler race and an offset in claim outlines [#438].

The full upstream list: `git log 1.5.10..51e80fd` in this repository, or the
[upstream compare view](https://github.com/WiIIiam278/HuskClaims/compare/1.5.10...51e80fd).

### Known issues
- Menu and warp texts are English in every locale file.
- Fabric: a claim made with the golden shovel gets its default name the first time you
  open My Claims (on Paper, at creation).
- Gifting claim blocks needs `huskclaims.command.claimblocks.gift`, which is
  operator-only by default (as in upstream).
- Folia is not tested.
- **Fabric: HuskClaimsPlus 1.0.0 does not start next to HuskHomes for Fabric.** On Fabric every
  mod shares one class loader, so a file at the same resource path is resolved once for the
  whole server. Both plugins ship `database/sqlite_schema.sql` (and the MySQL/MariaDB schemas,
  the `migrations/0-*-add_metadata_table.sql` scripts, `fabric.mod.json` and the `locales/*.yml`
  files) under identical paths inside their bundled common jars. HuskHomes therefore reads
  HuskClaims' schema and fails at startup with
  `No enum constant net.william278.huskhomes.database.Database.Table.USER_DATA`; it logs
  "HuskHomes has failed to enable successfully" and HuskClaimsPlus keeps running with its
  HuskHomes hook unregistered. Reproduced with HuskHomes 4.11 (Fabric 26.2): alone it enables
  cleanly, with HuskClaimsPlus installed it does not. **Paper is not affected** (each plugin
  has its own class loader). Fix in 1.0.1: relocate the schemas and locale files inside the
  nested jar. Until then, run HuskHomes on a Paper server or leave it out of Fabric mods.
- Integrations kept from upstream were not re-tested on 26.2 for this release: LuckPerms,
  Vault and Impactor economy, PlaceholderAPI, Plan, HuskTowns, WorldGuard, Geyser/Floodgate,
  BlueMap, Dynmap and Pl3xMap, cross-server sync (MySQL/MariaDB/MongoDB, Redis or plugin
  messaging) and the GriefPrevention importer. Their code is upstream's; please report
  anything that breaks. HuskHomes on Fabric was tested and is listed above under Known
  issues; HuskHomes on Paper was not re-tested.
- Cross-server messaging is not available on singleplayer/LAN worlds (by design).

### Upgrade notes
- Drop-in from HuskClaims 1.5.x. New config sections and messages are added on first start.
- From HuskClaims 1.5.10: staff who use `/ignoreclaims` through a permissions plugin, rather
  than as server operators, now also need `huskclaims.command.ignoreclaims.operations.*`
  (and `.bans` and `.private` if wanted). Without them `/ignoreclaims` turns on but
  bypasses nothing. Operators are not affected.
- From HuskClaims 1.5.10: `/reduceclaim` is new and available to everyone by default, like
  `/extendclaim`. Deny `huskclaims.command.reduceclaim` if you do not want it.
- To let Manage-trusted players manage warps on an existing server, add `MANAGE_WARP` to the
  `manage` level's `privileges` in `trust_levels.yml`. Remove it again before switching back
  to upstream.
- All servers sharing a database or broker must run the same HuskClaimsPlus version;
  HuskClaimsPlus 1.0 and upstream 1.5 use different network channels on purpose.
- Switching back to upstream drops warps when upstream next saves a claim world.

## History
- **1.5.11-visits.1** (2026-10-13, "HuskClaims with Visits", repository since replaced):
  claim warps for Paper 26.2 only (`/claimwarp set|rename|unset` with
  `huskclaims.command.claimwarp`, `/visit` with `huskclaims.command.visit`, `/visits` with
  `huskclaims.command.visits`; admin override `huskclaims.command.claimwarp.other`).
  Superseded by 1.0.0, which keeps its data.
