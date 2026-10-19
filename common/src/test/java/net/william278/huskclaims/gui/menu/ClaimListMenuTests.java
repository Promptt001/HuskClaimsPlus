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

import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiTestKit;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClaimListMenu render and routing tests: the slot map matches the accepted
 * Paper browser item-for-item, paging and the sort cycle behave, pick mode
 * hides Create/Sort and routes claim clicks to the trust hub, and a claim
 * deleted while the menu was open renders the claim-gone line.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("Claim List Menu (shared model)")
public class ClaimListMenuTests {

    private GuiTestKit kit;
    private GuiTestKit.FakeOnlineUser user;

    @BeforeEach
    public void setUp() {
        kit = new GuiTestKit();
        user = kit.user();
    }

    private ClaimListMenu menu(ClaimRef parent) {
        return new ClaimListMenu(kit.controller, user, parent);
    }

    @Test
    @DisplayName("Render: one icon per owned claim, plus Back/Create/Sort in browse mode")
    public void testRenderBrowseMode() {
        kit.claimAt(0, 0, 10, 10);
        kit.claimAt(100, 100, 110, 110);
        final var cfg = kit.settings.getGui().getClaimListMenu();
        final Map<Integer, MenuIcon> icons = menu(null).icons();

        // Two claim icons at slots 0-1, Back/Create/Sort on the control row; no paging buttons with one page
        assertEquals(5, icons.size());
        assertEquals("minecraft:grass_block", icons.get(0).itemKey());
        assertEquals("minecraft:grass_block", icons.get(1).itemKey());
        assertEquals("minecraft:arrow", icons.get(cfg.getBackSlot()).itemKey());
        assertEquals("minecraft:piston", icons.get(cfg.getCreateSlot()).itemKey());
        assertEquals("minecraft:hopper", icons.get(cfg.getSortSlot()).itemKey());
        assertFalse(icons.containsKey(cfg.getPrevSlot()));
        assertFalse(icons.containsKey(cfg.getNextSlot()));
        assertTrue(menu(null).rows() == 6);
        assertEquals("claim_list", menu(null).typeName());
    }

    @Test
    @DisplayName("Render: claim icon lore carries world, size, status, public and trusted lines")
    public void testClaimIconLoreKeys() {
        kit.claimAt(0, 0, 10, 10);
        final MenuIcon icon = menu(null).icons().get(0);
        assertEquals(GuiTestKit.lore("gui_claim_lore_world", "world"), icon.lore().get(0));
        assertEquals(GuiTestKit.lore("gui_claim_lore_size", "11", "11", "121"), icon.lore().get(1));
        // The status value is itself a locale key, resolved through text() and inserted literally
        assertEquals(GuiTestKit.lore("gui_claim_lore_status",
                GuiTestKit.echo("gui_claim_status_normal")), icon.lore().get(2));
        assertTrue(icon.lore().contains(GuiTestKit.lore("gui_claim_lore_public",
                GuiTestKit.echo("gui_claim_lore_public_none"))));
        assertTrue(icon.lore().contains(GuiTestKit.lore("gui_claim_lore_trusted", "0")));
        assertFalse(icon.lore().contains(GuiTestKit.lore("gui_claim_gone")));
    }

    @Test
    @DisplayName("Render: empty list shows the empty message, not a claim grid")
    public void testRenderEmptyList() {
        final var cfg = kit.settings.getGui().getClaimListMenu();
        final Map<Integer, MenuIcon> icons = menu(null).icons();
        // Empty message + Back + Create + Sort; no paging buttons with zero claims
        assertEquals(4, icons.size());
        assertEquals("minecraft:oak_sapling", icons.get(cfg.getEmptySlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_claim_list_empty"), icons.get(cfg.getEmptySlot()).name());
    }

    @Test
    @DisplayName("Render: pick mode hides Create and Sort and shows the pick title")
    public void testRenderPickMode() {
        kit.claimAt(0, 0, 10, 10);
        final var cfg = kit.settings.getGui().getClaimListMenu();
        final ClaimListMenu pick = new ClaimListMenu(kit.controller, user, null, ClaimListMenu.Mode.PICK_FOR_TRUST);
        final Map<Integer, MenuIcon> icons = pick.icons();
        assertFalse(icons.containsKey(cfg.getCreateSlot()));
        assertFalse(icons.containsKey(cfg.getSortSlot()));
        assertTrue(icons.containsKey(cfg.getBackSlot()));
        assertEquals(GuiTestKit.echo("gui_claim_list_pick_title"), pick.title().orElse(null));
    }

    @Test
    @DisplayName("Routing: claim click opens the migrated Claim Settings")
    public void testClaimClickOpensSettings() {
        kit.claimAt(0, 0, 10, 10);
        final ClaimListMenu m = menu(null);
        m.onClick(user, 0, ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("claim_settings", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: pick-mode claim click opens the trust hub")
    public void testPickModeClaimClickOpensTrustHub() {
        kit.claimAt(0, 0, 10, 10);
        final ClaimListMenu pick = new ClaimListMenu(kit.controller, user, null, ClaimListMenu.Mode.PICK_FOR_TRUST);
        pick.onClick(user, 0, ClickKind.LEFT);
        assertEquals("trust", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: Back from the top list goes to the main menu")
    public void testBackGoesToMain() {
        final var cfg = kit.settings.getGui().getClaimListMenu();
        menu(null).onClick(user, cfg.getBackSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("main", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: Create opens the migrated Create Claim stepper")
    public void testCreateOpensStepper() {
        final var cfg = kit.settings.getGui().getClaimListMenu();
        menu(null).onClick(user, cfg.getCreateSlot(), ClickKind.LEFT);
        assertEquals("create_claim", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Sort: the cycle button advances the sort and resets to page 0")
    public void testSortCycle() {
        kit.claimAt(0, 0, 10, 10);
        final var cfg = kit.settings.getGui().getClaimListMenu();
        final ClaimListMenu m = menu(null);
        m.onClick(user, cfg.getSortSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.refreshed.size());
        // The second render's sort item now names NAME, the next in the cycle
        final MenuIcon sort = m.icons().get(cfg.getSortSlot());
        assertEquals(GuiTestKit.name("gui_claim_list_sort",
                GuiTestKit.echo("gui_claim_list_sort_name")), sort.name());
    }

    @Test
    @DisplayName("Paging: prev/next appear with two pages and page through")
    public void testPaging() {
        for (int i = 0; i < 50; i++) {
            kit.claimAt(i * 100, 0, i * 100 + 10, 10);
        }
        final var cfg = kit.settings.getGui().getClaimListMenu();
        final ClaimListMenu m = menu(null);
        // 50 claims, 45 per page → two pages; on page 0 only next is shown
        assertTrue(m.icons().containsKey(cfg.getNextSlot()));
        assertFalse(m.icons().containsKey(cfg.getPrevSlot()));
        m.onClick(user, cfg.getNextSlot(), ClickKind.LEFT);
        assertTrue(m.icons().containsKey(cfg.getPrevSlot()));
        assertFalse(m.icons().containsKey(cfg.getNextSlot()));
        // Slot 0 on page 1 is the 46th claim (sorted WORLD_NAME then label; all
        // labels differ so the second page starts at index 45)
        m.onClick(user, 0, ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("claim_settings", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Claim deleted while open: the icon renders the claim-gone line")
    public void testClaimDeletedWhileOpen() {
        final Claim claim = kit.claimAt(0, 0, 10, 10);
        final ClaimListMenu m = menu(null);
        // Delete it: remove the only claim world the kit serves
        kit.claimWorlds.clear();
        final MenuIcon icon = m.icons().get(0);
        assertTrue(icon.lore().contains(GuiTestKit.lore("gui_claim_gone")));
    }

    @Test
    @DisplayName("Children: a parent ref lists the parent's children, Back returns to its settings")
    public void testChildrenList() {
        final Claim parent = kit.claimAt(0, 0, 50, 50);
        final Claim child = parent.createAndAddChild(
                net.william278.huskclaims.claim.Region.from(
                        net.william278.huskclaims.claim.Region.Point.at(5, 5),
                        net.william278.huskclaims.claim.Region.Point.at(10, 10)),
                kit.plugin);
        final ClaimRef parentRef = ClaimRef.of("world", parent);
        final ClaimListMenu m = menu(parentRef);
        assertEquals(1, countClaimIcons(m));
        assertEquals(GuiTestKit.echo("gui_children_title"), m.title().orElse(null));
        final var cfg = kit.settings.getGui().getClaimListMenu();
        m.onClick(user, cfg.getBackSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("claim_settings", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Unclaimed slots are not rendered (filler is the host's job)")
    public void testFillerNotRendered() {
        kit.claimAt(0, 0, 10, 10);
        final Map<Integer, MenuIcon> icons = menu(null).icons();
        assertFalse(icons.containsKey(5));
    }

    private int countClaimIcons(ClaimListMenu m) {
        // Claim icons are grass blocks/oak fences at slots < PER_PAGE
        int count = 0;
        for (Map.Entry<Integer, MenuIcon> e : m.icons().entrySet()) {
            if (e.getKey() < ClaimListMenu.PER_PAGE
                    && (e.getValue().itemKey().equals("minecraft:grass_block")
                    || e.getValue().itemKey().equals("minecraft:oak_fence"))) {
                count++;
            }
        }
        return count;
    }

}
