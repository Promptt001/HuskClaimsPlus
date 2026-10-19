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

import net.william278.desertwell.util.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginVersionsTests {

    @ParameterizedTest
    @CsvSource({
            "1.0.0,                                    1, 0, 0",
            "1.0.0+mc.26.2,                            1, 0, 0",   // the release Fabric string
            "1.0.1-abc1234+mc.26.2,                    1, 0, 1",
            "1.0.0-abc1234-indev-testonly.rc2+mc.26.2,  1, 0, 0",
            "1.5.11-gui.1-de3b04d+mc.26.2,             1, 5, 11",
            "v1.2.3,                                   1, 2, 3"
    })
    void parsesPluginVersions(String raw, int major, int minor, int patch) {
        final Version v = PluginVersions.plugin(raw);
        assertEquals(major, v.getMajor());
        assertEquals(minor, v.getMinor());
        assertEquals(patch, v.getPatch());
    }

    @ParameterizedTest
    @ValueSource(strings = {"26.2.build.133-stable", "26.2-R0.1-SNAPSHOT", "26.2"})
    void parsesMinecraftVersions(String raw) {
        final Version v = PluginVersions.minecraft(raw);
        assertEquals(26, v.getMajor());
        assertEquals(2, v.getMinor());
    }

    @Test
    void neverThrows() {
        assertDoesNotThrow(() -> PluginVersions.plugin("garbage+x"));
        assertDoesNotThrow(() -> PluginVersions.minecraft("unknown"));
    }

}
