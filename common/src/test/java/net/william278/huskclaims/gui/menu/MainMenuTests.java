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

package net.william278.huskclaims.gui.menu;

import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.gui.GuiTestKit;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MainMenu render and routing tests: the slot map matches the Paper menu
 * item-for-item (icon key from config with fallback, name key, lore key),
 * Public Claims hides while claim warps are off, and every navigation
 * target is a migrated view.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("Main Menu (shared model)")
public class MainMenuTests {

    private GuiTestKit kit;
    private Settings.GuiSettings.MainMenuSettings cfg;
    private MainMenu menu;

    @BeforeEach
    public void setUp() {
        kit = new GuiTestKit();
        cfg = kit.settings.getGui().getMainMenu();
        menu = new MainMenu(kit.controller);
    }

    @Test
    @DisplayName("Render: five buttons at the configured slots with their name/lore keys")
    public void testRenderMatchesPaperSlots() {
        final Map<Integer, MenuIcon> icons = menu.icons();

        assertEquals(5, icons.size());

        assertEquals("minecraft:grass_block", icons.get(cfg.getMyClaimsSlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_main_my_claims"), icons.get(cfg.getMyClaimsSlot()).name());
        assertEquals(java.util.List.of(GuiTestKit.lore("gui_main_my_claims_lore")),
                icons.get(cfg.getMyClaimsSlot()).lore());

        assertEquals("minecraft:ender_eye", icons.get(cfg.getPublicClaimsSlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_main_public_claims"), icons.get(cfg.getPublicClaimsSlot()).name());

        assertEquals("minecraft:diamond", icons.get(cfg.getTrustSlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_main_trust"), icons.get(cfg.getTrustSlot()).name());

        assertEquals("minecraft:golden_shovel", icons.get(cfg.getClaimToolSlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_main_claim_tool"), icons.get(cfg.getClaimToolSlot()).name());

        assertEquals("minecraft:gold_ingot", icons.get(cfg.getBlocksSlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_main_blocks"), icons.get(cfg.getBlocksSlot()).name());
        assertEquals(java.util.List.of(GuiTestKit.lore("gui_main_blocks_lore")),
                icons.get(cfg.getBlocksSlot()).lore());

        assertTrue(menu.rows() >= 1 && menu.rows() <= 6);
        assertEquals("main", menu.typeName());
    }

    @Test
    @DisplayName("Render: Public Claims hides while claim warps are off")
    public void testPublicClaimsHiddenWhenWarpsOff() throws Exception {
        setWarpsEnabled(false);
        final Map<Integer, MenuIcon> icons = menu.icons();
        assertEquals(4, icons.size());
        assertFalse(icons.containsKey(cfg.getPublicClaimsSlot()));
    }

    @Test
    @DisplayName("Render: Public Claims shows while claim warps are on")
    public void testPublicClaimsShownWhenWarpsOn() throws Exception {
        setWarpsEnabled(true);
        assertEquals(5, menu.icons().size());
    }

    @Test
    @DisplayName("Routing: My Claims opens the migrated claim list")
    public void testMyClaimsOpensClaimList() {
        menu.onClick(kit.user(), cfg.getMyClaimsSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("claim_list", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: Public Claims opens the migrated warp browser")
    public void testPublicClaimsOpensBrowser() throws Exception {
        setWarpsEnabled(true);
        // The browser is the clickable /visits: both warp commands must exist
        kit.command("visit");
        kit.command("visits");
        menu.onClick(kit.user(), cfg.getPublicClaimsSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("public_claims", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: Friends & Trust routes through the legacy bridge")
    public void testTrustGoesLegacy() {
        menu.onClick(kit.user(), cfg.getTrustSlot(), ClickKind.LEFT);
        // No target and no default: the claim picker opens (pick-for-trust mode)
        assertTrue(kit.host.opened.stream().anyMatch(v -> v.typeName().equals("claim_list")));
    }

    @Test
    @DisplayName("Routing: claim tool button hands out through the host")
    public void testClaimToolGoesThroughHost() {
        menu.onClick(kit.user(), cfg.getClaimToolSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.toolHandouts);
    }

    @Test
    @DisplayName("Routing: Claim Blocks opens the migrated BlocksMenu")
    public void testBlocksOpensMigratedMenu() {
        menu.onClick(kit.user(), cfg.getBlocksSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("blocks", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: clicking filler does nothing")
    public void testFillerClickIsNoOp() {
        menu.onClick(kit.user(), 0, ClickKind.LEFT);
        assertTrue(kit.host.opened.isEmpty() && kit.host.toolHandouts == 0);
    }

    @Test
    @DisplayName("Invalid configured icon falls back to the default (never a barrier)")
    public void testInvalidIconFallsBack() throws Exception {
        setIconMaterials(Map.of("main.my_claims", "minecraft:not_an_item"));
        final Map<Integer, MenuIcon> icons = menu.icons();
        assertEquals("minecraft:grass_block", icons.get(cfg.getMyClaimsSlot()).itemKey(),
                "an invalid configured icon must render the fallback, not a barrier");
    }

    private void setWarpsEnabled(boolean enabled) throws Exception {
        final var warpsField = Settings.ClaimSettings.class.getDeclaredField("warps");
        warpsField.setAccessible(true);
        final Object warps = warpsField.get(kit.settings.getClaims());
        final var enabledField = warps.getClass().getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(warps, enabled);
    }

    private void setIconMaterials(Map<String, String> map) throws Exception {
        final var field = Settings.GuiSettings.class.getDeclaredField("iconMaterials");
        field.setAccessible(true);
        field.set(kit.settings.getGui(), new java.util.HashMap<>(map));
    }

}
