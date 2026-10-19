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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GuiText}. These tests live in the bukkit module's test
 * sourceset (the class is in bukkit), but run without a server: GuiText is
 * dependency-free.
 *
 * <p>Note: if this file fails to compile because the bukkit test sourceset
 * does not exist, move it to the common module next to a copy of GuiText —
 * but GuiText lives in the gui package of bukkit, so this is the right home.</p>
 */
@DisplayName("Menu text: colours, injection safety, italics")
public class GuiTextTests {

    @Test
    @DisplayName("&#rrggbb hex translates to section-hex codes")
    public void testHexColor() {
        assertEquals("§x§3§6§e§4§f§fA", GuiText.color("&#36e4ffA"));
    }

    @Test
    @DisplayName("Legacy & codes translate")
    public void testLegacyColor() {
        assertEquals("§aGreen", GuiText.color("&aGreen"));
    }

    @Test
    @DisplayName("Fill inserts values literally (no colour injection)")
    public void testFillIsLiteral() {
        final String colored = GuiText.color("&7Hi %1%");
        assertEquals("§7Hi &aBob", GuiText.fill(colored, "&aBob"));
    }

    @Test
    @DisplayName("Filling placeholders is a single pass: a value containing %2% stays literal")
    public void testFillSinglePass() {
        org.junit.jupiter.api.Assertions.assertEquals(
                "%2%-x", GuiText.fill("%1%-%2%", "%2%", "x"),
                "fill must be a single substitution pass");
        // missing arg leaves the placeholder visible (loud, never silent)
        org.junit.jupiter.api.Assertions.assertEquals(
                "a %3% b", GuiText.fill("a %3% b", "one"),
                "missing arg stays visible");
    }

    @Test
    @DisplayName("asLore adds gray when absent; blank stays blank")
    public void testAsLore() {
        assertEquals("§7x", GuiText.asLore("x"));
        assertEquals("", GuiText.asLore(""));
        assertEquals("§aAlready", GuiText.asLore("§aAlready"));
    }

    @Test
    @DisplayName("asName adds white when absent; empty stays empty")
    public void testAsName() {
        assertEquals("§fx", GuiText.asName("x"));
        assertEquals("", GuiText.asName(""));
    }

    @Test
    @DisplayName("hex() formats a level color; bad input becomes white")
    public void testHexHelper() {
        assertEquals("§x§3§6§e§4§f§f", GuiText.hex("#36e4ff"));
        assertEquals("§f", GuiText.hex("zzz"));
    }

    @Test
    @DisplayName("literal() neutralises section signs in player-supplied values")
    public void testLiteral() {
        assertEquals("?x?f?fA", GuiText.literal("§x§f§fA"));
        assertEquals("plain", GuiText.literal("plain"));
    }
}
