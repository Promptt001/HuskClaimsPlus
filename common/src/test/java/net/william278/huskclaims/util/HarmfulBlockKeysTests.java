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

package net.william278.huskclaims.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the platform-neutral harmful-block list shared by every platform's
 * warp anti-trap check. Bukkit's equivalent {@code Material} set is asserted
 * one-to-one against this list by {@code HarmfulBlocksMappingTests} in the
 * bukkit module.
 */
@DisplayName("Harmful Block Keys")
public class HarmfulBlockKeysTests {

    @Test
    @DisplayName("The harmful block key set contains exactly the pinned 15 namespaced ids")
    public void testHarmfulBlockKeysPinned() {
        assertEquals(Set.of(
                "minecraft:lava", "minecraft:fire", "minecraft:soul_fire", "minecraft:campfire",
                "minecraft:soul_campfire", "minecraft:magma_block", "minecraft:cactus",
                "minecraft:sweet_berry_bush", "minecraft:wither_rose", "minecraft:powder_snow",
                "minecraft:pointed_dripstone", "minecraft:cobweb", "minecraft:nether_portal",
                "minecraft:end_portal", "minecraft:end_gateway"
        ), SafeTeleportProvider.HARMFUL_BLOCK_KEYS);
    }

    @Test
    @DisplayName("Every key is a valid namespaced id (namespace:path, lowercase)")
    public void testKeysAreNamespaced() {
        for (String key : SafeTeleportProvider.HARMFUL_BLOCK_KEYS) {
            assertTrue(key.startsWith("minecraft:"), key + " must be namespaced");
            assertTrue(key.chars().allMatch(c -> Character.isLowerCase(c) || c == ':' || c == '_' || c == '-' || Character.isDigit(c)),
                    key + " must be lowercase");
        }
    }

}
