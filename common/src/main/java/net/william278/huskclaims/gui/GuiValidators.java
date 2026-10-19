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

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Platform-free validators for values typed into menu prompts. They decide
 * which chat or anvil inputs are accepted, and normalise them before any
 * command runs. Kept free of platform classes so they are unit-testable.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class GuiValidators {

    private GuiValidators() {
    }

    /** Accepts any non-blank text (no normalization beyond trim). */
    @NotNull
    public static Function<String, Optional<String>> freeText() {
        return v -> v.isBlank() ? Optional.empty() : Optional.of(v);
    }

    /** Minecraft player names: 3–16 chars, [A-Za-z0-9_] (no tags/groups unless intended). */
    @NotNull
    public static Function<String, Optional<String>> playerName() {
        return v -> PLAYER_NAME.matcher(v).matches() ? Optional.of(v) : Optional.empty();
    }

    /** Minecraft player names, optionally with a prefix (allowNamePrefix). */
    @NotNull
    public static Function<String, Optional<String>> playerName(boolean allowPrefix) {
        if (!allowPrefix) {
            return playerName();
        }
        return v -> PLAYER_NAME_WITH_PREFIX.matcher(v).matches() ? Optional.of(v) : Optional.empty();
    }

    /** A whole, positive number of up to nine digits, such as a block amount. */
    @NotNull
    public static Function<String, Optional<String>> positiveAmount() {
        return v -> POSITIVE_AMOUNT.matcher(v).matches() && Long.parseLong(v) > 0
                ? Optional.of(v) : Optional.empty();
    }

    private static final Pattern PLAYER_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Pattern PLAYER_NAME_WITH_PREFIX = Pattern.compile("^[A-Za-z0-9_]{3,16}([:.][A-Za-z0-9_]{1,16})?$");
    private static final Pattern POSITIVE_AMOUNT = Pattern.compile("^\\d{1,9}$");

}
