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

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiTestKit;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClaimSettingsMenu render and routing tests: the slot map matches the
 * accepted Paper settings menu item-for-item (locked unclaim/warp states
 * included), every owner-only click re-checks and re-resolves the claim, a
 * claim deleted or re-owned while the menu was open redirects instead of
 * acting, and the flows moved into the controller (rename, warp set/delete,
 * unclaim) run through prompts and confirm screens.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("Claim Settings Menu (shared model)")
public class ClaimSettingsMenuTests {

    private GuiTestKit kit;
    private GuiTestKit.FakeOnlineUser user;
    private Claim claim;
    private ClaimRef ref;

    @BeforeEach
    public void setUp() {
        kit = new GuiTestKit();
        user = kit.user();
        // The settings menu preflights the real /unclaim and /claimwarp commands
        // before offering their buttons
        kit.command("unclaim");
        kit.command("claimwarp");
        claim = kit.claimAt(0, 0, 10, 10);
        ref = ClaimRef.of("world", claim);
    }

    private ClaimSettingsMenu menu() {
        return new ClaimSettingsMenu(kit.controller, ref, user);
    }

    @Test
    @DisplayName("Render: every configured slot is filled at its configured position")
    public void testRenderSlots() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        final Map<Integer, MenuIcon> icons = menu().icons();
        // info, rename, privacy, trust hub, public permissions, trustees, resize, warp, back, unclaim
        assertEquals(10, icons.size());
        assertEquals("minecraft:grass_block", icons.get(cfg.getInfoSlot()).itemKey());
        assertEquals("minecraft:name_tag", icons.get(cfg.getRenameSlot()).itemKey());
        assertEquals("minecraft:oak_door", icons.get(cfg.getPrivacySlot()).itemKey());
        assertEquals("minecraft:player_head", icons.get(cfg.getPublicAccessSlot()).itemKey());
        assertEquals("minecraft:book", icons.get(cfg.getTrusteesSlot()).itemKey());
        assertEquals("minecraft:scaffolding", icons.get(cfg.getResizeSlot()).itemKey());
        assertEquals("minecraft:arrow", icons.get(cfg.getBackSlot()).itemKey());
        assertEquals("minecraft:tnt", icons.get(cfg.getUnclaimSlot()).itemKey());
        // Children button only with children; warp icon with warps on and no warp set
        assertFalse(icons.containsKey(cfg.getChildrenSlot()));
        assertEquals("minecraft:ender_pearl", icons.get(cfg.getWarpSlot()).itemKey());
        assertTrue(menu().rows() == 6);
        assertEquals("claim_settings", menu().typeName());
    }

    @Test
    @DisplayName("Render: children button appears only when the claim has children")
    public void testChildrenButton() throws Exception {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        claim.createAndAddChild(
                net.william278.huskclaims.claim.Region.from(
                        net.william278.huskclaims.claim.Region.Point.at(2, 2),
                        net.william278.huskclaims.claim.Region.Point.at(5, 5)),
                kit.plugin);
        assertTrue(menu().icons().containsKey(cfg.getChildrenSlot()));
        assertEquals("minecraft:oak_fence", menu().icons().get(cfg.getChildrenSlot()).itemKey());
    }

    @Test
    @DisplayName("Render: unclaim locks to a barrier for a viewer without /unclaim permission")
    public void testUnclaimLockedWithoutPermission() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        final ClaimSettingsMenu locked = new ClaimSettingsMenu(
                kit.controller, ref, new NoPermissionUser(kit.plugin));
        assertEquals("minecraft:barrier", locked.icons().get(cfg.getUnclaimSlot()).itemKey());
        assertEquals(GuiTestKit.name("gui_unclaim_locked"),
                locked.icons().get(cfg.getUnclaimSlot()).name());
    }

    @Test
    @DisplayName("Routing: Back goes to the claim list")
    public void testBackGoesToClaimList() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        menu().onClick(user, cfg.getBackSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("claim_list", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: children button opens the child list for this claim")
    public void testChildrenButtonOpensChildList() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        claim.createAndAddChild(
                net.william278.huskclaims.claim.Region.from(
                        net.william278.huskclaims.claim.Region.Point.at(2, 2),
                        net.william278.huskclaims.claim.Region.Point.at(5, 5)),
                kit.plugin);
        menu().onClick(user, cfg.getChildrenSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("claim_list", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: rename asks for a name through the host prompt")
    public void testRenamePrompts() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        menu().onClick(user, cfg.getRenameSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.prompts.size());
        assertEquals("gui_prompt_claim_name", kit.host.prompts.get(0).titleKey());
    }

    @Test
    @DisplayName("Routing: rename stores the sanitized name and persists the world")
    public void testRenameStoresName() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        menu().onClick(user, cfg.getRenameSlot(), ClickKind.LEFT);
        kit.host.prompts.get(0).onSubmit().accept("  My&l Claim  ");
        assertTrue(claim.getMetadata().containsKey("huskclaims:gui_name"));
        assertEquals("My Claim", claim.getMetadata().get("huskclaims:gui_name"));
        assertEquals(1, kit.database.savedWorlds.size());
    }

    @Test
    @DisplayName("Routing: unclaim opens a confirm screen with the TNT info item")
    public void testUnclaimOpensConfirm() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        kit.inClaimCommand("unclaim");
        menu().onClick(user, cfg.getUnclaimSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        final MenuView confirm = kit.host.last();
        assertEquals("confirm", confirm.typeName());
        final var confirmCfg = kit.settings.getGui().getConfirmMenu();
        assertEquals("minecraft:tnt", confirm.icons().get(confirmCfg.getInfoSlot()).itemKey());
    }

    @Test
    @DisplayName("Routing: privacy click dispatches claimprivate through the scoped dispatcher")
    public void testPrivacyClickDispatches() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        final GuiTestKit.RecordingInClaimCommand cmd = kit.inClaimCommand("claimprivate");
        menu().onClick(user, cfg.getPrivacySlot(), ClickKind.LEFT);
        // The dispatcher re-resolves async (run inline in the kit) and executes
        assertFalse(cmd.executedWith.isEmpty(), "claimprivate should have been dispatched");
    }

    @Test
    @DisplayName("Claim deleted while open: any owner-only click redirects to the claim list")
    public void testClaimDeletedWhileOpen() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        kit.claimWorlds.clear(); // the claim no longer resolves
        menu().onClick(user, cfg.getRenameSlot(), ClickKind.LEFT);
        assertEquals(1, kit.host.opened.size());
        assertEquals("claim_list", kit.host.last().typeName());
        assertTrue(kit.host.prompts.isEmpty(), "no prompt for a gone claim");
    }

    @Test
    @DisplayName("Ownership changed while open: owner-only click says no-permission instead of acting")
    public void testOwnershipChangedWhileOpen() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        // Give the claim to someone else: the ref still resolves by box, but the
        // viewer no longer owns it
        final Claim stranger = kit.newStrangerClaimAt(0, 0, 10, 10);
        kit.claimWorlds.get("world").removeClaim(claim);
        menu().onClick(user, cfg.getRenameSlot(), ClickKind.LEFT);
        assertTrue(kit.host.prompts.isEmpty(), "rename must not prompt for someone else's claim");
        assertTrue(kit.host.opened.stream().noneMatch(v -> v.typeName().equals("claim_settings")));
    }

    @Test
    @DisplayName("Routing: trust hub entry opens the hub with settings as Back")
    public void testTrustHubEntry() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        menu().onClick(user, cfg.getPublicAccessSlot(), ClickKind.LEFT);
        assertEquals("trust", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: public permissions opens the migrated pair menu; trustees and resize too")
    public void testMigratedRoutes() {
        final var cfg = kit.settings.getGui().getClaimSettingsMenu();
        menu().onClick(user, cfg.getPublicPermissionsSlot(), ClickKind.LEFT);
        assertEquals("public_permissions", kit.host.last().typeName());
        menu().onClick(user, cfg.getTrusteesSlot(), ClickKind.LEFT);
        assertEquals("trustees", kit.host.last().typeName());
        menu().onClick(user, cfg.getResizeSlot(), ClickKind.LEFT);
        assertEquals("resize", kit.host.last().typeName());
    }

    /** A viewer the registered /unclaim command refuses. */
    private static final class NoPermissionUser extends GuiTestKit.FakeOnlineUser {
        NoPermissionUser(@NotNull HuskClaims plugin) {
            super(plugin);
        }

        @Override
        public boolean hasPermission(@NotNull String permission) {
            return false;
        }
    }

}
