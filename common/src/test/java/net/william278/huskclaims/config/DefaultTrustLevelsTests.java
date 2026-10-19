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
package net.william278.huskclaims.config;

import net.william278.huskclaims.trust.TrustLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The README, CHANGELOG and docs tell server owners that the default Manage
 * trust level can manage claim warps. Nothing enforced that against the code,
 * so the shipped default did not include the privilege. These tests pin the
 * promise to the default config.
 */
@DisplayName("Default trust levels")
class DefaultTrustLevelsTests {

    private static Map<String, TrustLevel> defaults() throws Exception {
        // configlib instantiates the @Configuration class through its private no-args constructor
        final Constructor<TrustLevels> ctor = TrustLevels.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance().getTrustLevels().stream()
                .collect(Collectors.toMap(TrustLevel::getId, level -> level));
    }

    @Test
    @DisplayName("Manage trust may manage the claim warp (README, CHANGELOG and docs promise this)")
    void manageHasManageWarp() throws Exception {
        final TrustLevel manage = defaults().get("manage");
        assertTrue(manage != null, "default trust levels must include a manage level");
        assertTrue(manage.getPrivileges().contains(TrustLevel.Privilege.MANAGE_WARP),
                "default manage level must include MANAGE_WARP");
    }

    @Test
    @DisplayName("No other default level gets MANAGE_WARP")
    void lowerLevelsDoNot() throws Exception {
        defaults().forEach((id, level) -> {
            if (!id.equals("manage")) {
                assertFalse(level.getPrivileges().contains(TrustLevel.Privilege.MANAGE_WARP),
                        id + " must not include MANAGE_WARP");
            }
        });
    }

}
