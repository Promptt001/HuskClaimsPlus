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

import net.william278.huskclaims.gui.ClaimNames;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.GuiLayout;
import net.william278.huskclaims.gui.GuiText;
import net.william278.huskclaims.gui.PickTarget;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Trust level picker: levels at {@link GuiLayout#levelColumns} positions in
 * row 1 (slot {@code 9+col}), Remove at 22, Back at 18; always 3 rows. Levels
 * are built dynamically from the configured trust levels sorted by weight —
 * never a hand-maintained slot map — so an id/alias mismatch is structurally
 * impossible. Clicking a level that carries privileges goes through a scoped
 * confirm; Back renders and handles at the same slot.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class TrustLevelPicker implements MenuView {

    /** Layout constants (menu geometry is code, not config). */
    static final int REMOVE_SLOT = 22;
    static final int BACK_SLOT = 18;
    static final int ROWS = 3;

    private final GuiController c;
    private final ClaimRef ref;
    @Nullable
    private final PickTarget target;
    @NotNull
    private final List<TrustLevel> levels;

    public TrustLevelPicker(@NotNull GuiController c, @NotNull ClaimRef ref,
                            @Nullable PickTarget target) {
        this.c = c;
        this.ref = ref;
        this.target = target;
        // Dynamic, weight-sorted, capped to what fits in one row (the render
        // and click loops index the columns array by level index, so an
        // uncapped list would overflow it).
        this.levels = grantableLevels(c);
    }

    /** Levels grantable by command (has an alias), ascending weight, row-capped. */
    @NotNull
    static List<TrustLevel> grantableLevels(@NotNull GuiController c) {
        final List<TrustLevel> grantable = c.getPlugin().getTrustLevels().stream()
                .filter(level -> !level.getCommandAliases().isEmpty())
                .sorted(Comparator.comparingInt(TrustLevel::getWeight))
                .toList();
        if (grantable.size() > GuiLayout.MAX_LEVEL_COLUMNS) {
            net.william278.huskclaims.gui.GuiDiagnostics.warnOnce(c.getPlugin(), "too-many-trust-levels",
                    "Only the " + GuiLayout.MAX_LEVEL_COLUMNS + " lowest trust levels fit in the claim GUI; "
                            + (grantable.size() - GuiLayout.MAX_LEVEL_COLUMNS)
                            + " are hidden (use the commands for those)");
        }
        return GuiLayout.capLevels(grantable);
    }

    @NotNull
    @Override
    public String typeName() {
        return "trust_level_picker";
    }

    @Override
    public int rows() {
        return ROWS; // fixed 3 rows (levels row 1, remove/back row 2)
    }

    @NotNull
    @Override
    public Optional<String> title() {
        // Dynamic title "Trust level for <player>"
        if (target != null) {
            return Optional.of(GuiText.fill(
                    c.text("gui_trust_level_picker_title"), target.name()));
        }
        return Optional.empty(); // falls back to gui_trust_level_picker_title
    }

    /**
     * The target's current trust level id in this claim, if any (for the ✔
     * marker). Pure in-memory lookup from the trusted-users map (no database
     * query on the render path — the target was resolved once, async, before
     * the picker opened).
     */
    @NotNull
    private Optional<String> currentLevelId() {
        if (target == null) {
            return Optional.empty();
        }
        return ref.resolve(c.getPlugin())
                .map(r -> r.claim().getTrustedUsers().get(target.uuid()));
    }

    @NotNull
    @Override
    public Map<Integer, MenuIcon> icons() {
        final Map<Integer, MenuIcon> items = new LinkedHashMap<>();
        final Optional<String> currentId = currentLevelId();
        final int[] columns = GuiLayout.levelColumns(levels.size());
        for (int i = 0; i < levels.size(); i++) {
            final TrustLevel level = levels.get(i);
            final net.william278.huskclaims.gui.IconBuilder item = c.item(c.trustLevelIconKey(level))
                    .nameRaw(GuiText.fill(c.text("gui_trust_level_name",
                            GuiText.hex(level.getColor()), level.getDisplayName())));
            // lore = the same ability bullets as the hub
            c.levelBullets(item, level, true);
            if (currentId.isPresent() && currentId.get().equals(level.getId())) {
                item.loreBlank().lore("gui_trust_current");
            }
            items.put(9 + columns[i], item.build());
        }
        if (target != null) {
            items.put(REMOVE_SLOT, c.item("trustees.remove", "minecraft:shears")
                    .name("gui_trustee_remove")
                    .lore("gui_trustee_remove_lore", target.name()).build());
        }
        // Back renders and handles at the SAME slot (18)
        items.put(BACK_SLOT, c.item("minecraft:arrow")
                .name("gui_back")
                .lore("gui_back_lore").build());
        return items;
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        if (slot == BACK_SLOT) {
            c.openTrusteesMenu(user, ref);
            return;
        }
        if (slot == REMOVE_SLOT) {
            if (target != null) {
                // Remove is destructive, so it confirms first and reopens the
                // trustees list once the change has landed
                c.whenNameStillMatches(user, target, () ->
                        c.confirmScoped(user, ref, "untrust", new String[]{target.name()},
                                c.actionInfo("gui_action_untrust", target.name()),
                                u -> c.openTrusteesMenu(u, ref),
                                u -> c.openTrusteesMenu(u, ref)));
            }
            return;
        }
        final int[] columns = GuiLayout.levelColumns(levels.size());
        for (int i = 0; i < levels.size(); i++) {
            if (slot == 9 + columns[i]) {
                grantLevel(user, levels.get(i));
                return;
            }
        }
    }

    private void grantLevel(@NotNull OnlineUser user, @NotNull TrustLevel level) {
        if (target == null) {
            return; // no target: nothing to grant
        }
        // Commands identify players by name, and names can change hands, so the
        // account check runs immediately before anything is dispatched
        c.whenNameStillMatches(user, target, () -> grantLevelChecked(user, level));
    }

    private void grantLevelChecked(@NotNull OnlineUser user, @NotNull TrustLevel level) {
        // Privileged levels confirm scoped to THIS claim, never the one
        // underfoot; the ✔ moves only once the change has landed
        if (!level.getPrivileges().isEmpty()) {
            c.confirmScoped(user, ref, level.getCommandAliases().getFirst(),
                    new String[]{target.name()},
                    c.trustGrantInfo(level, target.name(), claimName()),
                    u -> c.openTrusteesMenu(u, ref),
                    u -> c.openTrustLevelPicker(u, ref, target));
        } else {
            // the ✔ moves only once the change has landed
            net.william278.huskclaims.gui.ClaimActions.runThen(c, user, ref,
                    level.getCommandAliases().getFirst(), new String[]{target.name()},
                    () -> c.host().refresh(user, this));
        }
    }

    @NotNull
    private String claimName() {
        return ref.resolve(c.getPlugin())
                .map(res -> ClaimNames.displayName(res.claim(), res.worldName(), c.getPlugin()))
                .orElseGet(() -> c.text("gui_claim_gone"));
    }

}
