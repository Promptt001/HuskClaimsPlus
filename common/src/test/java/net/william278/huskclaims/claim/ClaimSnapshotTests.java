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

import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.claim.ClaimWarp;
import net.william278.huskclaims.gui.ClaimSnapshot;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.position.World;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.util.List;

@DisplayName("Claim snapshots: detect when a deferred mutation has landed")
public class ClaimSnapshotTests {

    private static final User OWNER = User.of(
            UUID.fromString("00000000-0000-0000-0000-000000000001"), "SnapshotOwner");

    @org.jetbrains.annotations.NotNull
    private static Claim newClaim() {
        // Same protected-constructor pattern as ClaimWarpTests (same package)
        return new Claim(
                OWNER.getUuid(),
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
    @DisplayName("Snapshot equality holds for an unchanged claim and null yields empty")
    public void unchangedClaimKeepsEquality() {
        final Claim claim = newClaim();
        final ClaimSnapshot a = ClaimSnapshot.of(claim).orElseThrow();
        final ClaimSnapshot b = ClaimSnapshot.of(claim).orElseThrow();
        assertEquals(a, b, "two snapshots of an unchanged claim must be equal");
        // Hash too: the settle predicate uses Set/Map semantics
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(ClaimSnapshot.of(null).isPresent(), "null claim yields empty");
    }

    @Test
    @DisplayName("Snapshot breaks after setUserTrustLevel (the P1 settle predicate)")
    public void snapshotBreaksAfterSetTrustLevel() {
        final Claim claim = newClaim();
        final ClaimSnapshot before = ClaimSnapshot.of(claim).orElseThrow();
        claim.setUserTrustLevel(UUID.fromString("00000000-0000-0000-0000-000000000002"),
                TrustLevel.builder().id("access").displayName("Access").weight(100)
                        .flags(List.of(OperationType.BLOCK_INTERACT))
                        .privileges(List.of()).build());
        assertFalse(ClaimSnapshot.of(claim).orElseThrow().equals(before),
                "setUserTrustLevel must change the snapshot");
    }

    @Test
    @DisplayName("Snapshot breaks after setPrivateClaim (the P1 settle predicate)")
    public void snapshotBreaksAfterSetPrivateClaim() {
        final Claim claim = newClaim();
        final ClaimSnapshot before = ClaimSnapshot.of(claim).orElseThrow();
        claim.setPrivateClaim(true);
        assertFalse(ClaimSnapshot.of(claim).orElseThrow().equals(before),
                "setPrivateClaim must change the snapshot");
    }

    @Test
    @DisplayName("Snapshot breaks after a resize (region change)")
    public void snapshotBreaksAfterResize() {
        // region has no setter (upstream @Getter only): two claims differing only
        // in region prove the snapshot records it — the settle predicate's P1 case
        final Claim small = newClaim();
        final Claim resized = new Claim(
                OWNER.getUuid(),
                Region.from(Region.Point.at(0, 0), Region.Point.at(20, 20)),
                com.google.common.collect.Maps.newConcurrentMap(),
                com.google.common.collect.Maps.newConcurrentMap(),
                com.google.common.collect.Maps.newConcurrentMap(),
                com.google.common.collect.Maps.newConcurrentMap(),
                com.google.common.collect.Sets.newConcurrentHashSet(),
                true,
                com.google.common.collect.Sets.newConcurrentHashSet(),
                false
        );
        assertFalse(ClaimSnapshot.of(resized).orElseThrow()
                        .equals(ClaimSnapshot.of(small).orElseThrow()),
                "a resize must change the snapshot");
    }

    @Test
    @DisplayName("A snapshot changes when the warp is set and when it is unset")
    public void snapshotBreaksAfterWarpSetAndUnset() {
        final World world = World.of("world", UUID.randomUUID(), "normal");
        final Claim claim = newClaim();
        final ClaimSnapshot before = ClaimSnapshot.of(claim).orElseThrow();
        claim.setWarp(ClaimWarp.of("farm", OWNER, Position.at(5, 64, 5, world)));
        assertFalse(ClaimSnapshot.of(claim).orElseThrow().equals(before),
                "setting a warp must change the snapshot");
        final ClaimSnapshot withWarp = ClaimSnapshot.of(claim).orElseThrow();
        claim.setWarp(null);
        assertFalse(ClaimSnapshot.of(claim).orElseThrow().equals(withWarp),
                "unsetting a warp must change the snapshot");
    }

    @Test
    @DisplayName("A snapshot changes when the claim is renamed")
    public void snapshotBreaksAfterRename() {
        final Claim claim = newClaim();
        final ClaimSnapshot before = ClaimSnapshot.of(claim).orElseThrow();
        claim.getMetadata().put(ClaimNaming.NAME_KEY, "Castle");
        assertFalse(ClaimSnapshot.of(claim).orElseThrow().equals(before),
                "storing a claim name must change the snapshot");
    }
}
