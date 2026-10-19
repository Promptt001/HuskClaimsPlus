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
import net.william278.huskclaims.gui.GuiLayout;
import net.william278.huskclaims.trust.TrustLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import net.william278.huskclaims.gui.TrustIcons;
import java.util.Locale;
import net.william278.cloplib.operation.OperationType;

@DisplayName("Claim GUI Settings & Locales Tests")
public class GuiConfigTests {

    private Settings.GuiSettings gui;
    private Locales englishLocales;

    @BeforeEach
    @DisplayName("Load Default GUI Settings and Locales")
    public void setUp() {
        // Reading a blank YAML document yields a GuiSettings instance with all field defaults.
        // (Reading the full Settings class here would require runtime-only classes on the test classpath.)
        try (InputStream input = new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))) {
            gui = YamlConfigurations.read(input, Settings.GuiSettings.class);
        } catch (Throwable e) {
            fail("Failed to build default GuiSettings", e);
        }
        try (InputStream locales = getClass().getClassLoader().getResourceAsStream("locales/en-gb.yml")) {
            assertNotNull(locales, "en-gb.yml is missing from the locales folder");
            englishLocales = YamlConfigurations.read(locales, Locales.class);
        } catch (Throwable e) {
            fail("Failed to load en-gb.yml", e);
        }
    }

    @Test
    @DisplayName("Default GUI settings load with sane values")
    public void testDefaultGuiSettingsLoad() {
        assertNotNull(gui);
        // The menu is on by default; an upgraded config keeps its saved value.
        assertTrue(gui.isEnabled(), "GUI should be enabled by default");
        assertTrue(gui.getClaimToolCooldownSeconds() >= 0, "Cooldown must be non-negative");
        assertNotNull(gui.getFillerMaterial());
        assertFalse(gui.getIconMaterials().isEmpty(), "Icon material map should not be empty");
    }

    @Test
    @DisplayName("Menu rows are within 1-6")
    public void testMenuRowsValid() {
        assertAll(
                () -> assertTrue(gui.getMainMenu().getRows() >= 1 && gui.getMainMenu().getRows() <= 6),
                () -> assertTrue(gui.getBlocksMenu().getRows() >= 1 && gui.getBlocksMenu().getRows() <= 6),
                () -> assertTrue(gui.getConfirmMenu().getRows() >= 1 && gui.getConfirmMenu().getRows() <= 6),
                () -> assertTrue(gui.getInputTimeoutSeconds() >= 30, "Input timeout must be at least 30s")
        );
    }

    @Test
    @DisplayName("The Claim Menu buttons form a square centred in a three-row menu, with My Claims in the middle")
    public void testMainMenuIsACentredSquare() {
        final Settings.GuiSettings.MainMenuSettings main = gui.getMainMenu();
        assertEquals(3, main.getRows());
        assertEquals(13, main.getMyClaimsSlot(), "My Claims is the centre of the square");
        assertEquals(Set.of(3, 5, 21, 23), Set.of(main.getPublicClaimsSlot(), main.getTrustSlot(),
                main.getClaimToolSlot(), main.getBlocksSlot()), "the other four buttons are the corners");
    }

    @Test
    @DisplayName("All configured slots fit within their menu size and do not collide")
    public void testSlotsWithinMenuAndNoCollisions() {
        // Main menu
        assertNoCollisions("main", gui.getMainMenu().getRows() * 9, List.of(
                gui.getMainMenu().getMyClaimsSlot(),
                gui.getMainMenu().getTrustSlot(),
                gui.getMainMenu().getBlocksSlot(),
                gui.getMainMenu().getClaimToolSlot(),
                gui.getMainMenu().getPublicClaimsSlot()
        ));
        // Blocks menu
        assertNoCollisions("blocks", gui.getBlocksMenu().getRows() * 9, List.of(
                gui.getBlocksMenu().getBalanceSlot(),
                gui.getBlocksMenu().getBuySlot(),
                gui.getBlocksMenu().getGiftSlot(),
                gui.getBlocksMenu().getBackSlot()
        ));
        // Trust hub geometry is code-defined — no config
        // slots to collide; see testTrustHubLayoutPure below.
        // Confirm menu: no Back slot, because Cancel returns
        assertNoCollisions("confirm", gui.getConfirmMenu().getRows() * 9, List.of(
                gui.getConfirmMenu().getInfoSlot(),
                gui.getConfirmMenu().getConfirmSlot(),
                gui.getConfirmMenu().getCancelSlot()
        ));
    }

    private void assertNoCollisions(@NotNull String menu, int size, @NotNull List<Integer> slots) {
        final Set<Integer> seen = new HashSet<>();
        for (final int slot : slots) {
            assertTrue(slot >= 0 && slot < size,
                    () -> String.format("%s slot %d out of range (0-%d)", menu, slot, size - 1));
            assertTrue(seen.add(slot),
                    () -> String.format("%s slot %d is duplicated", menu, slot));
        }
    }

    @Test
    @DisplayName("All required GUI locale keys are present in en-gb")
    public void testGuiLocaleKeysPresent() {
        final List<String> required = List.of(
                "gui_main_title", "gui_main_my_claims", "gui_main_trust", "gui_main_blocks",
                "gui_main_claim_tool", "gui_main_public_claims",
                "gui_public_warp_icon", "gui_public_warp_icon_lore",
                "gui_public_warp_none", "gui_public_warp_none_lore", "gui_public_warp_trusted",
                "gui_claim_list_title", "gui_children_title",
                "gui_blocks_title", "gui_blocks_balance", "gui_blocks_buy", "gui_blocks_gift",
                "gui_trust_title", "gui_trust_untrust", "gui_trust_list",
                "gui_back", "gui_filler_name",
                "gui_prompt_player", "gui_prompt_amount", "gui_prompt_radius", "gui_prompt_blocks",
                "gui_input_timeout", "gui_input_cancelled",
                "gui_confirm", "gui_confirm_lore", "gui_cancel", "gui_cancel_lore",
                "gui_confirm_value",
                "gui_claim_tool_cooldown", "gui_claim_tool_given", "gui_claim_tool_have",
                "gui_claim_tool_inventory_full",
                "gui_error_no_permission", "gui_error_no_economy",
                "gui_error_invalid_claim_tool",
                "gui_public_claims_title"
        );
        for (final String key : required) {
            assertTrue(englishLocales.getRawLocale(key).isPresent(),
                    () -> "Missing required GUI locale key: " + key);
        }
    }

    @Test
    @DisplayName("Chat value prompts are plain text with cancel hint")
    public void testChatPromptsArePlainText() {
        // Prompts must ask for a VALUE in chat (no suggest_command prefill,
        // no MineDown markup inside a command) and must offer the cancel keyword
        for (final String key : List.of("gui_prompt_player", "gui_prompt_amount",
                "gui_prompt_radius", "gui_prompt_blocks")) {
            final var raw = englishLocales.getRawLocale(key).orElse("");
            assertFalse(raw.isEmpty(), () -> key + " must be present");
            assertFalse(raw.contains("suggest_command"),
                    () -> key + " must not suggest a command");
            assertTrue(raw.toLowerCase().contains("cancel"),
                    () -> key + " must mention the cancel keyword");
        }
    }

    @Test
    @DisplayName("The trust-level picker names every level and every level-key exists (per-level keys retired)")
    public void testTrustLevelLocaleKeysPresent() {
        // Derive the expected levels from the REAL default TrustLevels config,
        // never from a hand-copied list — a hardcoded alias list ("trust"
        // instead of the build level's id "build") would enshrine a wrong default.
        final TrustLevels trustLevels = YamlConfigurations.read(
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
                TrustLevels.class);
        assertFalse(trustLevels.getTrustLevels().isEmpty(), "Default trust levels should not be empty");
        // The picker names every level via gui_trust_level_name +
        // LevelBullets phrases, and marks the current level with gui_trust_current.
        // The per-level gui_trust_level_<id>/_lore keys are retired (unreferenced).
        for (final String key : List.of("gui_trust_level_name", "gui_trust_level_picker_title",
                "gui_trust_current", "gui_trust_includes", "gui_trust_bullet", "gui_trust_more")) {
            assertTrue(englishLocales.getRawLocale(key).isPresent(),
                    () -> "Missing picker locale key: " + key);
        }
        // Regression: the "build" level must be configured directly by ID
        assertTrue(trustLevels.getTrustLevels().stream().anyMatch(l -> l.getId().equals("build")),
                "Default trust levels should include the build level");
        // The per-level keys must be GONE (they would otherwise drift stale)
        for (final TrustLevel level : trustLevels.getTrustLevels()) {
            assertFalse(englishLocales.getRawLocale("gui_trust_level_" + level.getId()).isPresent(),
                    () -> "Retired per-level locale key still present: gui_trust_level_"
                            + level.getId());
        }
    }

    @Test
    @DisplayName("Hub layout columns fit the code-defined slot scheme (replaces trust_level_slots config)")
    public void testTrustHubLayoutPure() {
        // TrustMenu geometry is code-defined: header 4, EVERYONE row
        // label 9 with toggles at 9+col, spacer, PLAYERS label 27 with icons at
        // 27+col, back 45 / list 48 / remove 50. The pure math lives in
        // GuiLayoutTests; here we pin the code constants themselves.
        final int[] reserved = {4, 9, 27, 45, 48, 50};
        for (int i = 1; i <= 7; i++) {
            final int n = i;
            final int[] columns = GuiLayout.levelColumns(n);
            for (final int c : columns) {
                final int col = c;
                final int pub = 9 + col;
                final int player = 27 + col;
                assertTrue(pub >= 10 && pub <= 17 && player >= 29 && player <= 35,
                        () -> "n=" + n + " column " + col + " out of the hub rows");
                for (final int r : reserved) {
                    assertTrue(pub != r && player != r,
                            () -> "collision with reserved slot " + r + " at column " + col);
                }
            }
        }
    }

    @Test
    @DisplayName("Default trust-level icons match the built-in fallbacks")
    public void testTrustLevelIconsDistinct() {
        // The icon map defaults equal the pure id-first fallbacks; distinctness
        // with no config map at all is covered by TrustIconsTests: assert what
        // production renders on an empty or upgraded icon map.
        final Set<String> materials = new HashSet<>();
        final TrustLevels trustLevels = YamlConfigurations.read(
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
                TrustLevels.class);
        for (final TrustLevel level : trustLevels.getTrustLevels()) {
            final String icon = gui.getIconMaterials().get("trust.level." + level.getId());
            assertNotNull(icon, () -> "No icon_materials entry for trust level: " + level.getId());
            assertTrue(materials.add(icon),
                    () -> "trust.level." + level.getId() + " reuses another level's icon: " + icon);
            assertEquals(TrustIcons.fallbackIcon(level),
                    icon.substring(icon.lastIndexOf(':') + 1).toUpperCase(Locale.ROOT),
                    () -> "icon_materials default for " + level.getId()
                            + " must equal the TrustIcons fallback");
        }
    }

    @Test
    @DisplayName("Hub ladder and text keys are present")
    public void testTrustHubLocaleKeysPresent() {
        for (final String key : List.of("gui_trust_header_lore", "gui_trust_row_public",
                "gui_trust_row_public_lore", "gui_trust_row_players", "gui_trust_row_players_lore",
                "gui_trust_public_on", "gui_trust_public_on_lore", "gui_trust_public_on_hint",
                "gui_trust_public_included_lore", "gui_trust_public_included_hint",
                "gui_trust_public_off", "gui_trust_public_off_lore", "gui_trust_public_off_hint",
                "gui_trust_public_chest_warning", "gui_trust_public_locked",
                "gui_trust_public_locked_lore", "gui_trust_level_name", "gui_trust_grant_lore",
                "gui_trust_grant_footer", "gui_trust_includes", "gui_trust_bullet", "gui_trust_more",
                "gui_trust_manage_warning", "gui_trust_list", "gui_trust_list_lore",
                "gui_trust_untrust", "gui_trust_untrust_lore", "gui_trust_confirm_grant")) {
            assertTrue(englishLocales.getRawLocale(key).isPresent(),
                    () -> "Missing step-6 hub locale key: " + key);
        }
    }

    @Test
    @DisplayName("New-claim defaults: no PvP in flags or Public Permissions allowlist")
    public void testNoPvpInDefaults() {
        // PvP is not ON for new claims, and the Public Permissions
        // (Public Actions) allowlist does not offer a PvP
        // toggle. Existing claims/configs keep their saved values.
        final Settings.ClaimSettings claims = YamlConfigurations.read(
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
                Settings.ClaimSettings.class);
        assertFalse(claims.getDefaultFlags().contains(
                        OperationType.PLAYER_DAMAGE_PLAYER),
                "PvP must not be ON for new claims by default");
        assertFalse(claims.getAdminFlags().contains(
                        OperationType.PLAYER_DAMAGE_PLAYER),
                "PvP must not be ON for new admin claims by default");
        assertFalse(gui.getPublicPermissions().getAllowed().contains("PLAYER_DAMAGE_PLAYER"),
                "Public Permissions allowlist must not offer PvP");
    }

    @Test
    @DisplayName("Default access/container levels cannot place or break vehicles")
    public void testVehiclesNotInLowTrustLevels() {
        // Everybody Access / Everybody Container must
        // not let every player place or break boats and minecarts in a claim.
        // Build and Manage keep vehicle rights (they imply real build trust).
        final TrustLevels trustLevels = YamlConfigurations.read(
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)),
                TrustLevels.class);
        for (final TrustLevel level : trustLevels.getTrustLevels()) {
            final boolean vehicles = level.getFlags().contains(
                    OperationType.PLACE_VEHICLE)
                    || level.getFlags().contains(
                    OperationType.BREAK_VEHICLE);
            if (level.getId().equals("access") || level.getId().equals("container")) {
                assertFalse(vehicles, () -> level.getId() + " must not include vehicle flags");
            } else {
                assertTrue(vehicles, () -> level.getId() + " keeps vehicle flags");
            }
        }
    }

}
