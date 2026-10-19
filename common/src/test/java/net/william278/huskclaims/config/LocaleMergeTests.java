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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;

@DisplayName("Locale merge on upgrade: stale message files")
public class LocaleMergeTests {

    private static Map<String, String> mapOf(String... kv) {
        final Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("Stale file missing a gui key gains it from the bundle")
    public void testMissingGuiKeyAdded() {
        final Map<String, String> file = mapOf("gui_back", "old", "claim_created", "You claimed it");
        final Map<String, String> bundle = mapOf("gui_back", "old", "gui_stepper_min", "Smallest",
                "claim_created", "You claimed it");
        final LocaleMerge.Result r = LocaleMerge.merge(file, bundle, Map.of());
        assertTrue(r.changed());
        assertEquals(1, r.added());
        assertEquals("Smallest", r.merged().get("gui_stepper_min"));
        assertTrue(r.merged().containsKey("claim_created"));
    }

    @Test
    @DisplayName("en-gb (default language) IS merged — the K1 regression")
    public void testDefaultLanguageMerged() {
        // file == bundledLang (en-gb) — old code early-returned and never merged
        final Map<String, String> file = mapOf("gui_back", "Back");
        final Map<String, String> enGb = mapOf("gui_back", "Back", "gui_trust_title", "Friends & Trust",
                LocaleMerge.GUI_VERSION_KEY, "2");
        final LocaleMerge.Result r = LocaleMerge.merge(file, enGb, enGb);
        assertTrue(r.changed());
        assertTrue(r.merged().containsKey("gui_trust_title"));
    }

    @Test
    @DisplayName("Older file version: every gui_* value replaced, non-GUI custom value preserved")
    public void testVersionBumpRefreshesGuiKeys() {
        final Map<String, String> file = mapOf(
                LocaleMerge.GUI_VERSION_KEY, "1",
                "gui_create_title", "stale custom",
                "claim_created", "custom");
        final Map<String, String> bundle = mapOf(
                LocaleMerge.GUI_VERSION_KEY, "2",
                "gui_create_title", "fresh");
        final LocaleMerge.Result r = LocaleMerge.merge(file, bundle, Map.of());
        assertTrue(r.changed());
        assertEquals("fresh", r.merged().get("gui_create_title"), "stale gui_* must be replaced");
        assertEquals("custom", r.merged().get("claim_created"), "non-GUI custom value must be preserved");
        assertEquals("2", r.merged().get(LocaleMerge.GUI_VERSION_KEY));
    }

    @Test
    @DisplayName("Equal versions: customised gui_* value preserved")
    public void testEqualVersionPreservesCustomGui() {
        final Map<String, String> file = mapOf(
                LocaleMerge.GUI_VERSION_KEY, "2",
                "gui_create_title", "my custom");
        final Map<String, String> bundle = mapOf(
                LocaleMerge.GUI_VERSION_KEY, "2",
                "gui_create_title", "default");
        final LocaleMerge.Result r = LocaleMerge.merge(file, bundle, Map.of());
        assertFalse(r.changed(), "nothing to add or refresh");
        assertEquals("my custom", r.merged().get("gui_create_title"));
    }

    @Test
    @DisplayName("Language values win over en-gb within the bundle")
    public void testLangWinsOverDefault() {
        final Map<String, String> file = mapOf("gui_back", "old");
        final Map<String, String> lang = mapOf("gui_back", "Retour");
        final Map<String, String> enGb = mapOf("gui_back", "Back");
        final LocaleMerge.Result r = LocaleMerge.merge(file, lang, enGb);
        assertEquals("old", r.merged().get("gui_back"), "file wins absent a version bump");
    }

    @Test
    @DisplayName("Absent version parses as 0 (old file refreshes GUI text)")
    public void testAbsentVersionIsZero() {
        final Map<String, String> file = mapOf("gui_stepper_min", "stale");
        final Map<String, String> bundle = mapOf(LocaleMerge.GUI_VERSION_KEY, "2",
                "gui_stepper_min", "fresh");
        final LocaleMerge.Result r = LocaleMerge.merge(file, bundle, Map.of());
        assertTrue(r.guiRefreshed() > 0, "absent version = 0 < 2, gui text must refresh");
        assertEquals("fresh", r.merged().get("gui_stepper_min"));
    }

    @Test
    @DisplayName("Stale file with missing keys: missing keys count as added, not refreshed")
    public void testMissingKeysCountAsAddedNotRefreshed() {
        // The upgrade fixture state: version key absent, some gui_* keys missing,
        // some gui_* values stale. Log line must show BOTH added N>0 and refreshed M>0.
        final Map<String, String> file = mapOf("gui_back", "Back", "gui_stepper_min", "Old text");
        final Map<String, String> bundle = mapOf(LocaleMerge.GUI_VERSION_KEY, "2",
                "gui_back", "&f← Back", "gui_stepper_min", "Minimum size",
                "gui_trust_row_public", "&aEveryone");
        final LocaleMerge.Result r = LocaleMerge.merge(file, bundle, Map.of());
        assertTrue(r.changed());
        assertTrue(r.added() > 0, "missing gui_trust_row_public must be counted as added");
        assertTrue(r.guiRefreshed() > 0, "stale gui_back value must be counted as refreshed");
        // added = gui_locale_version + gui_trust_row_public (both missing from the file)
        assertEquals(2, r.added());
        assertEquals(2, r.guiRefreshed(), "only pre-existing stale values count as refreshed");
        assertEquals("&f← Back", r.merged().get("gui_back"));
        assertEquals("&aEveryone", r.merged().get("gui_trust_row_public"));
    }
}
