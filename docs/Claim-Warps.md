Claim warps are **public teleport anchors** that claim owners can set on their claims, letting any player discover and travel to them with `/visit <name>`. They're perfect for public farms, shops, and community builds — anywhere you want visitors to be able to reach without walking.

Warps are an explicit opt-in: a claim without a warp behaves exactly as stock HuskClaims. Setting one makes the claim's landing spot publicly reachable.

> [!TIP]
> For a friendly walkthrough of warping, claiming, and trust, see the [Player Guide](Player-Guide.md).

## Setting a Warp
Stand inside your claim, at the exact position where you want visitors to arrive, and use:

`/claimwarp set <name>`

- The warp is anchored to your **exact standing position** (including rotation), and you'll be shown the exact coordinates it was saved at.
- The claim's owner (or a player with the `MANAGE_WARP` trust privilege) can set, rename, or unset the warp.
- Each claim has at most one warp. Warp names are case-insensitive and must be unique across the server.
- Names may use letters, numbers, `_`, `.` and `-` only (no spaces or other symbols) and are limited to 32 characters by default (`claims.warps.max_name_length`).
- A warp can be **public** (listed for everyone) or **trusted-only**: usable by the claim's owner, players trusted at a level that can build (Build or Manage by default), and admins with `huskclaims.command.claimwarp.other`. Toggle this from the claim's Public Permissions screen in the [Claim Menu](Claim-Menu.md).

## Visiting a Warp
Any player with the `huskclaims.command.visit` permission can teleport to a public warp by name:

`/visit <name>`

Or browse everything on offer with `/visits [page]` — a paginated, clickable chat list of all public warps. Click a warp name to teleport straight to it.

The Public Claims screen of the [Claim Menu](Claim-Menu.md) lists the public warps too, plus every **trusted-only** warp the player may use. Trusted-only warps are reached only from that screen: `/visit` and `/visits` never show or find them, so their names stay private.

Before teleporting, the plugin checks that:
- The visitor is not **banned** from the target claim.
- The claim is not **private** (if private-claims are enabled, only trusted players can enter).
- The landing spot is **safe** (see below).

## Anti-Trap Safety
Warps are validated for safety twice — they cannot be used to trap players:

1. **At set time:** `/claimwarp set` is refused if there's no safe ground (solid/water footing plus two blocks of headroom) at the anchor position.
2. **At every teleport:** `/visit` re-runs the safety check before moving the player. If the owner blocks off the landing spot after setting the warp, teleportation is refused until it's safe again.

The plugin also notifies the owner of the exact anchor position when the warp is set, so there's never any ambiguity about where visitors will land.

## Configuration
Warps are controlled by the `warps` section of `claims` in `config.yml`:

<details>
<summary>Claim Warps (config.yml)</summary>

```yaml
claims:
  # Settings for public claim warps (teleport anchors set by claim owners, visited via /visit)
  warps:
    # Whether to enable claim warps (/claimwarp set/unset/rename, /visit, /claimwarp list)
    enabled: true
    # The maximum length of a warp name
    max_name_length: 32
    # Whether warp names must be unique across the whole server (recommended; /visit lookup is by name)
    enforce_unique_names: true
    # Command cooldown, in seconds, between warp visits per player (0 to disable)
    visit_cooldown_seconds: 3.0
```
</details>

Setting `enabled: false` unregisters the `/claimwarp`, `/visit` and `/visits` commands entirely (after a restart — `/huskclaims reload` re-reads settings but does not rebuild the command list).

## Permissions
See [Permissions](Permissions.md) for the full list. In brief:

| Permission | Allows |
| --- | --- |
| `huskclaims.command.claimwarp` | Use `/claimwarp set/unset/rename` (managing a warp additionally requires the `MANAGE_WARP` privilege in the claim, or being the owner) |
| `huskclaims.command.claimwarp.other` | Manage warps on other players' claims; use any trusted-only warp from the Public Claims menu (operators by default) |
| `huskclaims.command.visit` | Use `/visit <name>` (public warps) |
| `huskclaims.command.visits` | Browse the `/visits` list |

## Data & Persistence
Warps are stored as part of the claim's serialized world data (`claim_warp` field on each claim), persisted through the standard claim-world database mechanism — no extra tables or files. Existing claim data from earlier versions loads unchanged (the warp is simply absent), and removing a claim removes its warp with it.
