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

package net.william278.huskclaims.gui.menu;

import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.gui.ClaimNames;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.GuiLayout;
import net.william278.huskclaims.gui.GuiText;
import net.william278.huskclaims.gui.LevelBullets;
import net.william278.huskclaims.gui.PublicLadder;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * "Friends &amp; Trust" per-claim hub.
 *
 * <p>The hub always targets an explicit {@link ClaimRef} (never the standing
 * claim): the claim picked from My Claims, the claim whose settings were open,
 * or — from the main menu — the claim the player is standing in <b>if they may
 * manage its trustees</b>, else their only claim, else a claim picker. All
 * clicks run the real trust/untrust commands scoped to the hub's ref through
 * the privilege-checked dispatcher, so no click can ever edit a claim the
 * viewer cannot manage or the wrong claim.</p>
 *
 * <p>Layout (6 rows, code constants): header claim icon slot 4, "Everyone"
 * row label slot 9 with public-row toggles at {@code 9 + col}, spacer row,
 * "Specific players" label slot 27 with per-level grant icons at
 * {@code 27 + col}, and back 45 / list 48 / remove 50. Columns come from
 * {@link GuiLayout#levelColumns(int)} so the public and player rows share
 * columns, sorted ascending by weight (left → right).</p>
 *
 * <p>The public row uses ladder semantics: a level is ON (the exact
 * {@code #public} level), INCLUDED (green, because a higher cumulative public
 * level grants everything this one does), OFF, or LOCKED (cannot be public:
 * privilege-carrying or above the {@code gui.public_access.max_level}
 * ceiling). Clicking a green tile lowers public access to the highest
 * candidate below it, or revokes entirely; clicking red raises it (raising
 * INTO a container-opening level confirms first, warning about chests).</p>
 *
 * <p><b>Settle-aware rendering</b>: the hub never re-renders on "dispatched" —
 * only once the claim's observed state changed; a click acts on the state the
 * tile was RENDERED with (if the live state differs, the hub merely refreshes
 * to show the truth); and at most one public-row change is in flight per menu
 * instance ({@code busy}).</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class TrustMenu implements MenuView {

    // Layout constants (menu geometry is code, not config)
    static final int ROWS = 6;
    static final int HEADER_SLOT = 4;
    static final int PUBLIC_ROW_LABEL_SLOT = 9;
    static final int PLAYERS_ROW_LABEL_SLOT = 27;
    static final int BACK_SLOT = 45;
    static final int LIST_SLOT = 48;
    static final int REMOVE_SLOT = 50;

    private final GuiController c;
    /** The claim this hub manages trust in — never null, never the standing claim. */
    @NotNull
    private final ClaimRef target;
    /** Where Back goes (main menu vs claim settings vs picker). */
    @NotNull
    private final Consumer<OnlineUser> backTo;
    /** The ladder state each public tile was RENDERED with. Refilled every render. */
    @NotNull
    private final Map<String, PublicLadder.State> shownPublic = new HashMap<>();
    /** A public-row change is in flight; further public-row clicks are ignored. */
    private boolean busy;
    /** Level id whose tile shows "Updating..." while busy. */
    @Nullable
    private String pendingLevelId;

    public TrustMenu(@NotNull GuiController c, @NotNull ClaimRef target) {
        this(c, target, null);
    }

    public TrustMenu(@NotNull GuiController c, @NotNull ClaimRef target,
                     @Nullable Consumer<OnlineUser> backTo) {
        this.c = c;
        this.target = target;
        this.backTo = backTo != null ? backTo : u -> c.openMainMenu(u);
    }

    @NotNull
    @Override
    public String typeName() {
        return "trust";
    }

    @Override
    public int rows() {
        return ROWS;
    }

    @NotNull
    @Override
    public Map<Integer, MenuIcon> icons() {
        final Map<Integer, MenuIcon> items = new LinkedHashMap<>();

        // Resolve the target claim; render an inert hub if it is gone (clicks are no-ops)
        final Optional<ClaimRef.Resolved> resolved = target.resolve(c.getPlugin());
        final Optional<Claim> claim = resolved.map(ClaimRef.Resolved::claim);

        // Header (slot 4): the claim being edited, click to pick another
        items.put(HEADER_SLOT, headerItem(resolved));

        // Row labels
        items.put(PUBLIC_ROW_LABEL_SLOT, c.item("minecraft:oak_sign")
                .name("gui_trust_row_public").lore("gui_trust_row_public_lore").build());
        items.put(PLAYERS_ROW_LABEL_SLOT, c.item("minecraft:player_head")
                .name("gui_trust_row_players").lore("gui_trust_row_players_lore").build());

        // Levels, ascending weight left → right. The ladder state each tile was
        // rendered with is remembered; the claim is resolved ONCE here.
        final List<TrustLevel> levels = shownLevels();
        final int[] columns = GuiLayout.levelColumns(levels.size());
        final List<TrustLevel> candidates = candidateLevels();
        final Optional<TrustLevel> current = claim.flatMap(cl -> cl.getTagTrustLevel(
                c.getPlugin().getSettings().getTrustTags().getPublicAccessName(), c.getPlugin()));
        shownPublic.clear();
        for (int i = 0; i < levels.size(); i++) {
            final TrustLevel level = levels.get(i);
            final int col = columns[i];
            final PublicLadder.State state = PublicLadder.state(level, candidates, current);
            shownPublic.put(level.getId(), state);
            items.put(GuiLayout.publicSlot(col), level.getId().equals(pendingLevelId)
                    ? pendingItem(level)
                    : publicToggleItem(level, state, current));
            items.put(GuiLayout.playerSlot(col), playerLevelItem(level, resolved));
        }

        // Bottom row: back / list / remove
        items.put(BACK_SLOT, c.item("minecraft:arrow").name("gui_back")
                .lore("gui_back_lore").build());
        items.put(LIST_SLOT, c.item("minecraft:book").name("gui_trust_list")
                .lore("gui_trust_list_lore").build());
        items.put(REMOVE_SLOT, c.item("minecraft:shears").name("gui_trust_untrust")
                .lore("gui_trust_untrust_lore").build());
        return items;
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        if (slot == BACK_SLOT) {
            backTo.accept(user);
            return;
        }
        if (slot == HEADER_SLOT) {
            // The header "pick a different claim" must open the PICKER, not
            // re-resolve the claim at the player's feet
            c.openTrustPicker(user);
            return;
        }
        if (slot == LIST_SLOT) {
            // Thread the hub's Back destination into the trustees list
            c.openTrusteesMenu(user, target, backTo);
            return;
        }
        if (slot == REMOVE_SLOT) {
            c.requestAndConfirmScoped(user, target, "untrust",
                    "gui_prompt_player",
                    net.william278.huskclaims.gui.GuiValidators.playerName(
                            c.getPlugin().getSettings().getGui().isAllowNamePrefix()),
                    "gui_input_invalid_player",
                    v -> c.actionInfo("gui_action_untrust", v),
                    this::backToMenu,
                    this::reopen);
            return;
        }
        // Level rows: match against the same columns used at render time
        final List<TrustLevel> levels = shownLevels();
        final int[] columns = GuiLayout.levelColumns(levels.size());
        for (int i = 0; i < levels.size(); i++) {
            final TrustLevel level = levels.get(i);
            if (slot == GuiLayout.publicSlot(columns[i])) {
                handlePublicToggle(user, level);
                return;
            }
            if (slot == GuiLayout.playerSlot(columns[i])) {
                handleGrantClick(user, level);
                return;
            }
        }
    }

    /**
     * Ladder click on the public row. OFF → set to this level (raising INTO a
     * container-opening level confirms first). ON or INCLUDED → lower to the
     * highest candidate below this level, or revoke entirely. LOCKED → no-op
     * (lore explains it).
     *
     * <p>Settle-aware rules: a click acts on the state the player SAW — if the
     * live state differs from the rendered one, just refresh (show the truth,
     * do nothing); ignore clicks while a change is in flight. The ladder math
     * lives in {@link PublicLadder} (pure, unit-tested).</p>
     */
    private void handlePublicToggle(@NotNull OnlineUser user, @NotNull TrustLevel level) {
        if (busy) {
            return; // the previous change hasn't landed yet
        }
        final var tags = c.getPlugin().getSettings().getTrustTags();
        final Optional<ClaimRef.Resolved> resolved = target.resolve(c.getPlugin());
        if (resolved.isEmpty()) {
            c.claimGone(user, target);
            return;
        }
        if (!tags.isEnabled()) {
            c.getPlugin().getLocales().getLocale("gui_public_access_disabled").ifPresent(user::sendMessage);
            return;
        }
        final List<TrustLevel> candidates = candidateLevels();
        final Optional<TrustLevel> current = resolved.get().claim()
                .getTagTrustLevel(tags.getPublicAccessName(), c.getPlugin());
        final PublicLadder.State live = PublicLadder.state(level, candidates, current);
        final PublicLadder.State shown = shownPublic.get(level.getId());
        if (shown != null && shown != live) {
            c.host().refresh(user, this); // the screen was stale — show the truth, do nothing
            return;
        }
        final PublicLadder.Action action = PublicLadder.click(level, candidates, current);
        if (action.kind() == PublicLadder.Kind.NONE) {
            return; // LOCKED: lore explains
        }
        final String tagArg = tags.getTagSpecifierPrefix() + tags.getPublicAccessName();
        final String command = action.kind() == PublicLadder.Kind.REVOKE
                ? "untrust" : action.level().getCommandAliases().getFirst(); // aliases non-empty (candidates)

        // Raising INTO a container-opening level confirms first (chests open to
        // everyone). The guard is on the RESULTING level, not the clicked one:
        // lowering that keeps containers closed no longer confirms.
        if (action.kind() == PublicLadder.Kind.GRANT
                && action.level().getFlags().contains(OperationType.CONTAINER_OPEN)
                && current.map(cur -> !cur.getFlags().contains(OperationType.CONTAINER_OPEN)).orElse(true)) {
            c.confirmScoped(user, target, command, new String[]{tagArg},
                    c.actionInfo("gui_public_access_warning_containers", tagArg),
                    this::backToMenu, this::reopen);
            return;
        }
        busy = true;
        pendingLevelId = level.getId();
        c.host().refresh(user, this);                   // show "Updating..." immediately
        net.william278.huskclaims.gui.ClaimActions.runThen(c, user, target, command, new String[]{tagArg}, () -> {
            busy = false;
            pendingLevelId = null;
            c.host().refresh(user, this);               // no-op if the player left this menu
        });
    }

    /** "Updating..." tile while a public-row change is in flight. */
    @NotNull
    private MenuIcon pendingItem(@NotNull TrustLevel level) {
        return c.item("minecraft:clock")
                .name("gui_trust_public_pending", level.getDisplayName())
                .build();
    }

    /**
     * Click a player-level icon: validated name prompt → scoped confirm (bound
     * to THIS claim). Info item warns when the level carries privileges.
     */
    private void handleGrantClick(@NotNull OnlineUser user, @NotNull TrustLevel level) {
        final Optional<ClaimRef.Resolved> resolved = target.resolve(c.getPlugin());
        if (resolved.isEmpty()) {
            c.claimGone(user, target);
            return;
        }
        c.requestAndConfirmScoped(user, target,
                level.getCommandAliases().getFirst(),
                "gui_prompt_player",
                net.william278.huskclaims.gui.GuiValidators.playerName(
                        c.getPlugin().getSettings().getGui().isAllowNamePrefix()),
                "gui_input_invalid_player",
                v -> c.trustGrantInfo(level, v, currentClaimName()),
                this::backToMenu, this::reopen);
    }

    // ------------------------------------------------------------------ render helpers

    /**
     * Levels shown in the hub: only levels with a command alias, ascending
     * weight, truncated to what fits in one row (the loops below index the
     * columns array by level index, so an uncapped list overflows it).
     */
    @NotNull
    private List<TrustLevel> shownLevels() {
        return TrustLevelPicker.grantableLevels(c);
    }

    /**
     * Trust levels that may be granted to everyone (the {@code #public} tag),
     * lowest first. The rule itself lives in {@link PublicLadder#candidates};
     * this method only supplies the configured ceiling.
     */
    @NotNull
    private List<TrustLevel> candidateLevels() {
        final String ceilingId = c.getPlugin().getSettings().getGui().getPublicAccess().getMaxLevel();
        final Optional<TrustLevel> ceiling = c.getPlugin().getTrustLevel(ceilingId);
        if (ceiling.isEmpty()) {
            net.william278.huskclaims.gui.GuiDiagnostics.warnOnce(c.getPlugin(), "public-access-ceiling",
                    "gui.public_access.max_level '" + ceilingId + "' is not a trust level id; using weight 200");
        }
        return PublicLadder.candidates(c.getPlugin().getTrustLevels(), ceiling.map(TrustLevel::getWeight).orElse(200));
    }

    /** Public-row toggle icon for one level: emerald/redstone blocks, iron bars locked. */
    @NotNull
    private MenuIcon publicToggleItem(@NotNull TrustLevel level, @NotNull PublicLadder.State state,
                                      @NotNull Optional<TrustLevel> currentPublic) {
        final String fallback = switch (state) {
            case ON, INCLUDED -> "minecraft:emerald_block";
            case OFF -> "minecraft:redstone_block";
            case LOCKED -> "minecraft:iron_bars";
        };
        final net.william278.huskclaims.gui.IconBuilder item = c.item(fallback);
        switch (state) {
            case ON -> {
                item.name("gui_trust_public_on", level.getDisplayName());
                item.lore("gui_trust_public_on_lore");
                c.levelBullets(item, level, false);
                item.lore("gui_trust_public_on_hint");
            }
            case INCLUDED -> {
                item.name("gui_trust_public_on", level.getDisplayName());
                item.lore("gui_trust_public_included_lore",
                        currentPublic.map(TrustLevel::getDisplayName).orElse(level.getDisplayName()));
                c.levelBullets(item, level, false);
            }
            case OFF -> {
                item.name("gui_trust_public_off", level.getDisplayName());
                item.lore("gui_trust_public_off_lore");
                c.levelBullets(item, level, false);
                if (level.getFlags().contains(OperationType.CONTAINER_OPEN)) {
                    item.lore("gui_trust_public_chest_warning");
                }
                item.lore("gui_trust_public_off_hint");
            }
            case LOCKED -> {
                item.name("gui_trust_public_locked", level.getDisplayName());
                item.lore("gui_trust_public_locked_lore", level.getDisplayName());
            }
        }
        return item.build();
    }

    /** Player-level grant icon: category icon, level colour, ability bullets. */
    @NotNull
    private MenuIcon playerLevelItem(@NotNull TrustLevel level,
                                     @NotNull Optional<ClaimRef.Resolved> resolved) {
        final net.william278.huskclaims.gui.IconBuilder item = c.item(c.trustLevelIconKey(level))
                .nameRaw(GuiText.fill(
                        c.text("gui_trust_level_name",
                                GuiText.hex(level.getColor()), level.getDisplayName())));
        item.lore("gui_trust_grant_lore", level.getDisplayName());
        c.levelBullets(item, level, true);
        item.lore("gui_trust_grant_footer", level.getDisplayName(),
                Long.toString(resolved.map(r -> r.claim().getTrustedUsers().values().stream()
                        .filter(levelId -> levelId.equals(level.getId())).count())
                        .orElse(0L)));
        return item.build();
    }

    /** Hub header (slot 4): the claim under management, click to switch claims. */
    @NotNull
    private MenuIcon headerItem(@NotNull Optional<ClaimRef.Resolved> resolved) {
        final net.william278.huskclaims.gui.IconBuilder item = c.item("minecraft:grass_block")
                .nameLiteral(currentClaimName());
        resolved.ifPresent(res -> item.lore("gui_trust_header_lore",
                res.worldName(),
                Long.toString(res.claim().getRegion().getSurfaceArea())));
        return item.build();
    }

    @NotNull
    private String currentClaimName() {
        return target.resolve(c.getPlugin())
                .map(res -> ClaimNames.displayName(res.claim(), res.worldName(), c.getPlugin()))
                .orElseGet(() -> c.text("gui_claim_gone"));
    }

    private void backToMenu(@NotNull OnlineUser u) {
        c.openTrustHub(u, this.target, this.backTo);
    }

    /** Confirm closes the inventory: reopen the hub, never a bare refresh. */
    private void reopen(@NotNull OnlineUser u) {
        c.openTrustHub(u, this.target, this.backTo);
    }



}
