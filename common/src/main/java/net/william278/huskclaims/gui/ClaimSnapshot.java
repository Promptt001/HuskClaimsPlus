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

import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimNaming;
import net.william278.huskclaims.claim.Region;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Value copy of everything a claim menu renders, so the GUI can tell when an
 * upstream command's deferred mutation has actually landed. Empty means the
 * claim no longer resolves (deleted / resized / transferred).
 *
 * @since HuskClaimsPlus 1.0.0
 */
public record ClaimSnapshot(@NotNull Map<UUID, String> users, @NotNull Map<String, String> groups,
                            @NotNull Map<String, String> tags, boolean privateClaim,
                            @NotNull Set<OperationType> flags, @NotNull Region region, int children,
                            @Nullable String name, @Nullable String warpName, boolean warpPublic) {

    /**
     * Snapshot a claim's renderable state.
     *
     * @param claim the claim to copy (may be null — yields empty)
     * @return the snapshot, or empty when the claim does not resolve
     * @throws java.util.ConcurrentModificationException if an async upstream
     *         callback is mutating the claim right now — callers treat that as
     *         "not settled yet"
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static Optional<ClaimSnapshot> of(@Nullable Claim claim) {
        if (claim == null) {
            return Optional.empty();
        }
        return Optional.of(new ClaimSnapshot(
                new HashMap<>(claim.getTrustedUsers()),
                new HashMap<>(claim.getTrustedGroups()),
                new HashMap<>(claim.getTrustedTags()),
                claim.isPrivateClaim(),
                new HashSet<>(claim.getDefaultFlags()),
                claim.getRegion(),
                claim.getChildren().size(),
                claim.getMetadata().get(ClaimNaming.NAME_KEY),
                claim.getWarp() != null ? claim.getWarp().getName() : null,
                claim.getWarp() != null && claim.getWarp().isPubliclyAccessible()));
    }

}
