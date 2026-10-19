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

import de.exlll.configlib.Comment;
import de.exlll.configlib.Configuration;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Fabric-specific settings, kept in a separate {@code fabric.yml} so the shared
 * {@code config.yml} (read by both Paper and Fabric) does not change.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@Configuration
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FabricSettings {

    public static final String CONFIG_HEADER = """
            ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            ┃ HuskClaimsPlus · Fabric
            ┃ Based on HuskClaims by William278 (Apache-2.0)
            ┣━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            ┣╸ HuskClaimsPlus: https://github.com/Promptt001/HuskClaimsPlus
            ┣╸ Settings for Fabric dedicated servers, singleplayer and Open to LAN
            ┣╸ Config Help: https://william278.net/docs/huskclaims/
            ┗╸ Documentation: https://william278.net/docs/huskclaims/
            """;

    @Comment({"Singleplayer worlds opened to LAN.",
            "When false, LAN guests never receive HuskClaims operator permissions, even if the",
            "world was opened with commands allowed. The world owner always has them.",
            "When true, guests with an operator level are treated as operators, as in vanilla."})
    private boolean lanGuestsFollowOperatorLevel = false;

}
