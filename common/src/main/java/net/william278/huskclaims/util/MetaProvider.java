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

import net.william278.desertwell.util.UpdateChecker;
import net.william278.desertwell.util.Version;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.ClaimWorld;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Interface for providing plugin metadata and update checking
 *
 * @since 1.0
 */
public interface MetaProvider {

    /** GitHub "owner/repo" whose latest release tag is compared with this build. Blank turns update checks off. */
    String UPDATE_REPOSITORY = "Promptt001/HuskClaimsPlus";

    /** URL shown when an update is available. */
    String RELEASES_URL = "https://github.com/" + UPDATE_REPOSITORY + "/releases";

    /** bStats id registered for HuskClaimsPlus. Zero turns metrics off (none is registered). */
    int BSTATS_PLUGIN_ID = 0;

    /**
     * Get a list of all {@link ClaimWorld}s
     *
     * @return A list of all {@link ClaimWorld}s
     * @since 1.0
     */
    @NotNull
    Version getPluginVersion();

    /**
     * Get the plugin {@link UpdateChecker}
     *
     * @return the plugin {@link UpdateChecker}
     * @since 1.0
     */
    @NotNull
    default UpdateChecker getUpdateChecker() {
        return UpdateChecker.builder()
                .currentVersion(getPluginVersion())
                .endpoint(UpdateChecker.Endpoint.GITHUB)
                .resource(UPDATE_REPOSITORY)
                .build();
    }

    /**
     * Whether this build compares its version against a release source of its own
     * (blank repository constant means update checks are turned off for this build)
     *
     * @return {@code true} if update checks are enabled for this build
     * @since HuskClaimsPlus 1.0.0
     */
    default boolean hasUpdateSource() {
        return !UPDATE_REPOSITORY.isBlank();
    }

    /**
     * Check for updates and log a warning if an update is available
     *
     * @since 1.0
     */
    default void checkForUpdates() {
        if (!hasUpdateSource() || !getPlugin().getSettings().isCheckForUpdates()) {
            return;
        }
        getUpdateChecker().check().thenAccept(checked -> {
            if (checked.isUpToDate()) {
                return;
            }
            getPlugin().log(Level.WARNING, String.format(
                    "A new version of HuskClaimsPlus is available: v%s (running v%s). Download: %s",
                    checked.getLatestVersion(), getPluginVersion(), RELEASES_URL)
            );
        });
    }

    /**
     * Get the server type
     *
     * @return the server type
     * @since 1.0
     */
    @NotNull
    String getServerType();

    /**
     * Get the Minecraft version
     *
     * @return the Minecraft version
     * @since 1.0
     */
    @NotNull
    Version getMinecraftVersion();

    @NotNull
    HuskClaims getPlugin();

}
