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

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

/**
 * Teleports a player to a claim warp, applying every rule a visit must pass.
 * Shared by {@code /visit} and the Public Claims menu so both behave
 * identically (the sequence was previously copy-pasted in both callers).
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class ClaimWarpVisits {

    private ClaimWarpVisits() {
    }

    /**
     * Send a visitor through a claim's warp: cooldown, ban and private-claim
     * navigation, private-warp gate, anchor-still-inside-claim, safe standing
     * spot, then teleport + stamp + message.
     *
     * @param plugin  the plugin
     * @param visitor the player teleporting
     * @param world   the claim world the claim lives in
     * @param claim   the claim whose warp is being used
     */
    public static void visit(@NotNull HuskClaims plugin, @NotNull OnlineUser visitor,
                             @NotNull ClaimWorld world, @NotNull Claim claim) {
        final ClaimWarp warp = claim.getWarp();
        if (warp == null) {
            plugin.getLocales().getLocale("claim_warp_not_set").ifPresent(visitor::sendMessage);
            return;
        }
        // These two checks send their own explanation to the visitor
        if (world.isBannedFromClaim(visitor, claim, plugin)
                || world.cannotNavigatePrivateClaim(visitor, claim, plugin)) {
            return;
        }
        if (!warp.isPubliclyAccessible() && !claim.canUseWarp(visitor, plugin)
                && !ClaimWarpNames.hasWarpOverride(plugin, visitor)) {
            plugin.getLocales().getLocale("claim_warp_not_public", warp.getName()).ifPresent(visitor::sendMessage);
            return;
        }
        // The owner may have shrunk the claim since setting the warp
        if (!claim.getRegion().contains(warp.getAnchor())) {
            plugin.getLocales().getLocale("claim_warp_outside_claim", warp.getName()).ifPresent(visitor::sendMessage);
            return;
        }
        if (!WarpCooldowns.tryStart(plugin, visitor.getUuid())) {
            plugin.getLocales().getLocale("claim_warp_cooldown").ifPresent(visitor::sendMessage);
            return;
        }
        // The owner may also have changed the terrain, so check the exact spot again now
        plugin.isSafeStandingSpot(warp.getAnchor()).whenComplete((safe, error) -> {
            if (error != null || !Boolean.TRUE.equals(safe)) {
                WarpCooldowns.finish(plugin, visitor.getUuid(), false);
                plugin.getLocales().getLocale("claim_warp_anchor_unsafe").ifPresent(visitor::sendMessage);
                return;
            }
            plugin.runSync(() -> {
                visitor.teleport(warp.getAnchor(), false);
                // The cooldown starts only once the player has actually been teleported
                WarpCooldowns.finish(plugin, visitor.getUuid(), true);
                plugin.getLocales().getLocale("claim_warp_teleported", warp.getName()).ifPresent(visitor::sendMessage);
            });
        });
    }

}
