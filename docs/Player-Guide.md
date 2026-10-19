This page is a quick-start guide for **players** — everything you need to protect your builds and visit public places. Server owners: see [Setup](Setup.md) and [Config](Config.md) instead.

## Do Everything From the Claim Menu
One command, no mod needed on Fabric: `/claimgui` opens the Claim Menu — see and manage every claim you own, trust friends, set a claim warp, buy or gift claim blocks, create and resize claims with a live block cost, and get a claim tool. Every button asks for confirmation before it changes anything. See [Claim Menu](Claim-Menu.md).

## Checking Your Claim Block Balance
Claim blocks (■) are the currency used to claim land. You earn more just by playing (100 per hour by default), and every claim you make spends them — so it pays to know your balance.

Use `/claimblocks` to check how many claim blocks you have left to spend. The command also shows how many of your blocks are currently spent on existing claims, so you can tell at a glance how much land you could still protect.

> [!TIP]
> If your server has an economy and the Vault hook enabled, you can buy extra claim blocks with `/buyclaimblocks <amount>`.

## Making a Claim
There are two easy ways to claim land:

**With the claim tool (Golden Shovel by default)**
1. Right-click a block to mark the first corner of your claim.
2. Right-click a block at the opposite corner (e.g. the far corner of your build).
3. Done! The rectangle between the two corners is now protected, from bedrock to the sky.

**With a command**
Stand where you want the center of your claim and use `/claim <radius>` — for example `/claim 10` creates a 21×21 claim centered on you. `/claim` on its own toggles claiming mode, letting you use the tool.

A few rules to know:
- Claims have a minimum size (5×5 by default) — very small selections will be rejected.
- Every block of the claim's surface area costs **one** claim block, so a 21×21 claim costs 441 blocks.
- You can't claim land that's already claimed, or land outside the world border.

Not sure if a spot is taken? Right-click the ground with a **Stick** (the inspection tool) to see if it's claimed — and hold **Sneak** to highlight all nearby claims at once.

## Managing a Claim
Once you have a claim, you're the only one who can build in it. To let friends help — or to change the claim itself, stand **inside** the claim and use these commands:

| What you want | Command |
| --- | --- |
| Let a friend build/use chests | `/trust <player>` (or a lighter level like `/containertrust <player>`) |
| Remove someone's access | `/untrust <player>` |
| See who has access | `/trustlist` |
| Make the claim bigger | Hold the claim tool, right-click a corner, then click the new corner |
| Make the claim smaller | Same as resizing — the refunded blocks go back to your balance |
| Rename & remove the claim | `/unclaim` (refund all blocks; `/unclaimall` for every claim you own) |
| List all your claims | `/claimslist` |
| Ban a griefer from entering | `/claimban <player>` (if enabled on your server) |
| Make the claim private | `/claimprivate` (if enabled on your server) |

**Trust levels** control what visitors can do — from just walking around, up to full build rights. Give people only what they need; you can always untrust later. See [Trust](Trust.md) for the full list of levels.

**Child claims** (sub-divisions) let you fence off parts of a claim — handy for shops or community plots inside your town. Use `/childclaim` while inside your claim to enter child-claiming mode.

## Using Claim Warps
Claim warps let you set up a **public teleport point** on your claim so anyone can visit — perfect for shops, farms, and community builds. You don't need one; claims without warps behave exactly as normal.

**Setting your warp:**
1. Stand **inside your claim**, at the exact spot where visitors should arrive.
2. Use `/claimwarp set <name>` — e.g. `/claimwarp set town-shop`.
3. You'll be told the exact position the warp was anchored to. Visitors will land precisely there.
4. That's it! Your warp now appears in the public list, and anyone can teleport with `/visit <name>`.

**Warp management:**

| What you want | Command |
| --- | --- |
| Change your warp's name | `/claimwarp rename <new-name>` (stand inside your claim) |
| Remove your warp | `/claimwarp unset` |
| Browse all public warps | `/visits` (or `/claimwarp list`) — clickable list, click a name to teleport! |
| Teleport to a warp | `/visit <name>` |

**Safety first:** warps can't be used to trap players. A warp only works if there is safe ground and headroom at the landing spot — this is checked when the warp is set **and again every time someone teleports**. If you block off your warp's landing spot, teleporting there will be refused until it's safe again. Banned or untrusted players can't warp into private claims, either.

Only the claim owner (or players with warp-manage permission) can set, rename, or unset the warp. Each claim has one warp, and warp names must be unique across the server.

## Quick Reference

| Command | What it does |
| --- | --- |
| `/claimblocks` | Check your claim block balance |
| `/claim <radius>` | Create a claim around you |
| `/trust <player>` | Give a player access to your claim |
| `/claimslist` | List your claims |
| `/claimwarp set <name>` | Set your claim's public warp (stand at the landing spot) |
| `/visit <name>` | Teleport to a public claim warp |
| `/visits` | Browse all public warps |
| `/huskclaims help` | Full command list in-game |
