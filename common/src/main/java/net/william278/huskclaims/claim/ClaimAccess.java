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

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Decides who may manage a claim from the claim GUI: open its settings menu,
 * rename it, resize it or delete it.
 * <p>
 * The rule deliberately mirrors upstream's owner-only commands
 * ({@code /unclaim}, {@code /extendclaim}, {@code /reduceclaim}), so the GUI
 * can never grant more than typing the command would. Trust levels such as
 * "Manage" let a player manage <em>trustees</em>, not the claim itself.
 */
public final class ClaimAccess {

    private ClaimAccess() {
    }

    /**
     * Whether a player may manage a claim's settings.
     *
     * @param claim              the claim; a child claim is judged by its parent
     * @param viewer             the player asking
     * @param mayEditAdminClaims whether the player may create and edit admin claims
     * @param hasOtherOverride   whether the player holds the staff override for
     *                           other players' claims ({@code huskclaims.command.unclaim.other})
     * @return {@code true} if the player may manage the claim
     */
    public static boolean mayManageSettings(@NotNull Claim claim, @NotNull UUID viewer,
                                            boolean mayEditAdminClaims, boolean hasOtherOverride) {
        final Claim topLevel = claim.getParent().orElse(claim);

        // Admin claims belong to the server, so only admin-claim staff may touch them
        if (topLevel.isAdminClaim()) {
            return mayEditAdminClaims;
        }

        // Player claims: the owner, or staff holding the override permission
        final boolean isOwner = topLevel.getOwner().map(viewer::equals).orElse(false);
        return isOwner || hasOtherOverride;
    }

}
