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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Claim names")
public class ClaimNamingTests {

    @Test
    @DisplayName("nextDefaultName: empty list yields <owner>_01")
    public void firstDefaultIs01() {
        assertEquals("Bob_01", ClaimNaming.nextDefaultName("Bob", List.of()));
    }

    @Test
    @DisplayName("nextDefaultName: max+1, never lowest gap")
    public void maxPlusOne() {
        assertEquals("Bob_04", ClaimNaming.nextDefaultName("Bob", List.of("Bob_01", "Bob_03")));
        assertEquals("Bob_02", ClaimNaming.nextDefaultName("Bob", List.of("bob_01")));
    }

    @Test
    @DisplayName("nextDefaultName: case-insensitive match, other owners and non-numeric ignored")
    public void caseInsensitiveAndIgnoresNonMatching() {
        assertEquals("Bob_03", ClaimNaming.nextDefaultName("Bob", List.of("bob_02", "Bobby_05", "Bob_x", "Bob", "Bob_0")));
        // regex-special player names are quoted: "a.b_07" counts, next is a.b_08 (raw name kept)
        assertEquals("a.b_08", ClaimNaming.nextDefaultName("a.b", List.of("a.b_07")));
        assertEquals("a_b_02", ClaimNaming.nextDefaultName("a_b", List.of("a.b_07", "a_b_01")));
    }

    @Test
    @DisplayName("nextDefaultName: rolls to three digits at 99")
    public void rollsToThreeDigits() {
        assertEquals("Bob_100", ClaimNaming.nextDefaultName("Bob", List.of("Bob_99")));
    }

    @Test
    @DisplayName("Warp names accept letters, digits, underscore, dot and hyphen, including non-Latin")
    public void warpNameWhitelistAccepts() {
        assertTrue(ClaimWarpNames.isValid("Bob_01", 32));
        assertTrue(ClaimWarpNames.isValid("\u57ce\u5821", 32), "non-Latin letters are allowed");
        assertTrue(ClaimWarpNames.isValid(".Steve_02", 32));
    }

    @Test
    @DisplayName("Warp names reject injection characters, spaces, empties and over-length names")
    public void warpNameWhitelistRejects() {
        assertFalse(ClaimWarpNames.isValid("a)b", 32), "brackets close MineDown links");
        assertFalse(ClaimWarpNames.isValid("a&b", 32), "ampersand is a color code");
        assertFalse(ClaimWarpNames.isValid("%1%", 32), "placeholders could be injected");
        assertFalse(ClaimWarpNames.isValid("a b", 32));
        assertFalse(ClaimWarpNames.isValid("", 32));
        assertFalse(ClaimWarpNames.isValid("abcdefghijklm", 12), "length cap enforced");
    }

    @Test
    @DisplayName("A warp name derived from a claim name is trimmed, has spaces replaced, drops disallowed characters and is cut to length")
    public void warpNameFromClaimName() {
        assertEquals("My_Base", ClaimWarpNames.fromClaimName("  My  Base! ", 32));
        assertEquals("My_Ba", ClaimWarpNames.fromClaimName("My Base!", 5));
        assertEquals("", ClaimWarpNames.fromClaimName("!!", 32), "may be empty: caller re-prompts");
    }

    @Test
    @DisplayName("Claim and warp names are in sync by one shared rule for menu and command")
    public void warpNameInSyncRule() {
        assertTrue(ClaimWarpNames.isInSync("My Base", "My_Base", 32),
                "space-underscore pair must be in sync (was NOT for /claimwarp rename)");
        assertTrue(ClaimWarpNames.isInSync("My_Base", "my_base", 32), "case-insensitive");
        assertFalse(ClaimWarpNames.isInSync("My Base", "Other", 32));
        assertFalse(ClaimWarpNames.isInSync("My Base", "My B4se", 32));
    }

    private static final User OWNER = User.of(UUID.fromString("00000000-0000-0000-0000-000000000001"), "Bob");
    private static final World WORLD = World.of("world", UUID.randomUUID(), "normal");

    // Same protected-constructor pattern as ClaimWarpTests (same package)
    private static Claim newClaim() {
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
    @DisplayName("metadata keys: setChosenName stores and clears auto; isAuto reads marker")
    public void chosenNameClearsAutoMarker() {
        final Claim claim = newClaim();
        claim.getMetadata().put(ClaimNaming.NAME_KEY, "Bob_01");
        claim.getMetadata().put(ClaimNaming.AUTO_KEY, "true");
        assertTrue(ClaimNaming.isAuto(claim));
        ClaimNaming.setChosenName(claim, "Castle");
        assertEquals("Castle", claim.getMetadata().get(ClaimNaming.NAME_KEY));
        assertFalse(ClaimNaming.isAuto(claim), "a deliberate name must clear the auto marker");
    }

    @Test
    @DisplayName("namesOf: stored name plus warp name")
    public void namesOfCombinesNameAndWarp() {
        final Claim claim = newClaim();
        assertEquals(0, ClaimNaming.namesOf(claim).size());
        claim.getMetadata().put(ClaimNaming.NAME_KEY, "Castle");
        assertEquals(List.of("Castle"), ClaimNaming.namesOf(claim));
        claim.setWarp(ClaimWarp.of("Keep", OWNER, Position.at(5, 64, 5, WORLD)));
        assertEquals(List.of("Castle", "Keep"), ClaimNaming.namesOf(claim));
    }
}
