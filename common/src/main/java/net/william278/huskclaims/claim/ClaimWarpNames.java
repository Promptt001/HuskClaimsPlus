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
import net.william278.huskclaims.command.ClaimWarpCommand;
import net.william278.huskclaims.user.CommandUser;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Warp-name uniqueness across the WHOLE server and warp-name validation, used
 * by the claimwarp command and the GUI.
 *
 * <p>Uniqueness: across all claim worlds, including the executor's own world.
 * Skipping the executor's own world would let two claims in one world share a
 * warp name, and a private duplicate could then shadow a public one in
 * /visit.</p>
 *
 * <p>Validation: names are a strict whitelist of letters,
 * digits, underscore, dot and hyphen. Warp names are passed raw into
 * MineDown-formatted chat and clickable run_command URLs (e.g. /visits rows);
 * anything outside the whitelist could inject formatting or commands.
 * Existing warps with non-whitelisted names keep working — the whitelist
 * applies to new set/rename only.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class ClaimWarpNames {

    /** Warp names: letters, digits, underscore, dot, hyphen — nothing else. */
    private static final Pattern WHITELIST = Pattern.compile("[\\p{L}\\p{N}_.-]+");

    private ClaimWarpNames() {
    }

    /**
     * Whether a proposed warp name is valid: non-empty, at most
     * {@code maxLength} characters, and every character on the whitelist.
     */
    public static boolean isValid(@NotNull String name, int maxLength) {
        return !name.isEmpty() && name.length() <= maxLength && WHITELIST.matcher(name).matches();
    }

    /**
     * Whether a warp name is safe to embed in a clickable chat component. Names that
     * pass today's naming rules are; names created before those rules may not be, and
     * must be shown without a click action.
     */
    public static boolean isClickSafe(@NotNull String name) {
        return isValid(name, Integer.MAX_VALUE);
    }

    /**
     * Derives a warp name from a claim name: trim, collapse whitespace to
     * underscores, drop characters off the whitelist, cut to {@code maxLength}.
     * May return an empty string (caller must re-prompt).
     */
    @NotNull
    public static String fromClaimName(@NotNull String claimName, int maxLength) {
        final String cleaned = claimName.trim().replaceAll("\\s+", "_").replaceAll("[^\\p{L}\\p{N}_.-]", "");
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    /**
     * Whether a claim name and warp name are "in sync": the warp name is what
     * the claim name turns into as a warp name.
     */
    public static boolean isInSync(@NotNull String claimName, @NotNull String warpName, int maxLength) {
        return warpName.equalsIgnoreCase(fromClaimName(claimName, maxLength));
    }

    /**
     * Whether the user may use warps of claims they cannot otherwise use (the
     * {@code claimwarp.other} override). Asks the command for the node so it can
     * never drift from the registered {@code huskclaims.command.claimwarp.other}
     *.
     */
    public static boolean hasWarpOverride(@NotNull HuskClaims plugin, @NotNull CommandUser user) {
        return plugin.getCommands().stream()
                .filter(ClaimWarpCommand.class::isInstance)
                .anyMatch(command -> command.hasPermission(user, "other"));
    }

    /**
     * Whether a warp name is taken by any claim other than {@code self}, in any world.
     * Honors {@code claims.warps.enforceUniqueNames}; case-insensitive.
     */
    public static boolean isTaken(@NotNull HuskClaims plugin, @NotNull Claim self, @NotNull String name) {
        if (!plugin.getSettings().getClaims().getWarps().isEnforceUniqueNames()) {
            return false;
        }
        return plugin.getClaimWorlds().values().stream()
                .flatMap(w -> w.getClaims().stream())
                .anyMatch(c -> c != self && c.getWarp() != null
                        && c.getWarp().getName().equalsIgnoreCase(name));
    }

}
