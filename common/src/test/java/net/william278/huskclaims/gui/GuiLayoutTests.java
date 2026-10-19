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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("GUI Layout Tests")
public class GuiLayoutTests {

    /** Hub slots that must never collide with the level columns. */
    private static final int[] RESERVED_SLOTS = {4, 9, 27, 45, 48, 50};

    @Test
    @DisplayName("levelColumns(n) for n = 0..9: entries in [2,8], strictly increasing, ≤ 7")
    public void testLevelColumnsShape() {
        assertEquals(0, GuiLayout.levelColumns(0).length);
        assertEquals(0, GuiLayout.levelColumns(-3).length);
        for (int n = 1; n <= 9; n++) {
            final int[] columns = GuiLayout.levelColumns(n);
            assertTrue(columns.length >= 1, "n=" + n + " yields at least one column");
            assertTrue(columns.length <= 7, "n=" + n + " yields at most 7 columns");
            for (int i = 0; i < columns.length; i++) {
                assertTrue(columns[i] >= 2 && columns[i] <= 8,
                        "n=" + n + " column " + i + " out of [2,8]: " + columns[i]);
                if (i > 0) {
                    assertTrue(columns[i - 1] < columns[i],
                            "n=" + n + " columns strictly increasing at " + i);
                }
            }
        }
    }

    @Test
    @DisplayName("rowsFor: never fewer rows than the menu's own buttons need, capped at 6")
    public void testRowsForButtonsAlwaysFit() {
        assertEquals(2, GuiLayout.rowsFor(1, 11, 13, 15));
        assertEquals(3, GuiLayout.rowsFor(3, 22));
        assertEquals(6, GuiLayout.rowsFor(6, 53));
        assertEquals(1, GuiLayout.rowsFor(0));
        assertEquals(6, GuiLayout.rowsFor(2, 60));
    }

    @Test
    @DisplayName("Up to 4 levels are spaced out: 2, 4, 6, 8")
    public void testSpacedColumns() {
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{2}, GuiLayout.levelColumns(1));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{2, 4}, GuiLayout.levelColumns(2));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{2, 4, 6}, GuiLayout.levelColumns(3));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{2, 4, 6, 8}, GuiLayout.levelColumns(4));
    }

    @Test
    @DisplayName("5–7 levels are dense: columns 2..8, truncated at 7")
    public void testDenseColumns() {
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{2, 3, 4, 5, 6}, GuiLayout.levelColumns(5));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{2, 3, 4, 5, 6, 7, 8}, GuiLayout.levelColumns(7));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{2, 3, 4, 5, 6, 7, 8},
                GuiLayout.levelColumns(8), "8+ levels truncated to 7");
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{2, 3, 4, 5, 6, 7, 8},
                GuiLayout.levelColumns(9), "9 levels truncated to 7");
    }

    @Test
    @DisplayName("Hub slot math: public = 9+col, player = 27+col, both < 54, no reserved collisions")
    public void testHubSlotMath() {
        final Set<Integer> reserved = new HashSet<>();
        for (int slot : RESERVED_SLOTS) {
            reserved.add(slot);
        }
        for (int n = 1; n <= 7; n++) {
            final int[] columns = GuiLayout.levelColumns(n);
            for (int col : columns) {
                final int pub = GuiLayout.publicSlot(col);
                final int player = GuiLayout.playerSlot(col);
                assertTrue(pub >= 10 && pub <= 17, "public slot " + pub + " in row 1 (10..17)");
                assertTrue(player >= 29 && player <= 35, "player slot " + player + " in row 3 (29..35)");
                assertTrue(pub < 54 && player < 54, "slots within a 6-row menu");
                assertTrue(!reserved.contains(pub) && !reserved.contains(player),
                        "slot collision with reserved slot at column " + col);
            }
        }
    }

    @Test
    @DisplayName("Public and player rows share the same columns (same i → same col)")
    public void testRowsAligned() {
        final int[] columns = GuiLayout.levelColumns(4);
        for (int i = 0; i < columns.length; i++) {
            assertEquals(18, GuiLayout.playerSlot(columns[i]) - GuiLayout.publicSlot(columns[i]),
                    "player row is exactly two rows below the public row");
        }
    }

    @Test
    @DisplayName("Public Action pairs are disjoint, in bounds, and never collide with hub slots")
    public void testPublicActionPairsAreDisjointAndInBounds() {
        final Set<Integer> used = new HashSet<>(Set.of(4, 18, 26, 45)); // info, prev, next, back
        for (int i = 0; i < 14; i++) {
            final int[] p = GuiLayout.publicActionPair(i);
            assertEquals(p[0] + 9, p[1], "status directly beneath icon");
            assertTrue(p[1] < 54, "status slot in bounds");
            assertTrue(used.add(p[0]), "icon slot reused: " + p[0] + " (i=" + i + ")");
            assertTrue(used.add(p[1]), "status slot reused: " + p[1] + " (i=" + i + ")");
        }
    }

    @Test
    @DisplayName("Click routing inverts publicActionPair: every pair slot resolves back to i")
    public void testPublicActionClickRoutingRoundTrip() {
        for (int count = 1; count <= 14; count++) {
            for (int i = 0; i < count; i++) {
                final int[] p = GuiLayout.publicActionPair(i);
                assertEquals(i, GuiLayout.publicActionIndexForSlot(p[0], count),
                        "icon slot " + p[0] + " resolves to i=" + i + " (count=" + count + ")");
                assertEquals(i, GuiLayout.publicActionIndexForSlot(p[1], count),
                        "status slot " + p[1] + " resolves to i=" + i + " (count=" + count + ")");
            }
            for (int slot = 0; slot < 54; slot++) {
                boolean isPairSlot = false;
                for (int i = 0; i < count; i++) {
                    final int[] p = GuiLayout.publicActionPair(i);
                    if (slot == p[0] || slot == p[1]) {
                        isPairSlot = true;
                        break;
                    }
                }
                if (!isPairSlot) {
                    assertEquals(-1, GuiLayout.publicActionIndexForSlot(slot, count),
                            "non-pair slot " + slot + " must route to -1 (count=" + count + ")");
                }
            }
        }
    }

    @Test
    @DisplayName("capLevels truncates above the 7-column limit and is a no-op below it")
    public void testCapLevelsTruncatesToColumnLimit() {
        for (int n = 0; n <= 10; n++) {
            final List<Integer> levels = IntStream.range(0, n).boxed().toList();
            final List<Integer> capped = GuiLayout.capLevels(levels);
            assertEquals(Math.min(n, GuiLayout.MAX_LEVEL_COLUMNS), capped.size(),
                    "n=" + n + " must cap at " + GuiLayout.MAX_LEVEL_COLUMNS);
            // Truncation keeps the lowest-weight (earliest) levels
            assertEquals(levels.subList(0, capped.size()), capped,
                    "capped list must be a prefix of the input (n=" + n + ")");
            // The capped list always fits the columns array it will be paired with
            assertEquals(capped.size(), GuiLayout.levelColumns(capped.size()).length,
                    "capped size must match levelColumns length (n=" + n + ")");
        }
    }

}
