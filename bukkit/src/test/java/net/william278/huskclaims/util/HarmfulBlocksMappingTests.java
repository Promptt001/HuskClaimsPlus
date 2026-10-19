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

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts the Bukkit {@code HARMFUL_BLOCKS} {@code Material} set stays one-to-one
 * with the platform-neutral {@code HARMFUL_BLOCK_KEYS} list in common's
 * {@link SafeTeleportProvider}. Test-only: this cannot move Paper behaviour,
 * it only guards the two lists against drifting apart.
 */
@DisplayName("Harmful Block Lists")
public class HarmfulBlocksMappingTests {

    @Test
    @DisplayName("Every Bukkit harmful Material maps onto a key in the common harmful key set")
    public void testBukkitMaterialsMapToCommonKeys() {
        final Set<String> bukkitKeys = BukkitSafeTeleportProvider.HARMFUL_BLOCKS.stream()
                .map(Material::name)
                .map(name -> name.toLowerCase(Locale.ENGLISH))
                .map(name -> name.startsWith("minecraft:") ? name : "minecraft:" + name)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(BukkitSafeTeleportProvider.HARMFUL_BLOCKS.size(), bukkitKeys.size(),
                "Bukkit Materials should map to distinct keys");
        assertEquals(SafeTeleportProvider.HARMFUL_BLOCK_KEYS, bukkitKeys,
                "Bukkit HARMFUL_BLOCKS and common HARMFUL_BLOCK_KEYS must be one-to-one");
    }

    @Test
    @DisplayName("The common harmful key set contains the expected 15 blocks")
    public void testCommonKeySetPinned() {
        assertEquals(15, SafeTeleportProvider.HARMFUL_BLOCK_KEYS.size());
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:lava"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:fire"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:soul_fire"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:campfire"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:soul_campfire"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:magma_block"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:cactus"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:sweet_berry_bush"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:wither_rose"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:powder_snow"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:pointed_dripstone"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:cobweb"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:nether_portal"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:end_portal"));
        assertTrue(SafeTeleportProvider.HARMFUL_BLOCK_KEYS.contains("minecraft:end_gateway"));
    }

}
