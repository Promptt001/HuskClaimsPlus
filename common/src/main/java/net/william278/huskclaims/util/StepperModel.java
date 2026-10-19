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

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Pure state machine for the GUI value steppers (create-claim radius, resize
 * distance). Bounds are assigned <em>before</em> the initial value is clamped
 * (the K4 regression: {@code StepperMenu} previously clamped against
 * zero-initialized {@code min}/{@code max}, so every stepper opened at 0).
 * Slot constants define a mirror-symmetric row around {@link #VALUE_SLOT}.
 *
 * @see net.william278.huskclaims.claim.ClaimSizeMath
 * @since HuskClaimsPlus 1.0.0
 */
public final class StepperModel {

    /** Inventory slot of the centre value item. */
    public static final int VALUE_SLOT = 13;
    /** Minus step slots, smallest step nearest the value ({@code 12, 11, 10}). */
    public static final int[] MINUS_SLOTS = {12, 11, 10};
    /** Plus step slots, smallest step nearest the value ({@code 14, 15, 16}). */
    public static final int[] PLUS_SLOTS = {14, 15, 16};

    private final int min;
    private final int max;
    private final List<Integer> steps;
    private int value;

    private StepperModel(int initial, int min, int max, @NotNull List<Integer> steps) {
        this.min = min;
        this.max = Math.max(min, max);
        this.steps = steps.stream().filter(s -> s > 0).distinct().sorted().limit(3).toList();
        this.value = clamp(initial);  // AFTER bounds —
    }

    @NotNull
    public static StepperModel of(int initial, int min, int max, @NotNull List<Integer> steps) {
        return new StepperModel(initial, min, max, steps);
    }

    public int value() {
        return value;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    /** Step sizes, ascending; ≤3, positive, de-duplicated. Index 0 is nearest the value. */
    @NotNull
    public List<Integer> steps() {
        return steps;
    }

    /** @return true if applying {@code delta} would stay within {@code [min, max]}. */
    public boolean canApply(int delta) {
        final long target = (long) value + delta;
        return target >= min && target <= max;
    }

    public void toMin() {
        value = min;
    }

    public void toMax() {
        value = max;
    }

    /**
     * Apply {@code delta}, clamping to the bounds. Overshooting deltas (e.g.
     * shift-click ×5 near a bound) land on the bound instead of doing nothing.
     *
     * @return true if the value changed
     */
    public boolean applyClamped(int delta) {
        final int before = value;
        final long candidate = (long) value + delta;
        final int clampedCandidate = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, candidate));
        value = clamp(clampedCandidate);
        return value != before;
    }

    private int clamp(int v) {
        return Math.max(min, Math.min(max, v));
    }

}
