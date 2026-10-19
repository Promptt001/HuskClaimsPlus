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
import net.william278.huskclaims.trust.TrustLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Trust Icons Tests")
public class TrustIconsTests {

    private static TrustLevel level(@NotNull String id, int weight,
                                    @NotNull List<OperationType> flags,
                                    @NotNull List<TrustLevel.Privilege> privileges) {
        return TrustLevel.builder()
                .id(id)
                .displayName(id.substring(0, 1).toUpperCase() + id.substring(1))
                .weight(weight)
                .flags(flags)
                .privileges(privileges)
                .build();
    }

    @Test
    @DisplayName("The four default levels get four distinct, correct category icons")
    public void testDefaultLevelIcons() {
        final List<TrustLevel> defaults = List.of(
                level("access", 100, List.of(OperationType.BLOCK_INTERACT, OperationType.REDSTONE_INTERACT), List.of()),
                level("container", 200, List.of(OperationType.CONTAINER_OPEN), List.of()),
                level("build", 300, List.of(OperationType.BLOCK_PLACE, OperationType.BLOCK_BREAK), List.of()),
                level("manage", 400, List.of(), List.of(TrustLevel.Privilege.MANAGE_TRUSTEES))
        );
        assertEquals("OAK_DOOR", TrustIcons.fallbackIcon(defaults.get(0)));
        assertEquals("CHEST", TrustIcons.fallbackIcon(defaults.get(1)));
        assertEquals("BRICKS", TrustIcons.fallbackIcon(defaults.get(2)));
        assertEquals("GOLDEN_HELMET", TrustIcons.fallbackIcon(defaults.get(3)));
        assertEquals(4, defaults.stream().map(TrustIcons::fallbackIcon).distinct().count(),
                "all four fallbacks must be distinct (no diamond collision)");
    }

    @Test
    @DisplayName("Id match wins over capabilities: a 'build' id with container flags is still bricks")
    public void testIdTakesPrecedenceOverCapabilities() {
        final TrustLevel build = level("build", 300, List.of(OperationType.CONTAINER_OPEN), List.of());
        assertEquals("BRICKS", TrustIcons.fallbackIcon(build));
    }

    @Test
    @DisplayName("Custom privilege-bearing level → GOLDEN_HELMET")
    public void testCustomPrivilegeLevel() {
        final TrustLevel admin = level("admin", 500, List.of(), List.of(TrustLevel.Privilege.MAKE_PRIVATE));
        assertEquals("GOLDEN_HELMET", TrustIcons.fallbackIcon(admin));
    }

    @Test
    @DisplayName("Custom levels infer from flags: BLOCK_PLACE → bricks, CONTAINER_OPEN → chest, REDSTONE → lever, empty → door")
    public void testCustomFlagInference() {
        assertEquals("BRICKS", TrustIcons.fallbackIcon(
                level("architect", 250, List.of(OperationType.BLOCK_PLACE), List.of())));
        assertEquals("CHEST", TrustIcons.fallbackIcon(
                level("shopper", 150, List.of(OperationType.CONTAINER_OPEN), List.of())));
        assertEquals("LEVER", TrustIcons.fallbackIcon(
                level("redstoner", 120, List.of(OperationType.REDSTONE_INTERACT), List.of())));
        assertEquals("OAK_DOOR", TrustIcons.fallbackIcon(
                level("visitor", 50, List.of(), List.of())));
    }

    @Test
    @DisplayName("Weight never changes the fallback: no off-by-one weight-band behavior (K6 regression)")
    public void testWeightDoesNotAffectFallback() {
        // Old K6 bug: weight/100 bands gave Access→CHEST, Container→IRON_PICKAXE, Build→DIAMOND, Manage→DIAMOND.
        // Same id at wildly different weights must map identically.
        assertEquals(TrustIcons.fallbackIcon(level("access", 100, List.of(), List.of())),
                TrustIcons.fallbackIcon(level("access", 9999, List.of(), List.of())));
        assertEquals(TrustIcons.fallbackIcon(level("manage", 400, List.of(), List.of())),
                TrustIcons.fallbackIcon(level("manage", 1, List.of(), List.of())));
    }

    @Test
    @DisplayName("Privileges outrank flags: a level with both gets GOLDEN_HELMET")
    public void testPrivilegesOutrankFlags() {
        final TrustLevel both = level("builder-manager", 350,
                List.of(OperationType.BLOCK_PLACE), List.of(TrustLevel.Privilege.MANAGE_CHILD_CLAIMS));
        assertEquals("GOLDEN_HELMET", TrustIcons.fallbackIcon(both));
    }

}
