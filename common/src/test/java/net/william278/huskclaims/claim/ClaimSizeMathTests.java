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

package net.william278.huskclaims.claim;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Claim Size Math Tests")
public class ClaimSizeMathTests {

    @Test
    @DisplayName("Side and area match Region.around geometry (2r+1 squared)")
    public void testSideAndArea() {
        assertEquals(1, ClaimSizeMath.side(0));
        assertEquals(21, ClaimSizeMath.side(10));
        assertEquals(1, ClaimSizeMath.area(0));
        assertEquals(441, ClaimSizeMath.area(10));
        assertEquals(10_000_200_001L, ClaimSizeMath.area(50_000)); // 100001^2
    }

    @Test
    @DisplayName("maxRadiusFor returns the largest affordable radius")
    public void testMaxRadiusFor() {
        assertEquals(10, ClaimSizeMath.maxRadiusFor(441));
        assertEquals(9, ClaimSizeMath.maxRadiusFor(440));
        assertEquals(0, ClaimSizeMath.maxRadiusFor(1));
        assertEquals(-1, ClaimSizeMath.maxRadiusFor(0));
        assertEquals(-1, ClaimSizeMath.maxRadiusFor(-5));
    }

    @Test
    @DisplayName("minRadius derives from minimum_claim_size (shortest edge)")
    public void testMinRadius() {
        assertEquals(2, ClaimSizeMath.minRadius(5));  // 5x5 claim
        assertEquals(3, ClaimSizeMath.minRadius(6));
        assertEquals(1, ClaimSizeMath.minRadius(1));  // ClaimCommand clamps radius to >= 1
    }

    @Test
    @DisplayName("Large values do not overflow")
    public void testNoOverflow() {
        assertEquals(1_000_002_000_001L, ClaimSizeMath.area(500_000)); // 1000001^2
        assertTrue(ClaimSizeMath.maxRadiusFor(1_000_000_000_000L) > 400_000);
    }

    @Test
    @DisplayName("Property: area(maxRadiusFor(b)) fits in b; area(r+1) does not")
    public void testMaxRadiusProperty() {
        for (long b = 1; b <= 100_000; b += 997) { // sampled sweep, co-prime step
            final int r = ClaimSizeMath.maxRadiusFor(b);
            assertTrue(ClaimSizeMath.area(r) <= b, "area(r) must fit in b for b=" + b);
            assertTrue(ClaimSizeMath.area(r + 1) > b, "area(r+1) must exceed b for b=" + b);
        }
    }

    @Test
    @DisplayName("hardMaxRadius combines radius and action-size limits")
    public void testHardMaxRadius() {
        assertEquals(100, ClaimSizeMath.hardMaxRadius(100, 45_000));
        assertEquals(105, ClaimSizeMath.hardMaxRadius(200, 45_000)); // action size binds first
        assertEquals(200, ClaimSizeMath.hardMaxRadius(200, 0));      // 0 = unlimited (defensive)
        assertEquals(0, ClaimSizeMath.hardMaxRadius(0, 45_000));
    }

    // resize edge/uniform helpers ----------------------------------------------

    @Test
    @DisplayName("maxEdgeGrowth: perpendicular-length division, floored and capped")
    public void testMaxEdgeGrowth() {
        assertEquals(4, ClaimSizeMath.maxEdgeGrowth(441, 100));
        assertEquals(0, ClaimSizeMath.maxEdgeGrowth(0, 100));   // no balance
        assertEquals(0, ClaimSizeMath.maxEdgeGrowth(441, 0));   // degenerate length
        assertEquals(0, ClaimSizeMath.maxEdgeGrowth(-5, 100));  // defensive
        assertEquals(10_000, ClaimSizeMath.maxEdgeGrowth(Long.MAX_VALUE, 1)); // capped
        // Property: cost at k fits, k+1 does not (cost = k * perpendicular)
        for (long blocks = 1; blocks <= 5_000; blocks += 131) {
            final int perp = 7;
            final int k = ClaimSizeMath.maxEdgeGrowth(blocks, perp);
            assertTrue((long) k * perp <= blocks, "k*perp must fit blocks=" + blocks);
            assertTrue((long) (k + 1) * perp > blocks || k == 10_000,
                    "k+1 must exceed blocks=" + blocks);
        }
    }

    @Test
    @DisplayName("maxEdgeShrink: length minus minimum, floored at 0")
    public void testMaxEdgeShrink() {
        assertEquals(5, ClaimSizeMath.maxEdgeShrink(10, 5));
        assertEquals(0, ClaimSizeMath.maxEdgeShrink(5, 5));   // already minimal
        assertEquals(0, ClaimSizeMath.maxEdgeShrink(3, 5));   // below minimum
        assertEquals(9, ClaimSizeMath.maxEdgeShrink(14, 5));
        // Property: shrunk length >= minimum, one more would violate
        for (int length = 1; length <= 30; length++) {
            final int min = 5;
            final int k = ClaimSizeMath.maxEdgeShrink(length, min);
            assertTrue(length - k >= min || k == 0, "shrunk length must respect min");
            if (k > 0) {
                assertTrue(length - (k + 1) < min, "k+1 must violate min");
            }
        }
    }

    @Test
    @DisplayName("maxUniformGrowth: brute-force cross-check for small inputs")
    public void testMaxUniformGrowth() {
        for (int w = 1; w <= 12; w += 3) {
            for (int d = 1; d <= 12; d += 3) {
                for (long blocks = 0; blocks <= 200; blocks += 17) {
                    final int expected = bruteUniformGrowth(w, d, blocks);
                    final int actual = ClaimSizeMath.maxUniformGrowth(w, d, blocks);
                    assertEquals(expected, actual,
                            "w=" + w + " d=" + d + " blocks=" + blocks);
                }
            }
        }
        assertEquals(0, ClaimSizeMath.maxUniformGrowth(5, 5, 0));
        assertEquals(10_000, ClaimSizeMath.maxUniformGrowth(1, 1, Long.MAX_VALUE)); // capped
    }

    @Test
    @DisplayName("maxUniformShrink: keeps the shorter edge >= minimum; brute-force check")
    public void testMaxUniformShrink() {
        for (int w = 1; w <= 14; w += 2) {
            for (int d = 1; d <= 14; d += 2) {
                for (int min = 1; min <= 7; min += 2) {
                    final int actual = ClaimSizeMath.maxUniformShrink(w, d, min);
                    final int brute = bruteUniformShrink(w, d, min);
                    assertEquals(brute, actual,
                            "w=" + w + " d=" + d + " min=" + min);
                    if (actual > 0) {
                        // shorter edge loses 2k (both opposite edges move in)
                        assertTrue(Math.min(w, d) - 2 * actual >= min,
                                "k=" + actual + " must keep shorter edge >= min");
                    }
                }
            }
        }
        assertEquals(0, ClaimSizeMath.maxUniformShrink(5, 5, 5));
        assertEquals(0, ClaimSizeMath.maxUniformShrink(4, 10, 5)); // (4-5)/2 -> 0
        assertEquals(2, ClaimSizeMath.maxUniformShrink(10, 12, 5));
    }

    /** Largest k with (w+2k)(d+2k) − w·d ≤ blocks (reference brute force). */
    private static int bruteUniformGrowth(int w, int d, long blocks) {
        int k = 0;
        while ((long) (w + 2 * (k + 1)) * (d + 2 * (k + 1)) - (long) w * d <= blocks) {
            k++;
        }
        return k;
    }

    /** Largest k with min(w,d) − 2k ≥ min (reference brute force). */
    private static int bruteUniformShrink(int w, int d, int min) {
        int k = 0;
        while (Math.min(w, d) - 2 * (k + 1) >= min) {
            k++;
        }
        return k;
    }

}
