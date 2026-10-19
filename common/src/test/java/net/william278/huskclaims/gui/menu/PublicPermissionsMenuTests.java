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

import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWarp;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiLayout;
import net.william278.huskclaims.gui.GuiTestKit;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.position.Position;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PublicPermissionsMenu render and routing tests: the pair layout matches the
 * accepted Paper menu (icon row 1/3, status row 2/4, warp pair last on
 * top-level claims only), the header counts actions only, toggling flips the
 * claim flag and persists the world, confirm-listed actions open the confirm
 * screen and re-check permission inside it, and a deleted claim redirects.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("Public Permissions Menu (shared model)")
public class PublicPermissionsMenuTests {

    private GuiTestKit kit;
    private GuiTestKit.FakeOnlineUser user;
    private Claim claim;
    private ClaimRef ref;

    @BeforeEach
    public void setUp() {
        kit = new GuiTestKit();
        user = kit.user();
        claim = kit.claimAt(0, 0, 10, 10);
        ref = ClaimRef.of("world", claim);
        // Warp toggling preflights the real /claimwarp command
        kit.command("claimwarp");
    }

    private PublicPermissionsMenu menu() {
        return new PublicPermissionsMenu(kit.controller, ref, user);
    }

    @Test
    @DisplayName("Render: pairs fill the first block; warp pair last; header counts actions only")
    public void testRenderPairs() {
        final Map<Integer, MenuIcon> icons = menu().icons();
        // Default allowed list is 12 actions + warp pair = 13 pairs (slots 10..22, 28..40
        // for icons/status), plus info, back. No second page with 12 actions.
        final int allowed = PublicPermissionsMenu.allowedOperations(kit.controller).size();
        assertTrue(allowed >= 12, "default config offers at least 12 actions");
        // allowed pairs (icon+status) + warp pair + info + back
        assertEquals(allowed * 2 + 2 + 2, icons.size());
        // First action pair: monster damage, on by default in new claims
        assertEquals("minecraft:zombie_head", icons.get(10).itemKey());
        assertEquals("minecraft:emerald_block", icons.get(19).itemKey());
        assertEquals(GuiTestKit.name("gui_public_perm_on"), icons.get(19).name());
        // Warp pair sits right after the last action pair on page
        final int[] warpPair = GuiLayout.publicActionPair(allowed);
        assertEquals("minecraft:ender_eye", icons.get(warpPair[0]).itemKey());
        assertEquals("minecraft:gray_dye", icons.get(warpPair[1]).itemKey(),
                "no warp set: gray dye");
        // Header counts actions only: 1 of 12 on (monster damage is default-on)
        assertEquals(GuiTestKit.name("gui_public_permissions"), icons.get(4).name());
        assertTrue(icons.get(4).lore().get(0).contains("1"));
        assertTrue(icons.get(4).lore().get(0).contains(Integer.toString(allowed)));
        assertEquals("public_permissions", menu().typeName());
        assertEquals(6, menu().rows());
    }

    @Test
    @DisplayName("Render: an enabled action shows emerald; the warp pair only on top-level claims")
    public void testEnabledAndWarpPairRules() {
        claim.getDefaultFlags().add(OperationType.BLOCK_INTERACT);
        final Map<Integer, MenuIcon> icons = menu().icons();
        final int[] pair = pairOf(OperationType.BLOCK_INTERACT);
        assertEquals("minecraft:emerald_block", icons.get(pair[1]).itemKey());
        // Child claims never offer the warp pair: their icon count lacks it entirely
        claim.createAndAddChild(
                net.william278.huskclaims.claim.Region.from(
                        net.william278.huskclaims.claim.Region.Point.at(2, 2),
                        net.william278.huskclaims.claim.Region.Point.at(5, 5)),
                kit.plugin);
        final ClaimRef childRef = ClaimRef.of("world",
                claim.getChildren().iterator().next());
        final PublicPermissionsMenu childMenu =
                new PublicPermissionsMenu(kit.controller, childRef, user);
        final int allowed = PublicPermissionsMenu.allowedOperations(kit.controller).size();
        assertEquals(allowed * 2 + 2, childMenu.icons().size(),
                "child menu: action pairs + info + back only, no warp pair");
    }

    @Test
    @DisplayName("Routing: back returns to Claim Settings")
    public void testBackToSettings() {
        menu().onClick(user, PublicPermissionsMenu.BACK_SLOT, ClickKind.LEFT);
        assertEquals("claim_settings", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: toggling a non-confirm action flips the flag and saves the world")
    public void testToggleFlipsFlag() {
        final OperationType op = OperationType.BLOCK_INTERACT; // not on the confirm list
        final int[] pair = pairOf(op);
        menu().onClick(user, pair[0], ClickKind.LEFT);
        assertTrue(claim.getDefaultFlags().contains(op), "flag enabled");
        assertEquals(1, kit.database.savedWorlds.size(), "world persisted");
        assertEquals(1, kit.host.refreshed.size(), "menu re-rendered");
        // And back off through the status half
        menu().onClick(user, pair[1], ClickKind.LEFT);
        assertFalse(claim.getDefaultFlags().contains(op), "flag disabled again");
    }

    @Test
    @DisplayName("Routing: a confirm-listed action opens Confirm; confirming re-checks and enables")
    public void testConfirmListedAction() {
        final OperationType op = OperationType.CONTAINER_OPEN; // on the default confirm list
        final int[] pair = pairOf(op);
        menu().onClick(user, pair[0], ClickKind.LEFT);
        assertEquals("confirm", kit.host.last().typeName());
        // Confirm is final: confirm click executes and reopens this menu
        final var confirmCfg = kit.settings.getGui().getConfirmMenu();
        kit.host.last().onClick(user, confirmCfg.getConfirmSlot(), ClickKind.LEFT);
        assertTrue(claim.getDefaultFlags().contains(op), "flag enabled after confirm");
        assertEquals("public_permissions", kit.host.last().typeName(),
                "menu reopens after the confirm screen");
    }

    @Test
    @DisplayName("Routing: claim deleted while open redirects instead of acting")
    public void testClaimDeletedWhileOpen() {
        final int[] pair = pairOf(OperationType.BLOCK_INTERACT);
        kit.claimWorlds.clear();
        menu().onClick(user, pair[0], ClickKind.LEFT);
        assertFalse(claim.getDefaultFlags().contains(OperationType.BLOCK_INTERACT));
        assertTrue(kit.host.opened.stream().anyMatch(v -> v.typeName().equals("claim_list")),
                "redirected to the claim list");
    }

    @Test
    @DisplayName("Routing: toggling the warp visibility flips it and saves")
    public void testWarpVisibilityToggle() {
        setWarp(claim, "home", false);
        final int allowed = PublicPermissionsMenu.allowedOperations(kit.controller).size();
        final int[] warpPair = GuiLayout.publicActionPair(allowed);
        menu().onClick(user, warpPair[0], ClickKind.LEFT);
        assertTrue(claim.getWarp().isPubliclyAccessible(), "warp now public");
        assertEquals(1, kit.database.savedWorlds.size());
        assertEquals(1, kit.host.refreshed.size());
    }

    @Test
    @DisplayName("Routing: warp toggle with no warp set sends the hint, changes nothing")
    public void testWarpToggleWithoutWarp() {
        final int allowed = PublicPermissionsMenu.allowedOperations(kit.controller).size();
        final int[] warpPair = GuiLayout.publicActionPair(allowed);
        menu().onClick(user, warpPair[1], ClickKind.LEFT);
        assertTrue(claim.getWarp() == null, "no warp was created");
        assertTrue(kit.host.refreshed.isEmpty(), "nothing changed");
        assertTrue(kit.database.savedWorlds.isEmpty());
    }

    @Test
    @DisplayName("Routing: a viewer without the menu permission is refused")
    public void testNoPermissionUserRefused() {
        final NoGuiPermissionUser viewer = new NoGuiPermissionUser(kit.plugin);
        final int[] pair = pairOf(OperationType.BLOCK_INTERACT);
        menu().onClick(viewer, pair[0], ClickKind.LEFT);
        assertFalse(claim.getDefaultFlags().contains(OperationType.BLOCK_INTERACT));
        assertTrue(kit.host.refreshed.isEmpty());
    }

    /* helpers ---------------------------------------------------------------- */

    private void setWarp(Claim c, String name, boolean publiclyAccessible) {
        final var region = c.getRegion();
        final Position pos = Position.at(
                region.getNearCorner().getBlockX() + 1, 64,
                region.getNearCorner().getBlockZ() + 1, GuiTestKit.WORLD);
        final ClaimWarp warp = ClaimWarp.of(name, user, pos);
        warp.setPubliclyAccessible(publiclyAccessible);
        c.setWarp(warp);
    }

    /** The (icon, status) slots of an action from the default allowed order. */
    private int[] pairOf(@NotNull OperationType op) {
        final int index = PublicPermissionsMenu.allowedOperations(kit.controller).indexOf(op);
        assertTrue(index >= 0, op + " is on the default allowed list");
        return GuiLayout.publicActionPair(index);
    }

    /** A user the menu permission refuses. */
    private static final class NoGuiPermissionUser extends GuiTestKit.FakeOnlineUser {
        NoGuiPermissionUser(@NotNull HuskClaims plugin) {
            super(plugin);
        }

        @Override
        public boolean hasPermission(@NotNull String permission, boolean isDefault) {
            return !permission.equals(PublicPermissionsMenu.GUI_PERMISSION);
        }

        @Override
        public boolean hasPermission(@NotNull String permission) {
            return !permission.equals(PublicPermissionsMenu.GUI_PERMISSION);
        }
    }

}
