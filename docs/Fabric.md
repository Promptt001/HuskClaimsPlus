HuskClaimsPlus runs on Fabric for Minecraft 26.2 — on dedicated servers, in
singleplayer, and in worlds opened to LAN where friends join with a **plain vanilla
client**.

## Install
- **Dedicated server**: put `HuskClaimsPlus-Fabric-1.0.0+mc.26.2.jar` and
  [Fabric API](https://modrinth.com/mod/fabric-api) in `mods/`. Files live in
  `config/huskclaims/`.
- **Singleplayer / LAN host**: put the same two jars in your client's `mods/` folder.
  Settings: `.minecraft/config/huskclaims/` (one config shared by all your worlds);
  each world's claims: `saves/<world>/huskclaims/`.

Requirements: Minecraft 26.2 (26.2.x hotfixes are accepted), Fabric Loader 0.19.3+,
Fabric API, Java 25. Everything else — including the ClopLib Fabric port and the
server-side GUI library — is bundled inside the jar.

## Where files live
| Installation | Config | Claims database |
|---|---|---|
| Dedicated server | `config/huskclaims/` | `config/huskclaims/` |
| Singleplayer / LAN | `config/huskclaims/` (shared) | `saves/<world>/huskclaims/` (per world) |

## LAN rules
- The world owner always has HuskClaims operator rights in their own world.
- LAN guests never inherit operator rights from "Allow Commands" — `fabric.yml`
  (`lan_guests_follow_operator_level`, default `false`) controls this.
- Vanilla guests install nothing: claiming, `/claimgui` and claim warps all work on
  a vanilla client, through server-side screens and anvil text prompts.
- Note: vanilla commands such as `/fill` bypass any protection mod when cheats are
  enabled, so "Allow Commands" is itself a trust decision.

## Cross-server
Cross-server messaging (Redis or proxy plugin messages) is available on dedicated
servers only. It is disabled on singleplayer/LAN worlds. Every server sharing a
HuskClaims database or broker must run the same HuskClaimsPlus version.

## Known limits
- Toggling some settings requires a restart to apply command registrations.
- There are no client-side features; the mod is invisible to vanilla clients apart
  from the screens it opens.
