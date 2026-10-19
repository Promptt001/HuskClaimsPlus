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

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Identifies a claim for as long as a menu is open.
 * <p>
 * Claims have no id, and {@link Claim#equals} compares regions, which change when a claim is
 * resized. So a menu never keeps a {@link Claim} object between clicks. It keeps this record
 * (world name, owner and corner coordinates) and looks the claim up again on every click. If
 * that fails, the claim was deleted, resized or transferred in the meantime, and the menu says so.
 */
@ApiStatus.Internal
public record ClaimRef(@NotNull String worldName, @Nullable UUID owner,
                       int x1, int z1, int x2, int z2, @Nullable ClaimRef parent) {

    @NotNull
    public static ClaimRef of(@NotNull String worldName, @NotNull Claim claim) {
        final Region r = claim.getRegion();
        return new ClaimRef(worldName, claim.getOwner().orElse(null),
                r.getNearCorner().getBlockX(), r.getNearCorner().getBlockZ(),
                r.getFarCorner().getBlockX(), r.getFarCorner().getBlockZ(),
                claim.getParent().map(p -> ClaimRef.of(worldName, p)).orElse(null));
    }

    /**
     * Finds the claim this reference points at, in its own world only.
     * <p>
     * Returns empty when the claim was deleted, resized or transferred since the menu was
     * built. It never looks in other worlds: a claim with the same owner and corners in
     * another world is a different claim, and acting on it would change the wrong land.
     */
    @NotNull
    public Optional<Resolved> resolve(@NotNull HuskClaims plugin) {
        return plugin.getClaimWorld(worldName).flatMap(world -> candidates(plugin, world).stream()
                .filter(this::matches)
                .findFirst()
                .map(claim -> new Resolved(worldName, world, claim)));
    }

    /** The claims this reference may match: its parent's children, or its owner's top-level claims. */
    @NotNull
    private Collection<Claim> candidates(@NotNull HuskClaims plugin, @NotNull ClaimWorld world) {
        if (parent != null) {
            return parent.resolve(plugin)
                    .<Collection<Claim>>map(resolvedParent -> resolvedParent.claim().getChildren())
                    .orElse(List.of());
        }
        // Admin claims are stored under a fixed placeholder owner id rather than null
        return world.getClaimsByUser(owner != null ? owner : ClaimWorld.ADMIN_CLAIM);
    }

    public boolean matches(@NotNull Claim c) {
        final Region r = c.getRegion();
        return r.getNearCorner().getBlockX() == x1 && r.getNearCorner().getBlockZ() == z1
                && r.getFarCorner().getBlockX() == x2 && r.getFarCorner().getBlockZ() == z2;
    }

    /** A successfully resolved claim reference: the world it was found in, and the live claim. */
    public record Resolved(@NotNull String worldName, @NotNull ClaimWorld world, @NotNull Claim claim) {
    }

}
