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
import net.william278.huskclaims.claim.ClaimNaming;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Claim display names: stored in the claim's generic {@code @Expose}d metadata
 * map (persisted with the claim world), with a namespaced key so it can't
 * collide with third-party metadata. Names are sanitized so they can never
 * inject formatting or MineDown click events into item names or chat.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@ApiStatus.Internal
public final class ClaimNames {

    /** Metadata key under which the GUI stores the player-chosen claim name (alias of the common key). */
    static final String NAME_KEY = ClaimNaming.NAME_KEY;

    /** Maximum claim name length after trimming. */
    static final int MAX_LENGTH = 32;

    private ClaimNames() {
    }

    /**
     * Sanitize a proposed claim name: trim, strip {@code &}/{@code §} colour
     * codes, MineDown brackets and newlines, and cap the length. Returns empty
     * if nothing valid remains.
     *
     * @param raw the raw chat-captured name
     * @return the sanitized name, or empty if invalid
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static Optional<String> sanitize(@NotNull String raw) {
        // Loop until stable: one pass leaves "&&aa" as "&a", because the
        // stripped & pairs with the next letter. Section signs are never typed
        // on purpose, so they all go before the code pairs are stripped.
        String prev;
        String name = raw.trim();
        do {
            prev = name;
            name = name
                    .replace("§", "")                          // section signs are never typed on purpose
                    .replaceAll("&[0-9a-fk-orxA-FK-ORX#]", "")   // legacy and hex colour-code starters
                    .replaceAll("\\[", "(")                      // MineDown link/click syntax
                    .replaceAll("]", ")")
                    .replaceAll("[\\r\\n]", " ")                 // no newlines in item names
                    .trim();
        } while (!name.equals(prev));
        if (name.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH) : name);
    }

    /**
     * The display name for a claim: the stored GUI name if set, else the claim
     * warp's name, else a localized default (world + centre).
     *
     * @param claim the claim
     * @param world the claim's world
     * @param plugin the plugin
     * @return the display name (never blank)
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static String displayName(@NotNull Claim claim, @NotNull String worldName,
                              @NotNull HuskClaims plugin) {
        final String stored = claim.getMetadata().get(NAME_KEY);
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        if (claim.getWarp() != null && !claim.getWarp().getName().isBlank()) {
            return claim.getWarp().getName();
        }
        final Region.Point centre = claim.getRegion().getCenter();
        return plugin.getLocales().getRawLocale("gui_claim_default_name",
                        worldName, Integer.toString(centre.getBlockX()),
                        Integer.toString(centre.getBlockZ()))
                .orElse("Claim");
    }

    /**
     * Store a sanitized claim name in claim metadata and persist the claim world.
     * Call on any thread; persistence goes through {@code runQueued} (the same
     * call {@code TrustCommand} uses).
     *
     * @param claim the claim to name
     * @param world the claim's world
     * @param plugin the plugin
     * @param sanitized the sanitized name to store
     * @since HuskClaimsPlus 1.0.0
     */
    public static void setName(@NotNull Claim claim, @NotNull ClaimWorld world,
                        @NotNull HuskClaims plugin, @NotNull String sanitized) {
        // The owner named the claim deliberately: store it and clear the auto marker
        ClaimNaming.setChosenName(claim, sanitized);
        plugin.runQueued(() -> plugin.getDatabase().updateClaimWorld(world));
    }

}
