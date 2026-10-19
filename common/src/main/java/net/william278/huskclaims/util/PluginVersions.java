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
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns platform version strings into DesertWell versions without ever throwing.
 * DesertWell parses every dot-separated part with {@code Integer.parseInt}, so
 * anything that is not plain numbers must be stripped or moved into metadata
 * before the string reaches it.
 */
public final class PluginVersions {

    private static final Pattern LEADING_NUMBERS = Pattern.compile("^(\\d+(?:\\.\\d+)*)");

    private PluginVersions() {
    }

    /**
     * Parse this plugin's own version. Build metadata after {@code +} (Fabric
     * appends {@code +mc.26.2}) is dropped; anything after the first {@code -}
     * becomes metadata. A leading {@code v} is accepted, so a tag such as
     * {@code v1.0.1} also parses.
     */
    @NotNull
    public static Version plugin(@NotNull String raw) {
        String text = raw.strip();
        final int plus = text.indexOf('+');
        if (plus >= 0) {
            text = text.substring(0, plus);
        }
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        final int dash = text.indexOf('-');
        final String core = dash >= 0 ? text.substring(0, dash) : text;
        if (!LEADING_NUMBERS.matcher(core).matches()) {
            // Never throw at startup; fall back to a metadata-only version
            return Version.fromString("0.0.0-" + text, "-");
        }
        return Version.fromString(text, "-");
    }

    /**
     * Parse a Minecraft or server version, keeping only the leading numbers:
     * {@code 26.2.build.133-stable} and {@code 26.2-R0.1-SNAPSHOT} both
     * become {@code 26.2}.
     */
    @NotNull
    public static Version minecraft(@NotNull String raw) {
        final Matcher matcher = LEADING_NUMBERS.matcher(raw.strip());
        return Version.fromString(matcher.find() ? matcher.group(1) : "0");
    }

}
