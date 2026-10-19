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

import de.exlll.configlib.NameFormatters;
import de.exlll.configlib.YamlConfigurationProperties;
import de.exlll.configlib.YamlConfigurationStore;
import de.exlll.configlib.YamlConfigurations;
import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.trust.TrustLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.Map;

/**
 * Interface for getting and setting data from plugin configuration files
 *
 * @since 1.0
 */
public interface ConfigProvider {

    @NotNull
    YamlConfigurationProperties.Builder<?> YAML_CONFIGURATION_PROPERTIES = YamlConfigurationProperties.newBuilder()
            .charset(StandardCharsets.UTF_8)
            .setNameFormatter(NameFormatters.LOWER_UNDERSCORE);

    /**
     * Get the plugin settings, read from the config file
     *
     * @return the plugin settings
     * @since 1.0
     */
    @NotNull
    Settings getSettings();

    /**
     * Set the plugin settings
     *
     * @param settings The settings to set
     * @since 1.0
     */
    void setSettings(@NotNull Settings settings);

    /**
     * Load the plugin settings from the config file
     *
     * @since 1.0
     */
    default void loadSettings() {
        final Path path = getConfigDirectory().resolve("config.yml");
        final YamlConfigurationProperties props =
                YAML_CONFIGURATION_PROPERTIES.header(Settings.CONFIG_HEADER).build();
        final Settings settings = YamlConfigurations.update(path, Settings.class, props);
        final int previousLayout = settings.getGui().getLayoutVersion();
        if (settings.getGui().migrateLayout()) {
            // The new layout replaces any menu slots and icons the owner changed, so keep their file first
            final Path backup = path.resolveSibling("config.yml.gui-layout-v" + previousLayout + ".bak");
            try {
                Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                getPlugin().log(Level.WARNING, "Could not back up config.yml before updating the claim GUI layout", e);
            }
            YamlConfigurations.save(path, Settings.class, settings, props);
            getPlugin().log(Level.INFO, "Reset the claim GUI layout settings in config.yml to layout v"
                    + Settings.GuiSettings.CURRENT_LAYOUT_VERSION + " (previous file saved as " + backup.getFileName() + ")");
        }
        setSettings(settings);
    }

    /**
     * Get the locales for the plugin
     *
     * @return the locales for the plugin
     * @since 1.0
     */
    @NotNull
    Locales getLocales();

    /**
     * Set the locales for the plugin
     *
     * @param locales The locales to set
     * @since 1.0
     */
    void setLocales(@NotNull Locales locales);

    /**
     * Load the locales from the config file
     *
     * @since 1.0
     */
    default void loadLocales() {
        final YamlConfigurationStore<Locales> store = new YamlConfigurationStore<>(
                Locales.class, YAML_CONFIGURATION_PROPERTIES.header(Locales.CONFIG_HEADER).build()
        );
        // Read existing locales if present
        final String lang = getSettings().getLanguage();
        final Path path = getConfigDirectory().resolve(String.format("messages-%s.yml", lang));
        final Locales locales;
        if (Files.exists(path)) {
            locales = store.load(path);
        } else {
            // Otherwise, save and read the default locales
            locales = loadDefaultLocales(store, path);
        }
        if (locales == null) {
            return;
        }
        // Merge bundled keys for EVERY language (the old code early-returned
        // for en-gb, so upgraded servers never saw new gui_* keys). When the bundled
        // gui_locale_version is newer, all gui_* values are refreshed (with a .bak).
        final Locales bundledLang = readBundledLocales(store, lang);
        final Locales bundledDefault = readBundledLocales(store, Locales.DEFAULT_LOCALE);
        // Read the version before the merge replaces it
        final String previousGuiVersion = locales.locales.getOrDefault(LocaleMerge.GUI_VERSION_KEY, "0").trim();
        final LocaleMerge.Result r = LocaleMerge.merge(
                locales.locales,
                bundledLang != null ? bundledLang.locales : Map.of(),
                bundledDefault != null ? bundledDefault.locales : Map.of());
        if (r.changed()) {
            try {
                if (r.guiRefreshed() > 0 && Files.exists(path)) {
                    // One backup per GUI text version, so a later refresh can't overwrite the owner's original wording
                    Files.copy(path, path.resolveSibling(path.getFileName() + ".gui-v" + previousGuiVersion + ".bak"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                locales.locales.clear();
                locales.locales.putAll(r.merged());
                store.save(locales, path);
                getPlugin().log(Level.INFO, String.format(
                        "Updated %s: added %d missing message(s), refreshed %d GUI message(s)",
                        path.getFileName(), r.added(), r.guiRefreshed()));
            } catch (Throwable e) {
                getPlugin().log(Level.WARNING, "Could not update " + path.getFileName(), e);
                locales.locales.clear();
                locales.locales.putAll(r.merged());     // still use the merged set in memory
            }
        }
        setLocales(locales);
    }

    /**
     * Loads the default (bundled) locales for the configured language, saving them to disk
     *
     * @param store the YAML store to read with
     * @param path  the path to save the read locales to
     * @return the loaded locales, or {@code null} if the bundled resource could not be read
     * @since HuskClaimsPlus 1.0.0
     */
    @Nullable
    default Locales loadDefaultLocales(@NotNull YamlConfigurationStore<Locales> store, @NotNull Path path) {
        try (InputStream input = getResource(String.format("locales/%s.yml", getSettings().getLanguage()))) {
            final Locales loaded = store.read(input);
            store.save(loaded, path);
            return loaded;
        } catch (Throwable e) {
            getPlugin().log(Level.SEVERE, "An error occurred loading the locales (invalid lang code?)", e);
            return null;
        }
    }

    /**
     * Read a bundled locale file without touching disk.
     *
     * @param store the YAML store to read with
     * @param lang  the language code (e.g. {@code en-gb})
     * @return the bundled locales, or {@code null} if the resource is missing
     * @since HuskClaimsPlus 1.0.0
     */
    @Nullable
    default Locales readBundledLocales(@NotNull YamlConfigurationStore<Locales> store, @NotNull String lang) {
        try (InputStream input = getResource(String.format("locales/%s.yml", lang))) {
            return input == null ? null : store.read(input);
        } catch (Throwable e) {
            getPlugin().log(Level.WARNING, "Could not read bundled locale '" + lang + "'", e);
            return null;
        }
    }

    @NotNull
    String getServerName();

    void setServerName(@NotNull Server server);

    default void loadServer() {
        if (getSettings().getCrossServer().isEnabled()) {
            setServerName(YamlConfigurations.update(
                    getConfigDirectory().resolve("server.yml"),
                    Server.class,
                    YAML_CONFIGURATION_PROPERTIES.header(Server.CONFIG_HEADER).build()
            ));
        }
    }

    /**
     * Get a list of all trust levels
     *
     * @return a list of all trust levels
     * @since 1.0
     */
    @NotNull
    List<TrustLevel> getTrustLevels();

    /**
     * Get the trust level with the highest weight
     *
     * @return the highest trust level
     * @since 1.0
     */
    @NotNull
    default TrustLevel getHighestTrustLevel() {
        return getTrustLevels().stream().max(TrustLevel::compareTo)
                .orElseThrow(() -> new IllegalStateException("No trust levels found"));
    }

    /**
     * Get the trust level with the lowest weight
     *
     * @return the lowest trust level
     * @since 1.0
     */
    @NotNull
    default TrustLevel getLowestTrustLevel() {
        return getTrustLevels().stream().min(TrustLevel::compareTo)
                .orElseThrow(() -> new IllegalStateException("No trust levels found"));
    }

    /**
     * Get the lowest trust level that grants build trust
     *
     * @return the lowest trust level that grants build trust
     * @since 1.0
     */
    @NotNull
    default Optional<TrustLevel> getBuildTrustLevel() {
        return getTrustLevels().stream()
                .filter(level -> level.getFlags().contains(OperationType.BLOCK_PLACE))
                .min(TrustLevel::compareTo);
    }

    /**
     * Get a trust level by ID
     *
     * @param id The ID of the trust level
     * @return the trust level, if found
     * @since 1.0
     */
    default Optional<TrustLevel> getTrustLevel(@NotNull String id) {
        return getTrustLevels().stream().filter(trustLevel -> trustLevel.getId().equalsIgnoreCase(id)).findFirst();
    }

    /**
     * Set the trust levels
     *
     * @param trustLevels The trust levels to set
     * @since 1.0
     */
    void setTrustLevels(@NotNull TrustLevels trustLevels);

    /**
     * Load the trust levels from the config file
     *
     * @since 1.0
     */
    default void loadTrustLevels() {
        setTrustLevels(YamlConfigurations.update(
                getConfigDirectory().resolve("trust_levels.yml"),
                TrustLevels.class,
                YAML_CONFIGURATION_PROPERTIES.header(TrustLevels.CONFIG_HEADER).build()
        ).sortByWeight());
    }

    @NotNull
    Set<OperationType> allowedOwnerOperations();

    /**
     * Get a plugin resource
     *
     * @param name The name of the resource
     * @return the resource, if found
     * @since 1.0
     */
    InputStream getResource(@NotNull String name);

    /**
     * Get the plugin config directory
     *
     * @return the plugin config directory
     * @since 1.0
     */
    @NotNull
    Path getConfigDirectory();

    /**
     * Directory that holds the local database file.
     * <p>
     * Defaults to the configuration directory. Platforms that run several worlds in one
     * process override it so that each world keeps its own claims.
     *
     * @return the data directory
     * @since HuskClaimsPlus 1.0.0
     */
    @NotNull
    default Path getDataDirectory() {
        return getConfigDirectory();
    }

    @NotNull
    HuskClaims getPlugin();

}
