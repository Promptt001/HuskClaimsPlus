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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Item-text formatting for menu names and lore. Dependency-free:
 * no Adventure/bungee APIs. Locale <b>item text</b> uses {@code &}
 * codes plus {@code &#rrggbb} hex, never MineDown; chat messages keep using
 * MineDown via {@code getLocale}.
 *
 * <p>The colour codes are applied to the <b>template</b>, then replacement
 * values are inserted, so a claim name or player name can never inject
 * colours, on both the legacy and the hex paths.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class GuiText {

    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final Pattern LEGACY = Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    /** %1%, %2%, … placeholders — one pass, so a value containing %2% is never re-substituted. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%([0-9]+)%");
    private static final char S = '§';

    private GuiText() {
    }

    /**
     * Template colour codes → section codes. Apply to locale TEMPLATES only,
     * never to values.
     *
     * @param template the locale template
     * @return the coloured template
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static String color(@NotNull String template) {
        final Matcher hex = HEX.matcher(template);
        final StringBuilder sb = new StringBuilder();
        while (hex.find()) {
            final StringBuilder rep = new StringBuilder().append(S).append('x');
            for (char c : hex.group(1).toCharArray()) {
                rep.append(S).append(Character.toLowerCase(c));
            }
            hex.appendReplacement(sb, Matcher.quoteReplacement(rep.toString()));
        }
        hex.appendTail(sb);
        return LEGACY.matcher(sb.toString()).replaceAll(S + "$1");
    }

    /**
     * {@code #36e4ff} (e.g. {@code TrustLevel#getColor}) → section hex code;
     * white on bad input.
     *
     * @param color the hex color string (with or without leading {@code #})
     * @return the formatted color code
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static String hex(@NotNull String color) {
        final String h = color.startsWith("#") ? color.substring(1) : color;
        return h.matches("[0-9a-fA-F]{6}") ? color("&#" + h) : S + "f";
    }

    /**
     * Insert {@code %1%}, {@code %2%}… AFTER colouring, so values stay
     * literal.
     *
     * @param colored the coloured template
     * @param args    replacement values
     * @return the filled text
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static String fill(@NotNull String colored, @NotNull String... args) {
        // One regex pass: every placeholder is matched against the ORIGINAL
        // text once, so a replacement value containing e.g. %2% stays literal instead
        // of being substituted by a later arg. A %n% with no matching arg stays
        // visible.
        final Matcher m = PLACEHOLDER.matcher(colored);
        final StringBuilder sb = new StringBuilder();
        while (m.find()) {
            final int n;
            try {
                n = Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
                continue;
            }
            final String rep;
            if (n >= 1 && n <= args.length && args[n - 1] != null) {
                rep = args[n - 1];
            } else {
                rep = m.group(); // missing arg: leave %n% visible
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Leading colour → the client renders it NON-italic. Names default to
     * white.
     *
     * @param s the text
     * @return the name text with a leading colour code
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static String asName(@NotNull String s) {
        return s.isEmpty() || s.charAt(0) == S ? s : S + "f" + s;
    }

    /**
     * Lore lines default to gray, non-italic; blank lines stay blank.
     *
     * @param s the text
     * @return the lore text with a leading colour code
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static String asLore(@NotNull String s) {
        return s.isEmpty() || s.charAt(0) == S ? s : S + "7" + s;
    }

    /**
     * Makes a player-supplied value safe to show as item text: a section sign
     * would start a colour code the player never intended.
     *
     * @param value the value to neutralise
     * @return the value with every section sign replaced
     */
    @NotNull
    public static String literal(@NotNull String value) {
        return value.replace(S, '?');
    }
}
