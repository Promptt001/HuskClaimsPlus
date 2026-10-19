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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ClaimAccess}: the rule deciding who may open and use the
 * Claim Settings menu (and its owner-only actions) for a claim.
 */
public class ClaimAccessTests {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @NotNull
    private static Claim claimOf(@NotNull UUID owner) {
        // Same protected-constructor pattern as ClaimSnapshotTests (same package)
        return new Claim(
                OWNER.equals(owner) ? OWNER : FRIEND.equals(owner) ? FRIEND : owner,
                Region.from(Region.Point.at(0, 0), Region.Point.at(10, 10)),
                com.google.common.collect.Maps.newConcurrentMap(),
                com.google.common.collect.Maps.newConcurrentMap(),
                com.google.common.collect.Maps.newConcurrentMap(),
                com.google.common.collect.Maps.newConcurrentMap(),
                com.google.common.collect.Sets.newConcurrentHashSet(),
                true,
                com.google.common.collect.Sets.newConcurrentHashSet(),
                false
        );
    }

    @Test
    @DisplayName("The owner may manage their claim's settings")
    public void ownerMayManage() {
        assertTrue(ClaimAccess.mayManageSettings(claimOf(OWNER), OWNER, false, false));
    }

    @Test
    @DisplayName("A trusted non-owner may not manage the claim (trust manages trustees, not the claim)")
    public void trustedNonOwnerMayNotManage() {
        assertFalse(ClaimAccess.mayManageSettings(claimOf(OWNER), FRIEND, false, false));
    }

    @Test
    @DisplayName("Staff holding the unclaim.other override may manage another player's claim")
    public void overrideHolderMayManageOthers() {
        assertTrue(ClaimAccess.mayManageSettings(claimOf(OWNER), FRIEND, false, true));
    }

    @Test
    @DisplayName("Admin claims stay admin-only: the unclaim.other override alone is not enough")
    public void adminClaimRejectsOverrideWithoutAdminPermission() {
        final Claim adminClaim = claimOf(null);
        assertFalse(ClaimAccess.mayManageSettings(adminClaim, FRIEND, false, true));
    }

    @Test
    @DisplayName("Staff with admin-claim permission may manage an admin claim")
    public void adminClaimAllowsAdminPermission() {
        final Claim adminClaim = claimOf(null);
        assertTrue(ClaimAccess.mayManageSettings(adminClaim, FRIEND, true, false));
    }

    @Test
    @DisplayName("A child claim is judged by its parent: a child of someone else's claim is not manageable")
    public void childOfOthersClaimRejected() {
        final Claim parent = claimOf(OWNER);
        final Claim child = claimOf(OWNER);
        child.setParent(parent);
        assertFalse(ClaimAccess.mayManageSettings(child, FRIEND, false, false));
    }

    @Test
    @DisplayName("A child claim is judged by its parent: the parent owner may manage their own child")
    public void ownChildAllowed() {
        final Claim parent = claimOf(OWNER);
        final Claim child = claimOf(OWNER);
        child.setParent(parent);
        assertTrue(ClaimAccess.mayManageSettings(child, OWNER, false, false));
    }

}
