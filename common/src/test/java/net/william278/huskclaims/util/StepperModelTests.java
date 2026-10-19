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

package net.william278.huskclaims.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Stepper Model Tests")
public class StepperModelTests {

    private static final List<Integer> STEPS = List.of(1, 5, 10);

    @Test
    @DisplayName("Initial value clamps into [min, max] — never 0 when min > 0")
    public void testInitialClampsIntoBounds() {
        assertEquals(4, StepperModel.of(5, 2, 4, STEPS).value());      // above max → clamped down
        assertEquals(5, StepperModel.of(5, 2, 9, STEPS).value());      // within → kept
        assertEquals(2, StepperModel.of(0, 2, 9, STEPS).value());      // below min → clamped up
        assertEquals(3, StepperModel.of(3, 3, 3, STEPS).value());      // degenerate range
    }

    @Test
    @DisplayName("Bounds are normalized: max below min is raised to min")
    public void testBoundsNormalization() {
        final StepperModel m = StepperModel.of(5, 2, 1, STEPS);
        assertEquals(2, m.min());
        assertEquals(2, m.max());
        assertEquals(2, m.value());
    }

    @Test
    @DisplayName("Steps are filtered positive, de-duplicated, sorted ascending, ≤3 kept")
    public void testStepNormalization() {
        assertEquals(List.of(1, 5, 10), StepperModel.of(5, 1, 10, List.of(10, 1, 5)).steps());
        assertEquals(List.of(1, 2), StepperModel.of(5, 1, 10, List.of(2, 1, 2, -3, 0)).steps());
        assertEquals(List.of(1, 2, 3), StepperModel.of(5, 1, 10, List.of(3, 2, 1, 5, 5)).steps());
        assertEquals(List.of(), StepperModel.of(5, 1, 10, List.of(-1, 0)).steps());
    }

    @Test
    @DisplayName("Mirror invariant: MINUS_SLOTS[i] + PLUS_SLOTS[i] == 2 * VALUE_SLOT")
    public void testMirrorInvariant() {
        assertEquals(StepperModel.MINUS_SLOTS.length, StepperModel.PLUS_SLOTS.length,
                "same step count on both sides");
        for (int i = 0; i < StepperModel.MINUS_SLOTS.length; i++) {
            assertEquals(2 * StepperModel.VALUE_SLOT,
                    StepperModel.MINUS_SLOTS[i] + StepperModel.PLUS_SLOTS[i],
                    "slots must mirror around the value at index " + i);
        }
    }

    @Test
    @DisplayName("Slot constants stay in row 1 and are distinct")
    public void testSlotConstants() {
        final int[] minus = StepperModel.MINUS_SLOTS;
        final int[] plus = StepperModel.PLUS_SLOTS;
        assertTrue(minus[0] > minus[1] && minus[1] > minus[2],
                "index 0 (smallest step) is nearest the value; minus slots descend");
        assertTrue(plus[0] < plus[1] && plus[1] < plus[2],
                "index 0 (smallest step) is nearest the value; plus slots ascend");
        for (int slot : minus) {
            assertTrue(slot >= 9 && slot < 13, "minus slot " + slot + " in row 1, left of value");
        }
        for (int slot : plus) {
            assertTrue(slot > 13 && slot <= 16, "plus slot " + slot + " in row 1, right of value");
        }
    }

    @Test
    @DisplayName("canApply is exact at both bounds")
    public void testCanApplyAtBounds() {
        final StepperModel m = StepperModel.of(5, 2, 9, STEPS);
        assertTrue(m.canApply(0));
        assertTrue(m.canApply(4));    // 5+4 == max
        assertFalse(m.canApply(5));   // overshoots max
        assertTrue(m.canApply(-3));   // 5-3 == min
        assertFalse(m.canApply(-4));  // undershoots min
        assertFalse(m.canApply(Integer.MAX_VALUE - 5), "no overflow in canApply");
    }

    @Test
    @DisplayName("applyClamped changes the value and respects bounds; overshoot clamps to bound")
    public void testApplyClamped() {
        StepperModel m = StepperModel.of(5, 2, 9, STEPS);
        assertTrue(m.applyClamped(5));
        assertEquals(9, m.value());
        assertFalse(m.applyClamped(5));           // already at max
        assertEquals(9, m.value());
        assertTrue(m.applyClamped(-20));          // overshoot clamps to min
        assertEquals(2, m.value());
        assertFalse(m.applyClamped(0));           // no-op returns false
    }

    @Test
    @DisplayName("toMin/toMax jump to the bounds")
    public void testJumps() {
        final StepperModel m = StepperModel.of(5, 2, 9, STEPS);
        m.toMin();
        assertEquals(2, m.value());
        m.toMax();
        assertEquals(9, m.value());
    }

    @Test
    @DisplayName("Extreme deltas do not overflow into other-side bounds")
    public void testNoOverflow() {
        StepperModel m = StepperModel.of(0, -1_000_000, 1_000_000, STEPS);
        assertTrue(m.applyClamped(Integer.MAX_VALUE));
        assertEquals(1_000_000, m.value());
        assertTrue(m.applyClamped(Integer.MIN_VALUE));
        assertEquals(-1_000_000, m.value());
    }

}
