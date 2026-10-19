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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default claim naming and the rule that keeps a claim's name and its warp name in step.
 * The metadata keys live here (common) so {@code ClaimSnapshot},
 * {@code ClaimWarpCommand} and the bukkit GUI all share them.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class ClaimNaming {

    /** Player-chosen (or auto-assigned) display name. Unchanged key: existing data keeps working. */
    public static final String NAME_KEY = "huskclaims:gui_name";
    /** "true" while NAME_KEY holds an auto-assigned default; cleared once the owner names the claim. */
    public static final String AUTO_KEY = "huskclaims:gui_name_auto";

    private ClaimNaming() {
    }

    /**
     * The next default name for an owner: {@code <owner>_<NN>} where NN is the highest
     * numeric suffix among their existing names + 1 (max + 1, never lowest-gap), at
     * least 2 digits, case-insensitive, player-name-regex-quoted.
     *
     * @param ownerName     the owner's name
     * @param existingNames the owner's current claim/warp names across all worlds
     * @return e.g. {@code Bob_01} for no names, {@code Bob_04} for [Bob_01, Bob_03]
     */
    @NotNull
    public static String nextDefaultName(@NotNull String ownerName, @NotNull Collection<String> existingNames) {
        final Pattern p = Pattern.compile("^" + Pattern.quote(ownerName) + "_(\\d{1,9})$", Pattern.CASE_INSENSITIVE);
        int max = 0;
        for (String n : existingNames) {
            if (n == null) {
                continue;
            }
            final Matcher m = p.matcher(n.trim());
            if (m.matches()) {
                max = Math.max(max, Integer.parseInt(m.group(1))); // \d{1,9} cannot overflow int
            }
        }
        return String.format(Locale.ROOT, "%s_%02d", ownerName, max + 1);
    }

    /** Names that "occupy" a number for this claim: stored name and warp name. */
    @NotNull
    public static List<String> namesOf(@NotNull Claim claim) {
        final List<String> out = new ArrayList<>(2);
        final String stored = claim.getMetadata().get(NAME_KEY);
        if (stored != null && !stored.isBlank()) {
            out.add(stored);
        }
        if (claim.getWarp() != null && !claim.getWarp().getName().isBlank()) {
            out.add(claim.getWarp().getName());
        }
        return out;
    }

    public static boolean isAuto(@NotNull Claim claim) {
        return "true".equals(claim.getMetadata().get(AUTO_KEY));
    }

    /** Deliberate naming (GUI rename or warp naming): store and clear the auto marker. */
    public static void setChosenName(@NotNull Claim claim, @NotNull String name) {
        claim.getMetadata().put(NAME_KEY, name);
        claim.getMetadata().remove(AUTO_KEY);
    }

    /**
     * Assign a default name if the claim has none. Returns true if metadata changed
     * (caller persists the world). Top-level player claims only. A claim with a warp
     * adopts the warp name (what the player already sees).
     */
    public static boolean assignDefaultIfMissing(@NotNull HuskClaims plugin, @NotNull Claim claim,
                                                 @NotNull String ownerName) {
        if (claim.isChildClaim() || claim.getOwner().isEmpty()) {
            return false;
        }
        final String stored = claim.getMetadata().get(NAME_KEY);
        if (stored != null && !stored.isBlank()) {
            return false;
        }
        if (claim.getWarp() != null && !claim.getWarp().getName().isBlank()) {
            setChosenName(claim, claim.getWarp().getName()); // what the player already sees
            return true;
        }
        final UUID owner = claim.getOwner().get();
        final List<String> names = plugin.getClaimWorlds().values().stream()
                .flatMap(w -> w.getClaimsByUser(owner).stream())
                .filter(c -> c != claim)
                .flatMap(c -> namesOf(c).stream())
                .toList();
        claim.getMetadata().put(NAME_KEY, nextDefaultName(ownerName, names));
        claim.getMetadata().put(AUTO_KEY, "true");
        return true;
    }

}
