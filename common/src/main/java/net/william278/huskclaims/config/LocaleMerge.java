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

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.TreeMap;

/**
 * Pure locale-merge logic. No I/O, no plugin — fully
 * unit-testable.
 *
 * <p>Fixes the upgrade-path blind spot: when {@code messages-<lang>.yml}
 * exists it was loaded as-is and, for the default language (en-gb),
 * <b>nothing was ever merged</b>, so {@code gui_*} keys added after the
 * file was first written never reached the server. The merge now runs for
 * every language, and when the bundled {@code gui_locale_version} is newer
 * than the file's, all bundled {@code gui_*} values replace stale ones
 * (the caller keeps a {@code .bak} backup).</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class LocaleMerge {

    /** Key holding the GUI text version (bumped whenever GUI text changes meaningfully). */
    public static final String GUI_VERSION_KEY = "gui_locale_version";
    /** Prefix of keys owned by the GUI (refreshed wholesale on version bump). */
    public static final String GUI_PREFIX = "gui_";

    /**
     * Merge result.
     *
     * @param merged       the merged key map (a new, sorted map; inputs untouched)
     * @param added        count of missing keys filled from the bundle
     * @param guiRefreshed count of stale {@code gui_*} values replaced
     * @since HuskClaimsPlus 1.0.0
     */
    public record Result(@NotNull Map<String, String> merged, int added, int guiRefreshed) {

        /** @return if the merge changed anything (the caller must save the file) */
        public boolean changed() {
            return added > 0 || guiRefreshed > 0;
        }
    }

    private LocaleMerge() {
    }

    /**
     * Merge bundled locale keys into the file's keys.
     *
     * <p>Order of operations: (1) if the bundled GUI version is newer than the
     * file's, bundled {@code gui_*} values <b>replace</b> stale ones (GUI text
     * is still evolving; customised non-GUI text is never touched); (2) any
     * keys still missing are filled from the bundle. Language values win over
     * en-gb within the bundle.</p>
     *
     * @param file           keys from {@code messages-<lang>.yml} on disk
     * @param bundledLang    bundled {@code locales/<lang>.yml} (may equal bundledDefault)
     * @param bundledDefault bundled {@code locales/en-gb.yml}
     * @return the merge result
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    public static Result merge(@NotNull Map<String, String> file,
                               @NotNull Map<String, String> bundledLang,
                               @NotNull Map<String, String> bundledDefault) {
        final Map<String, String> out = new TreeMap<>(file);
        final Map<String, String> bundled = new TreeMap<>(bundledDefault);
        bundled.putAll(bundledLang);                      // language values win over en-gb

        // Fill missing keys FIRST so additions aren't miscounted as refreshes
        int added = 0;
        for (Map.Entry<String, String> e : bundled.entrySet()) {
            if (out.putIfAbsent(e.getKey(), e.getValue()) == null) {
                added++;
            }
        }
        int refreshed = 0;
        if (version(file) < version(bundled)) {
            // GUI text is still evolving: newer bundled GUI text REPLACES stale GUI text
            // (only keys that were already in the file count as refreshed; the version
            // key itself is bookkeeping, not GUI text, and is never counted)
            for (Map.Entry<String, String> e : bundled.entrySet()) {
                final boolean inFile = file.containsKey(e.getKey());
                if (e.getKey().startsWith(GUI_PREFIX) && inFile
                        && !e.getValue().equals(out.get(e.getKey()))) {
                    out.put(e.getKey(), e.getValue());
                    refreshed++;
                }
            }
        }
        return new Result(out, added, refreshed);
    }

    private static int version(@NotNull Map<String, String> m) {
        try {
            return Integer.parseInt(m.getOrDefault(GUI_VERSION_KEY, "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
