The Claim Menu is HuskClaimsPlus's one-command GUI for everything players do with
claims. `/claimgui` (alias `/huskclaimgui`) opens it; every button runs the real
HuskClaims command or claim editor behind the scenes, with the same permission,
cooldown and trust checks as typing the command.

> [!TIP]
> Menu buttons need the same permissions as the commands they run. The menu never
> grants anything a command would not.

## Opening the menu
- `/claimgui` or `/huskclaimgui` — permission `huskclaims.command.huskclaimgui`
  (everyone by default). Requires `gui.enabled: true` (default) in `config.yml`.

## The screens
- **Claim Menu** (`/claimgui`): Public Claims · Friends & Trust · My Claims · Claim tool · Claim Blocks.
  - **My Claims**: every claim you own, paged and sortable, child claims included; **Create Claim** is here.
    - **Claim settings**: rename · private · Friends & Trust · Public Permissions · trusted players ·
      child claims · Resize · claim warp · unclaim.
  - **Friends & Trust**: trust/untrust players, groups and everyone; trust-level picker.
  - **Public Permissions**: one action at a time for everyone, plus the warp's
    public/trusted-only switch. Each action needs `huskclaims.flag.<action>` in addition to
    `huskclaims.gui.public_permissions`.
  - **Claim Blocks**: balance · buy (economy plugin) · gift (`huskclaims.command.claimblocks.gift`,
    operators by default — players without it are told so before any prompt opens).
  - **Public Claims**: every warp you may visit; click to teleport.
- **Confirm screen**: shown before anything that changes a claim or spends claim blocks.

## Safety by design
- Every click re-checks the real command permission and your trust in that exact
  claim; claims are looked up again at click time, not remembered from when the menu
  was opened.
- A menu left open while its claim is deleted or transferred says so instead of
  acting on the wrong land.
- Menu items can never be taken out of a menu.
- A handed-out claim tool cannot be smelted, refuelled, or repaired into an unmarked
  tool.

## Text prompts
- **Paper**: menus are normal inventory screens; text is typed in chat. Type
  `cancel` to abort; prompts time out (`gui.input_timeout_seconds`).
- **Fabric**: menus are server-side screens (sgui); text is typed in an anvil input
  with a short title. Close the anvil to cancel. Vanilla clients need nothing.

## The claim tool button
Hands out the configured claim tool (permission `huskclaims.gui.claim_tool`), with a
60 s cooldown that survives relogging (`gui.claim_tool_cooldown_seconds`), never
while you already carry one.

## Configuration
Every slot, icon and row count is configurable in `config.yml` under `gui:`. When an
update changes the default layout, your old `config.yml` is backed up first
(`gui.layout_version`). See [Config](Config.md) for the full key list.

## Permissions
| Permission | Allows | Default |
|---|---|---|
| `huskclaims.command.huskclaimgui` | Open the Claim Menu | everyone |
| `huskclaims.gui.claim_tool` | Get a claim tool from the menu | everyone |
| `huskclaims.gui.public_permissions` | Use the Public Permissions menu | everyone |
