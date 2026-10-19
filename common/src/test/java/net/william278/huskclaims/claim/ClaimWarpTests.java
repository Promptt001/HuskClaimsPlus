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

import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.position.World;
import net.william278.huskclaims.user.User;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.Map;

@DisplayName("Claim Warp Tests")
public class ClaimWarpTests {

    private static final User OWNER = User.of(UUID.fromString("00000000-0000-0000-0000-000000000001"), "William278");
    private static final World WORLD = World.of("world", UUID.randomUUID(), "normal");

    @NotNull
    private static Position at(double x, double y, double z) {
        return Position.at(x, y, z, WORLD);
    }

    @NotNull
    private static Claim newClaim() {
        // Protected constructor is accessible from the same package; avoids needing a plugin instance
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
    @DisplayName("Test warp is null by default (stock behavior)")
    public void testWarpNullByDefault() {
        final Claim claim = newClaim();
        assertNull(claim.getWarp(), "New claims should not have a warp");
    }

    @Test
    @DisplayName("Test warp stores name, setter and anchor")
    public void testWarpStoresFields() {
        final ClaimWarp warp = ClaimWarp.of("farm", OWNER, at(5.5, 64, 5.5));
        assertEquals("farm", warp.getName());
        assertEquals(OWNER.getUuid(), warp.getSetter());
        assertEquals(at(5.5, 64, 5.5), warp.getAnchor());
    }

    @Test
    @DisplayName("Test warp name equality is case-insensitive")
    public void testWarpNameCaseInsensitive() {
        final ClaimWarp warp = ClaimWarp.of("Farm", OWNER, at(1, 64, 1));
        assertEquals(ClaimWarp.of("FARM", OWNER, at(2, 64, 2)), warp);
        assertNotEquals(ClaimWarp.of("mine", OWNER, at(1, 64, 1)), warp);
    }

    @Test
    @DisplayName("Test claim warp lookup by name in a world")
    public void testWarpLookupByWorld() {
        final Claim claim = newClaim();
        claim.setWarp(ClaimWarp.of("farm", OWNER, at(5, 64, 5)));

        final ClaimWorld world = ClaimWorld.convert(
                Set.of(claim), Map.of(), Set.of());
        assertTrue(world.getClaimByWarpName("FARM").isPresent(), "Lookup should be case-insensitive");
        assertEquals(claim, world.getClaimByWarpName("farm").get());
        assertTrue(world.getClaimByWarpName("mine").isEmpty());
        assertEquals(1, world.getWarpClaims().size());

        claim.setWarp(null);
        assertTrue(world.getClaimByWarpName("farm").isEmpty(), "Unset warp should no longer be findable");
    }

    @Test
    @DisplayName("Test warp serializes and deserializes via Gson")
    public void testWarpGsonRoundtrip() {
        final Claim claim = newClaim();
        claim.setWarp(ClaimWarp.of("farm", OWNER, Position.at(5.5, 64.25, 5.5, 90f, 15f, WORLD)));

        final com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
        final String json = gson.toJson(claim, Claim.class);
        assertTrue(json.contains("\"claim_warp\""), "Warp should be serialized under claim_warp: " + json);
        assertTrue(json.contains("\"farm\""));
        assertTrue(json.contains(OWNER.getUuid().toString()));

        final Claim deserialized = gson.fromJson(json, Claim.class);
        assertNotNull(deserialized.getWarp(), "Warp should survive a roundtrip");
        assertEquals("farm", deserialized.getWarp().getName());
        assertEquals(OWNER.getUuid(), deserialized.getWarp().getSetter());
        final Position anchor = deserialized.getWarp().getAnchor();
        assertEquals(5.5, anchor.getX(), 0.001);
        assertEquals(64.25, anchor.getY(), 0.001);
        assertEquals(5.5, anchor.getZ(), 0.001);
        assertEquals(90f, anchor.getYaw(), 0.001);
        assertEquals(15f, anchor.getPitch(), 0.001);
        assertEquals(WORLD.getName(), anchor.getWorld().getName());
    }

    @Test
    @DisplayName("Test legacy claim JSON without warp deserializes with null warp")
    public void testLegacyClaimJsonWithoutWarp() {
        final String legacyJson = """
                {
                  "region": {
                    "near_corner": {"x": 0, "z": 0},
                    "far_corner": {"x": 10, "z": 10}
                  },
                  "owner": "00000000-0000-0000-0000-000000000001",
                  "trusted_users": {},
                  "trusted_groups": {},
                  "trusted_tags": {},
                  "banned_users": {},
                  "children": [],
                  "default_flags": [],
                  "inherit_parent": true,
                  "private_claim": false
                }
                """;
        final com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .excludeFieldsWithoutExposeAnnotation()
                .create();
        final Claim claim = gson.fromJson(legacyJson, Claim.class);
        assertNull(claim.getWarp(), "Legacy claims should deserialize with no warp");
        assertNotNull(claim.getOwner());
        assertTrue(claim.getTrustedUsers().isEmpty());
    }


    @Test
    @DisplayName("Warp privacy: new warps are public by default; legacy null deserializes public")
    public void testWarpPrivacyDefaults() {
        // User request 2026-10-14: /claimwarp set creates PUBLIC warps (listed in
        // /visits + Public Claims); owners restrict them via the Public
        // Permissions toggle. Legacy warps saved before the field existed read
        // as null and must behave as public (original semantics).
        final ClaimWarp warp = ClaimWarp.of("shop", OWNER, at(0, 64, 0));
        assertTrue(warp.isPubliclyAccessible(), "new warps must default to public");
        warp.setPubliclyAccessible(false);
        assertFalse(warp.isPubliclyAccessible(), "restriction must stick");
        // Legacy save (JSON without the field): Gson leaves it null -> must read as public
        final ClaimWarp legacy = new com.google.gson.Gson().fromJson(
                "{\"name\":\"old\",\"setter\":\"00000000-0000-0000-0000-000000000001\"}", ClaimWarp.class);
        assertTrue(legacy.isPubliclyAccessible(), "null field (legacy save) must read as public");
    }

    @Test
    @DisplayName("Re-setting a private warp must keep it private")
    public void testReSetPrivateWarpStaysPrivate() {
        // /claimwarp set on a claim that already has a PRIVATE warp must carry the
        // old visibility onto the new warp object — moving a private warp must not
        // silently re-publish it to /visits + Public Claims. (ClaimWarpCommand.setWarp
        // calls exactly this sequence before committing the replacement.)
        final Claim claim = newClaim();
        final ClaimWarp first = ClaimWarp.of("shop", OWNER, at(0, 64, 0));
        first.setPubliclyAccessible(false);
        claim.setWarp(first);
        assertFalse(claim.getWarp().isPubliclyAccessible());

        final ClaimWarp moved = ClaimWarp.of("shop", OWNER, at(8, 64, 8));
        if (claim.getWarp() != null) {
            moved.setPubliclyAccessible(claim.getWarp().isPubliclyAccessible());
        }
        claim.setWarp(moved);

        assertFalse(claim.getWarp().isPubliclyAccessible(),
                "moving a private warp must keep it private");
        assertTrue(claim.getWarp().getAnchor().equals(at(8, 64, 8)),
                "the new anchor must be the one stored");
    }
}
