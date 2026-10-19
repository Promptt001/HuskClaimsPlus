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

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the fork's update/metrics identity: no upstream Spigot resource, no
 * upstream bStats id, and update checks point at the fork's GitHub releases
 * (or are off entirely when the repository constant is blank).
 */
class MetaProviderIdentityTests {

    @Test
    void upstreamIdentifiersAreGone() {
        assertEquals(0, MetaProvider.BSTATS_PLUGIN_ID, "bStats id must be 0 (off) — upstream's must never ship");
        assertFalse(MetaProvider.UPDATE_REPOSITORY.toLowerCase(Locale.ROOT).contains("william278"),
                "update repository must not point at upstream");
    }

    @Test
    void updateRepositoryIsWellFormed() {
        // Endpoint.GITHUB expects "owner/repo" (releases/latest endpoint)
        assertTrue(MetaProvider.UPDATE_REPOSITORY.matches("[\\w.-]+/[\\w.-]+"),
                "UPDATE_REPOSITORY must look like owner/repo: " + MetaProvider.UPDATE_REPOSITORY);
        assertTrue(MetaProvider.RELEASES_URL.startsWith("https://github.com/"),
                "RELEASES_URL must be a GitHub releases URL");
    }

}
