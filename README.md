<h1 align="center">HuskClaimsPlus</h1>

<p align="center">
  <b>Claims, trust, claim warps and a full Claim Menu for Paper and Fabric — Minecraft 26.2.</b><br/>
  <sub>Based on <a href="https://github.com/WiIIiam278/HuskClaims">HuskClaims</a> 1.5.11 by William278 (Apache-2.0).
  HuskClaimsPlus is an independent fork: it is not made, endorsed or supported by William278.</sub>
</p>

<p align="center">
  <a href="https://github.com/Promptt001/HuskClaimsPlus/releases/latest">Download</a> ·
  <a href="docs/Claim-Menu.md">Claim Menu</a> ·
  <a href="docs/Claim-Warps.md">Claim warps</a> ·
  <a href="docs/Fabric.md">Fabric, singleplayer &amp; LAN</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="https://github.com/Promptt001/HuskClaimsPlus/issues">Issues</a>
</p>

HuskClaimsPlus is HuskClaims, the golden-shovel grief prevention plugin, with the things
players kept asking for:

- a **Claim Menu** (`/claimgui`) you can do everything from,
- **claim warps** so people can visit each other's builds (`/claimwarp`, `/visit`, `/visits`),
- **claim names**, and
- **Fabric** support for dedicated servers, **singleplayer** and **Open to LAN**, where
  friends join with a plain vanilla client.

Everything HuskClaims already does is still here: golden-shovel claiming, trust levels,
child and admin claims, claim blocks, user groups, LuckPerms trust, map hooks, cross-server
sync, and the GriefPrevention importer. HuskClaimsPlus is built on upstream's current
development line, so it also includes upstream work that no HuskClaims release has yet,
such as `/reduceclaim` and MongoDB support (see the
[changelog](CHANGELOG.md#included-from-upstream-huskclaims-since-1510)).

## 🧭 Claim Menu

`/claimgui` opens the Claim Menu: **Public Claims**, **Friends & Trust**, **My Claims**,
**Claim tool** and **Claim Blocks**.

- **My Claims**: every claim you own as an icon, paged and sortable, child claims included.
  Click one to open its settings; you do not have to stand in it. **Create Claim** is here.
- **Claim settings** (per claim): rename · make private · Friends & Trust · Public
  Permissions · trusted players · child claims · resize · claim warp · unclaim.
- **Friends & Trust**: trust and untrust players, groups and everyone, pick a trust level,
  and see every trustee by name and head. In the menu, *everyone* can be trusted up to the
  Container level by default (`gui.public_access.max_level`); the `/trust` command keeps
  upstream's rules.
- **Public Permissions**: let everyone use one action (doors, buttons, farming, and so on)
  without trusting anyone. Also switches the claim's warp between public and trusted-only.
- **Create Claim** and **Resize**: step the size up and down and see the claim-block cost
  live before you confirm. Works without holding the golden shovel by default
  (`gui.create_claim.bypass_tool_requirement`); the `claim` command permission is always
  required.
- **Claim Blocks**: see your balance, buy blocks (needs an economy plugin), and gift blocks
  to a friend (needs `huskclaims.command.claimblocks.gift`, operators by default).
- **Public Claims**: every claim warp you may visit; click to teleport.
- **Claim tool**: hands you the claim tool (60 s cooldown, never a second one). A tool from
  the menu cannot be smelted, used as fuel or repaired into free gold.

**Safety by design.** Anything that changes a claim or spends claim blocks asks you to
confirm. Every click re-checks the real command permission and your trust in *that*
claim, looked up again at click time. A menu left open while its claim is deleted or
transferred says so instead of acting on the wrong land. Menu items can never be taken
out of a menu.

**Make it yours.** Every slot, row count and icon is configurable in `config.yml` under
`gui:`. When an update changes the default layout, your old `config.yml` is backed up first.

On **Paper**, menus are normal inventory screens and you type text in chat (`cancel` to
abort). On **Fabric**, menus are server-side screens and you type text in an anvil (close it
to cancel). **Players never need a client mod.**

## 🌀 Claim warps

Claim owners set one warp per claim: great for shops, farms and community builds. Anyone
may visit a **public** warp, by name or from the Public Claims menu. A **trusted-only**
warp is for the owner, players with Build or Manage trust, and admins; it appears only in
their Public Claims menu, never in `/visit` or `/visits`.

- `/claimwarp set <name>` where you stand · `/claimwarp rename <name>` · `/claimwarp unset`
- `/visit <name>` · `/visits [page]` (clickable list)
- **Anti-trap:** the landing spot is checked when the warp is set **and again before every
  visit**: no lava, fire, magma, cactus, powder snow, cobwebs, portals, mid-air or
  walled-in spots.
- Bans and private claims are respected. A warp left outside its claim after a resize
  stops working until it is moved.
- Switch a warp between public and trusted-only in the claim's Public Permissions menu.
- Names are unique on the server and use letters, numbers, `_`, `.` and `-` only (no
  spaces), up to 32 characters by default.
- Who may manage a warp: the owner, players with the `MANAGE_WARP` trust privilege (given
  to the Manage level in new installs), and admins with `huskclaims.command.claimwarp.other`.

## 🏷️ Claim names

Claims get readable names (`Steve_01`, `Steve_02`, …) that you can change from the menu.
Setting a warp names the claim after the warp unless you chose a name yourself.

## 🧵 Fabric, singleplayer and LAN

- **Dedicated Fabric servers**: the same features as Paper.
- **Singleplayer**: install the mod on your client and claim in your own world. Each
  world keeps its own claims (`saves/<world>/huskclaims/`).
- **Open to LAN**: friends join with a **vanilla** client and can claim and use
  `/claimgui` under the host's rules. LAN guests never become HuskClaims admins, even when
  the world was opened with commands allowed (`fabric.yml` can change that).

## 🛠️ Fixes over upstream HuskClaims

SQLite claim-world ids (claim worlds could overwrite each other), Fabric placement
protection (placement was checked at the player instead of the target block; wrong block on
east/south/top faces), the Fabric claim outline offset, Fabric crop handling, and
`/huskclaims status` on Paper 26.2. Full list in the [changelog](CHANGELOG.md).

## Supported platforms

| Platform | Minecraft | Status |
|---|---|---|
| Paper | 26.2 | ✅ Supported (tested on Paper 26.2 build 133) |
| Fabric dedicated server | 26.2 | ✅ Supported |
| Fabric singleplayer | 26.2 | ✅ Supported |
| Fabric singleplayer, Open to LAN | 26.2 | ✅ Supported (guests need no mod) |
| Paper / Fabric | 26.3 | 🔜 Next release |
| Folia | 26.2 | ⚠️ Not tested by HuskClaimsPlus |
| Spigot, older Minecraft versions | — | ❌ Not supported (use upstream HuskClaims) |

Both editions need **Java 25**. Fabric needs **Fabric Loader 0.19.3+** and **Fabric API**.
The Fabric jar also loads on 26.2.x hotfix releases (tested on 26.2). Everything else,
including the ClopLib Fabric port and the server-side menu library, is bundled inside
the Fabric jar.

## Install

**Paper:** put `HuskClaimsPlus-Paper-1.0.0.jar` in `plugins/` and restart. Files: `plugins/HuskClaims/`.

**Fabric server:** put `HuskClaimsPlus-Fabric-1.0.0+mc.26.2.jar` and Fabric API in `mods/` and
restart. Files: `config/huskclaims/`.

**Singleplayer / LAN host:** put the same two jars in your client's `mods/` folder. Settings
live in `.minecraft/config/huskclaims/` (shared by all your worlds); each world's claims live
in `saves/<world>/huskclaims/`. LAN guests install nothing.

Check the download against `SHA256SUMS.txt` on the release page.

### Upgrading

- **From HuskClaims 1.5.x (upstream):** back up, then replace the jar (remove the old one).
  HuskClaimsPlus uses the same plugin name, data folder, database, commands and permission
  nodes. New settings (`gui:`, `claims.warps:`) and new messages are added to your files
  automatically.
  - Your existing `trust_levels.yml` is **not** changed. To let Manage-trusted players
    manage claim warps, add `MANAGE_WARP` to the `privileges` of your `manage` level.
  - New installs only: the default **Access** and **Container** levels no longer allow
    placing or breaking vehicles (Build and Manage still do).
- **From "HuskClaims with Visits" 1.5.11-visits.1:** replace the jar. Your warps are kept.
- **Going back to upstream HuskClaims** works, but upstream does not know about warps or
  claim names: warps are dropped the next time upstream saves a claim world. Remove
  `MANAGE_WARP` from `trust_levels.yml` first.
- **Networks:** every server that shares a HuskClaims database or Redis broker must run the
  same HuskClaimsPlus version. Do not mix HuskClaimsPlus and upstream HuskClaims servers on
  one network.

## Commands added by HuskClaimsPlus

| Command | What it does | Permission | Default |
|---|---|---|---|
| `/claimgui` (also `/huskclaimgui`) | Open the Claim Menu | `huskclaims.command.huskclaimgui` | everyone |
| `/claimwarp set <name>` | Set this claim's warp where you stand | `huskclaims.command.claimwarp` + owner or `MANAGE_WARP` trust | everyone |
| `/claimwarp rename <name>` · `/claimwarp unset` | Rename or remove this claim's warp | same | everyone |
| `/visit <name>` | Teleport to a public claim warp | `huskclaims.command.visit` | everyone |
| `/visits [page]` | Clickable list of public claim warps | `huskclaims.command.visits` | everyone |

| Extra permission | What it allows | Default |
|---|---|---|
| `huskclaims.command.claimwarp.other` | Manage warps on other players' claims; use any trusted-only warp from the Public Claims menu | operators |
| `huskclaims.gui.claim_tool` | Get a claim tool from the Claim Menu | everyone |
| `huskclaims.gui.public_permissions` | Use the Public Permissions menu (each action also needs `huskclaims.flag.<action>`) | everyone |

Every menu button runs the matching HuskClaims command, so it needs that command's
permission (for example `/trust`, `/unclaim`, `/buyclaimblocks`, or
`huskclaims.command.claimblocks.gift` for gifting). The menu never grants anything the
command would not. All upstream commands and permissions are unchanged: see the
[HuskClaims docs](https://william278.net/docs/huskclaims/commands).

## Configuration

- `config.yml` → `gui:`: the Claim Menu (on/off, layout, icons, claim-tool cooldown,
  public-access cap, which public permissions owners may toggle, create/resize steps).
  See [Claim Menu](docs/Claim-Menu.md).
- `config.yml` → `claims.warps:`: claim warps (on/off, name length, unique names, visit
  cooldown). See [Claim warps](docs/Claim-Warps.md).
- `fabric.yml` (Fabric only): the LAN guest operator rule. See [Fabric](docs/Fabric.md).
- Everything else is upstream HuskClaims configuration:
  [HuskClaims config docs](https://william278.net/docs/huskclaims/config).

Update checks compare your version with this repository's GitHub releases (turn them off
with `check_for_updates: false`). HuskClaimsPlus sends no metrics.

## Translations

HuskClaimsPlus ships the 7 upstream languages (en-gb, it-it, pt-br, ro-ro, ru-ru, zh-cn,
zh-tw). The new Claim Menu and warp texts are English in every file for now. Translations
are very welcome as pull requests to `common/src/main/resources/locales/`.

## Building from source

You need JDK 21 and JDK 25. The Fabric build uses the
[HuskClaimsPlus fork of ClopLib](https://github.com/Promptt001/ClopLib), published to your
local Maven repository first:

```bash
git clone https://github.com/Promptt001/ClopLib && cd ClopLib
git checkout 2.0.13.1
JAVA_HOME=/path/to/jdk-25 ./gradlew :fabric:26.2:publishToMavenLocal :fabric:26.3:publishToMavenLocal
cd ..

git clone https://github.com/Promptt001/HuskClaimsPlus && cd HuskClaimsPlus
JAVA_HOME=/path/to/jdk-21 ./gradlew :common:build :bukkit:build
JAVA_HOME=/path/to/jdk-25 ./gradlew :paper:build :fabric:26.2:build
# jars: target/HuskClaimsPlus-Paper-<version>.jar, fabric/26.2/build/libs/HuskClaimsPlus-Fabric-<version>+mc.26.2.jar
```

Release tags are plain version numbers owned by this repository (`1.0.0`, `1.0.1`, …,
no `v`); the update checker reads them. Upstream's tags are not mirrored here.

## License and credits

HuskClaimsPlus is licensed under the [Apache License 2.0](LICENSE), like HuskClaims. See
[NOTICE](NOTICE) and [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

- **HuskClaims** by [William278](https://william278.net/) and contributors: the plugin this
  fork is built on. Please do not ask the HuskClaims team for help with HuskClaimsPlus.
- **HuskClaimsPlus** is maintained by [Promptt001](https://github.com/Promptt001).
- Uses [sgui](https://github.com/Patbox/sgui) (LGPL-3.0) for server-side menus on Fabric,
  and a [fork of ClopLib](https://github.com/Promptt001/ClopLib) for Fabric 26.2.

## Support

Found a bug or have an idea? [Open an issue](https://github.com/Promptt001/HuskClaimsPlus/issues).
Please include your platform (Paper/Fabric), Minecraft version, and the line from
`latest.log` that says `Successfully enabled HuskClaims v…`.
