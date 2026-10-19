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

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validator semantics for menu value prompts — these decide which chat and
 * anvil inputs reach the
 * confirm step, so their exact behavior is pinned.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public class GuiValidatorsTests {

    @Test
    void testFreeTextAcceptsNonBlankOnly() {
        assertTrue(GuiValidators.freeText().apply("hello").isPresent());
        assertEquals("hello", GuiValidators.freeText().apply("hello").orElseThrow());
        assertTrue(GuiValidators.freeText().apply("   ").isEmpty());
        assertTrue(GuiValidators.freeText().apply("").isEmpty());
    }

    @Test
    void testPlayerNameRules() {
        // 3–16 chars, [A-Za-z0-9_]
        assertTrue(GuiValidators.playerName().apply("William278").isPresent());
        assertTrue(GuiValidators.playerName().apply("ab").isEmpty());          // too short
        assertTrue(GuiValidators.playerName().apply("a".repeat(17)).isEmpty()); // too long
        assertTrue(GuiValidators.playerName().apply("bad name").isEmpty());     // space
        assertTrue(GuiValidators.playerName().apply("bad!name").isEmpty());     // punctuation
    }

    @Test
    void testPlayerNamePrefixRules() {
        // Without allowPrefix, prefixes are rejected
        assertTrue(GuiValidators.playerName(false).apply("group:bob").isEmpty());
        // With allowPrefix, one : or . separated group is accepted
        assertTrue(GuiValidators.playerName(true).apply("group:bob").isPresent());
        assertTrue(GuiValidators.playerName(true).apply("group.bob").isPresent());
        // But never two separators / garbage
        assertTrue(GuiValidators.playerName(true).apply("a:b:c").isEmpty());
        assertTrue(GuiValidators.playerName(true).apply("!:bob").isEmpty());
    }

    @Test
    void testPositiveAmountRules() {
        assertTrue(GuiValidators.positiveAmount().apply("10").isPresent());
        assertTrue(GuiValidators.positiveAmount().apply("999999999").isPresent()); // 9 digits max
        assertTrue(GuiValidators.positiveAmount().apply("0").isEmpty());          // must be > 0
        assertTrue(GuiValidators.positiveAmount().apply("-5").isEmpty());         // no sign
        assertTrue(GuiValidators.positiveAmount().apply("1.5").isEmpty());        // whole only
        assertTrue(GuiValidators.positiveAmount().apply("1234567890").isEmpty()); // 10 digits
        assertTrue(GuiValidators.positiveAmount().apply("1_000").isEmpty());      // no separators
    }

    @Test
    void testOptionalChainingShape() {
        // The validators return Optional<String> of the SAME value on success (callers use it as the arg)
        final Optional<String> ok = GuiValidators.playerName().apply("Steve_2");
        assertEquals("Steve_2", ok.orElseThrow());
    }

}
