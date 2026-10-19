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

import de.exlll.configlib.YamlConfigurations;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Menu layout migration on upgrade")
public class GuiSettingsMigrationTests {

    /**
     * Config from before layout versioning: lime filler, an icon map from before
     * the trust.level.* keys, and no layout_version key. Generated-file naming
     * style is lower_underscore
     * (NameFormatters.LOWER_UNDERSCORE, per ConfigProvider's YAML properties).
     */
    private static final String LAYOUT_V0_CONFIG = """
            enabled: true
            claim_tool_cooldown_seconds: 60
            input_timeout_seconds: 60
            filler_material: 'minecraft:lime_stained_glass_pane'
            icon_materials:
              main.my_claims: 'minecraft:grass_block'
              main.trust: 'minecraft:diamond'
              main.blocks: 'minecraft:gold_ingot'
              main.claim_tool: 'minecraft:golden_shovel'
              my_claims.claim_list: 'minecraft:paper'
              my_claims.claim_create: 'minecraft:piston'
              my_claims.unclaim: 'minecraft:barrier'
              my_claims.resize: 'minecraft:ender_pearl'
              my_claims.back: 'minecraft:arrow'
              blocks.balance: 'minecraft:gold_nugget'
              blocks.buy: 'minecraft:emerald'
              blocks.gift: 'minecraft:player_head'
              blocks.back: 'minecraft:arrow'
              trust.untrust: 'minecraft:redstone_block'
              trust.trustlist: 'minecraft:book'
              trust.back: 'minecraft:arrow'
            trust_menu:
              rows: 4
              trust_level_slots:
                trust: 10
                container: 12
                access: 14
                manage: 16
            create_claim:
              rows: 5
              over_balance_mode: CAP
              bypass_tool_requirement: true
              steps: [1, 5, 10]
            """;

    @Test
    @DisplayName("Upgraded config resets layout, keeps policy, is idempotent")
    public void testLayoutMigration() {
        final Settings.GuiSettings gui = read(LAYOUT_V0_CONFIG);

        // Sanity: the fixture really is the pre-layout-version state
        assertEquals("minecraft:lime_stained_glass_pane", gui.getFillerMaterial());
        assertFalse(gui.getIconMaterials().containsKey("trust.level.build"));

        assertTrue(gui.migrateLayout(), "stale layout must be reset");

        // Layout reset to the current defaults
        assertEquals(Settings.GuiSettings.CURRENT_LAYOUT_VERSION, gui.getLayoutVersion());
        assertEquals("minecraft:black_stained_glass_pane", gui.getFillerMaterial());
        assertEquals("minecraft:bricks", gui.getIconMaterials().get("trust.level.build"));
        assertEquals("minecraft:golden_helmet", gui.getIconMaterials().get("trust.level.manage"));

        // Policy always kept
        assertTrue(gui.isEnabled());
        assertEquals(60, gui.getClaimToolCooldownSeconds());
        assertTrue(gui.getCreateClaim().isBypassToolRequirement());
        // Retired keys are dropped by the reset: over_balance_mode was
        // never read; trust.* icons belong to the retired TrustMenu layout
        assertFalse(gui.getIconMaterials().containsKey("trust.untrust"));
        assertFalse(gui.getIconMaterials().containsKey("trust.trustlist"));
        assertFalse(gui.getIconMaterials().containsKey("trust.back"));

        // Idempotent
        assertFalse(gui.migrateLayout(), "second call must be a no-op");
    }

    @Test
    @DisplayName("Blank document (absent version) migrates too — absent ≠ current, by design")
    public void testBlankDocumentMigrates() {
        final Settings.GuiSettings gui = read("{}");
        assertTrue(gui.migrateLayout(), "absent layout_version reads as 0, so the reset applies (by design)");
        assertEquals(Settings.GuiSettings.CURRENT_LAYOUT_VERSION, gui.getLayoutVersion());
    }

    private static final String LAYOUT_V4_YAML = """
            layout_version: 4
            enabled: true
            claim_tool_cooldown_seconds: 30
            allow_name_prefix: true
            main_menu:
              rows: 3
              my_claims_slot: 10
              trust_slot: 12
              blocks_slot: 14
              claim_tool_slot: 16
              public_claims_slot: 26
            """;

    @Test
    @DisplayName("A layout-4 config moves to the square Claim Menu and keeps the server's rules")
    public void testLayoutFourMovesToSquare() {
        final Settings.GuiSettings gui = read(LAYOUT_V4_YAML);
        assertTrue(gui.migrateLayout());
        assertEquals(13, gui.getMainMenu().getMyClaimsSlot());
        assertEquals(3, gui.getMainMenu().getPublicClaimsSlot());
        assertEquals("minecraft:ender_eye", gui.getIconMaterials().get("main.public_claims"));
        assertEquals(30, gui.getClaimToolCooldownSeconds(), "cooldown is a server rule and is kept");
        assertTrue(gui.isAllowNamePrefix(), "the name prefix option is a server rule and is kept");
        assertFalse(gui.migrateLayout(), "running it again changes nothing");
    }

    private static Settings.GuiSettings read(@NotNull String yaml) {
        try (InputStream input = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))) {
            // Production reads with LOWER_UNDERSCORE naming (ConfigProvider.YAML_CONFIGURATION_PROPERTIES);
            // reading without it silently drops snake_case keys.
            return YamlConfigurations.read(input, Settings.GuiSettings.class,
                    ConfigProvider.YAML_CONFIGURATION_PROPERTIES.build());
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to read GuiSettings fixture", e);
        }
    }
}
