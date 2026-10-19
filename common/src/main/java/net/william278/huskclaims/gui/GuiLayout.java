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

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * Pure layout math for code-defined menu geometry (the layout is code, not
 * config). Computes the column positions for the Friends &amp; Trust hub's
 * two rows (public / player-level columns) without Bukkit, so it is
 * unit-testable in {@code common}.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class GuiLayout {

    private GuiLayout() {
    }

    /**
     * Rows for a menu whose size comes from config.yml: the configured number, but never so few
     * that one of the menu's own buttons would fall outside the inventory and disappear.
     *
     * @param configuredRows the rows set in config.yml
     * @param buttonSlots    every slot the menu puts a button in
     * @return a row count from 1 to 6
     */
    public static int rowsFor(int configuredRows, int... buttonSlots) {
        final int highestSlot = Arrays.stream(buttonSlots).max().orElse(0);
        final int rowsNeeded = highestSlot / 9 + 1;
        return Math.min(6, Math.max(Math.max(1, configuredRows), rowsNeeded));
    }

    /** The hub and the picker have room for this many trust levels per row. */
    public static final int MAX_LEVEL_COLUMNS = 7;

    /**
     * Sub-list of one page of items, clamped (claims can be deleted while a
     * menu is open).
     *
     * @param all     all items
     * @param page    0-based page index
     * @param perPage items per page
     * @return the page's items (empty when out of range)
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static <T> List<T> page(@NotNull List<T> all, int page, int perPage) {
        final int from = Math.max(0, page * perPage);
        return from >= all.size() ? List.of() : all.subList(from, Math.min(all.size(), from + perPage));
    }

    /**
     * Number of pages needed for the given item count (at least 1).
     *
     * @param size    total item count
     * @param perPage items per page
     * @return page count (never 0)
     * @since HuskClaimsPlus 1.0.0
     */
    public static int pageCount(int size, int perPage) {
        return Math.max(1, (size + perPage - 1) / perPage);
    }

    /**
     * Truncates a weight-sorted level list to what fits in one menu row,
     * {@link #MAX_LEVEL_COLUMNS}. Callers warn (once) when levels are hidden.
     *
     * @param levels levels sorted ascending by weight
     * @return at most the first {@link #MAX_LEVEL_COLUMNS} entries
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static <T> List<T> capLevels(@NotNull List<T> levels) {
        return levels.size() > MAX_LEVEL_COLUMNS ? levels.subList(0, MAX_LEVEL_COLUMNS) : levels;
    }

    /**
     * Columns for {@code n} trust levels in a 9-wide row that has a label in
     * column 0. Up to 4 levels are spaced out ({@code 2,4,6,8}); 5–7 use the
     * dense columns {@code 2..8}. Callers pass a count already capped at
     * {@link #MAX_LEVEL_COLUMNS} via {@link #capLevels(List)}.
     *
     * @param n the number of trust levels to lay out
     * @return strictly increasing column indexes, all in {@code [2, 8]}
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static int[] levelColumns(int n) {
        if (n <= 0) {
            return new int[0];
        }
        if (n <= 4) {
            // Spaced: 2, 4, 6, 8
            final int[] columns = new int[n];
            for (int i = 0; i < n; i++) {
                columns[i] = 2 + 2 * i;
            }
            return columns;
        }
        // Dense: 2..8 (7 max)
        final int k = Math.min(n, 7);
        final int[] columns = new int[k];
        for (int i = 0; i < k; i++) {
            columns[i] = 2 + i;
        }
        return columns;
    }

    /**
     * Slot of the public-row toggle for the level at column {@code col} in the
     * Friends &amp; Trust hub (row 1: {@code 9 + col}).
     *
     * @param col a column from {@link #levelColumns(int)}
     * @return the public-row slot
     * @since HuskClaimsPlus 1.0.0
     */
    public static int publicSlot(int col) {
        return 9 + col;
    }

    /**
     * Slot of the player-level item for the level at column {@code col} in the
     * Friends &amp; Trust hub (row 3: {@code 27 + col}).
     *
     * @param col a column from {@link #levelColumns(int)}
     * @return the player-row slot
     * @since HuskClaimsPlus 1.0.0
     */
    public static int playerSlot(int col) {
        return 27 + col;
    }

    /**
     * Icon rows for the two Public Actions pair blocks: icons on rows 1 and 3,
     * status directly beneath on rows 2 and 4. The blocks must not overlap: a
     * second block starting at 19 would collide with the first block's status
     * slots.
     *
     * @since HuskClaimsPlus 1.0.0
     */
    static final int[] ICON_ROW_STARTS = {10, 28};

    /**
     * The two slots (icon, status) for the {@code i}-th operation of a Public
     * Actions page: 7 pairs per block, icons on rows 1 and 3 ({@code 10-16},
     * {@code 28-34}), status directly beneath on rows 2 and 4
     * ({@code 19-25}, {@code 37-43}).
     *
     * @param i zero-based index of the op on the current page, {@code 0..13}
     * @return {@code {iconSlot, statusSlot}} with {@code statusSlot == iconSlot + 9}
     * @since HuskClaimsPlus 1.0.0
     */
    public static int[] publicActionPair(int i) {
        final int block = i / 7; // 0 → rows 1/2, 1 → rows 3/4
        final int col = i % 7;
        final int icon = ICON_ROW_STARTS[block] + col;
        return new int[]{icon, icon + 9};
    }

    /**
     * Pure inverse of {@link #publicActionPair(int)} for click routing: the
     * on-page index of the op occupying {@code slot}, or {@code -1} if the
     * slot is not a pair slot for the first {@code count} ops on this page.
     *
     * @param slot the clicked inventory slot
     * @param count number of ops on the current page (at most 14)
     * @return the on-page index, or {@code -1}
     * @since HuskClaimsPlus 1.0.0
     */
    public static int publicActionIndexForSlot(int slot, int count) {
        for (int i = 0; i < count; i++) {
            final int[] pair = publicActionPair(i);
            if (slot == pair[0] || slot == pair[1]) {
                return i;
            }
        }
        return -1;
    }

}
