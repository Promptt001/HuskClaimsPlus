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

/**
 * Claim-size arithmetic shared by GUI previews; mirrors {@code Region.around}
 * and the {@code ClaimEditor} checks. Pure, unit-testable, no
 * Bukkit dependencies.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class ClaimSizeMath {

    private ClaimSizeMath() {
    }

    /** {@code Region.around(pos, r)} spans x−r..x+r → side = 2r+1. */
    public static long side(int radius) {
        return 2L * radius + 1;
    }

    /** Area of a square claim of the given radius. */
    public static long area(int radius) {
        final long s = side(radius);
        return s * s;
    }

    /**
     * Smallest radius whose side satisfies {@code minimum_claim_size}
     * (shortest edge ≥ min). {@code ClaimCommand} clamps radius to ≥ 1.
     */
    public static int minRadius(int minimumClaimSize) {
        return Math.max(1, (int) Math.ceil((minimumClaimSize - 1) / 2.0));
    }

    /**
     * Largest radius whose area fits within the given blocks, or −1 if even
     * radius 0 is unaffordable (blocks less than 1).
     */
    public static int maxRadiusFor(long blocks) {
        if (blocks < 1) {
            return -1;
        }
        int r = (int) Math.floor((Math.sqrt((double) blocks) - 1) / 2);
        while (r >= 0 && area(r) > blocks) {
            r--;
        }
        while (area(r + 1) <= blocks) {
            r++;
        }
        return r;
    }

    /**
     * Largest radius allowed by every non-balance limit (max claim radius,
     * max action size; ≤ 0 max action size treated as unlimited — defensive).
     */
    public static int hardMaxRadius(long maxClaimRadius, long maxActionSize) {
        final int byAction = maxActionSize <= 0 ? Integer.MAX_VALUE : maxRadiusFor(maxActionSize);
        return (int) Math.min(Math.min(maxClaimRadius, Integer.MAX_VALUE), byAction);
    }

    // Resize: edge and uniform growth/shrink ------------------------------------

    /**
     * Largest distance a single edge can move outwards given the claim-block
     * balance and the length of the perpendicular edge (CAP mode, as create).
     * Capped at 10 000 to stay well inside region arithmetic and int range.
     */
    public static int maxEdgeGrowth(long blocks, int perpendicularLength) {
        if (perpendicularLength <= 0 || blocks <= 0) {
            return 0;
        }
        return (int) Math.min(10_000L, blocks / perpendicularLength);
    }

    /**
     * Largest distance a single edge can move inwards keeping the moved axis
     * at least {@code minimumClaimSize} blocks long (the editor's
     * {@code getShortestEdge() < minimumClaimSize} check).
     */
    public static int maxEdgeShrink(int length, int minimumSize) {
        return Math.max(0, length - minimumSize);
    }

    /**
     * Largest uniform (all-sides) outwards distance given both edge lengths
     * and the balance: largest k with (w+2k)(d+2k) − w·d ≤ blocks.
     */
    public static int maxUniformGrowth(int w, int d, long blocks) {
        int k = 0;
        while (k < 10_000 && (long) (w + 2 * (k + 1)) * (d + 2 * (k + 1)) - (long) w * d <= blocks) {
            k++;
        }
        return k;
    }

    /**
     * Largest uniform (all-sides) inwards distance keeping both edges at
     * least {@code minimumClaimSize} long (moves both opposite edges, so the
     * shorter edge loses 2k).
     */
    public static int maxUniformShrink(int w, int d, int minimumSize) {
        return Math.max(0, (Math.min(w, d) - minimumSize) / 2);
        }

}
