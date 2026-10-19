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
import net.william278.huskclaims.claim.ClaimWarp;
import net.william278.huskclaims.gui.GuiTestKit;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
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
 * PublicClaimsMenu render and routing tests: every usable warp shows as a
 * clickable slot (private ones of strangers hidden, own private ones shown
 * with the trusted-only lore line), slots are sorted by warp name, the empty
 * state shows the sapling, and a click re-validates the live warp/claim and
 * re-checks /visit permission before teleporting through the shared helper.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("Public Claims Menu (shared model)")
public class PublicClaimsMenuTests {

    private GuiTestKit kit;
    private GuiTestKit.FakeOnlineUser user;
    private Claim ownerClaim;
    private Claim strangerClaim;

    @BeforeEach
    public void setUp() {
        kit = new GuiTestKit();
        user = kit.user();
        // The menu is the clickable /visits: both warp commands must exist
        kit.command("visit");
        kit.command("visits");
        ownerClaim = kit.claimAt(0, 0, 10, 10);
        strangerClaim = kit.newStrangerClaimAt(100, 100, 110, 110);
        // One warp per claim (a claim holds a single warp): three claims, three warps
        setWarp(ownerClaim, "alpha", true);
        setWarp(strangerClaim, "beta", true);
        setWarp(kit.newStrangerClaimAt(200, 200, 210, 210), "private-one", false);
    }

    private PublicClaimsMenu menu() {
        return new PublicClaimsMenu(kit.controller, user);
    }

    /** Sets a warp anchored inside the claim. */
    private void setWarp(Claim claim, String name, boolean publiclyAccessible) {
        final var region = claim.getRegion();
        final Position pos = Position.at(
                region.getNearCorner().getBlockX() + 1, 64,
                region.getNearCorner().getBlockZ() + 1, GuiTestKit.WORLD);
        final ClaimWarp warp = ClaimWarp.of(name, user, pos);
        warp.setPubliclyAccessible(publiclyAccessible);
        claim.setWarp(warp);
    }

    @Test
    @DisplayName("Render: public warps fill slots in name order; a stranger's private warp is hidden")
    public void testRenderPublicWarpsSorted() {
        final Map<Integer, MenuIcon> icons = menu().icons();
        // alpha + beta on the grid, plus Back; no paging on a single page
        assertEquals(3, icons.size());
        assertEquals("minecraft:ender_eye", icons.get(0).itemKey());
        assertEquals(literalName("alpha"), icons.get(0).name());
        assertEquals(literalName("beta"), icons.get(1).name());
        assertEquals("minecraft:arrow", icons.get(PublicClaimsMenu.BACK_SLOT).itemKey());
        // The stranger's private warp is hidden from this player
        assertFalse(icons.values().stream().anyMatch(i -> i.name().equals(literalName("private-one"))));
    }

    @Test
    @DisplayName("Render: own private warp shows with the private icon and lore line")
    public void testOwnPrivateWarpShownWithPrivateLore() {
        setWarp(ownerClaim, "my-secret", false);
        final Map<Integer, MenuIcon> icons = menu().icons();
        final MenuIcon secret = iconNamed("my-secret");
        assertEquals("minecraft:ender_pearl", secret.itemKey());
        assertTrue(secret.lore().stream()
                        .anyMatch(l -> l.contains(GuiTestKit.lore("gui_public_claim_lore_private"))),
                "private warps carry the trusted-only lore line");
        // The public warp has no private line (it was replaced by my-secret, so
        // beta is the public one on screen)
        final MenuIcon beta = iconNamed("beta");
        assertFalse(beta.lore().stream()
                .anyMatch(l -> l.contains(GuiTestKit.lore("gui_public_claim_lore_private"))));
    }

    @Test
    @DisplayName("Render: no usable warps shows the empty sapling at slot 22")
    public void testEmptyState() {
        kit.claimWorlds.get("world").getClaims().forEach(c -> c.setWarp(null));
        final Map<Integer, MenuIcon> icons = menu().icons();
        assertEquals("minecraft:oak_sapling", icons.get(PublicClaimsMenu.EMPTY_SLOT).itemKey());
        assertEquals(GuiTestKit.name("gui_public_claims_empty"),
                icons.get(PublicClaimsMenu.EMPTY_SLOT).name());
    }

    @Test
    @DisplayName("Routing: back returns to the main menu")
    public void testBackToMain() {
        menu().onClick(user, PublicClaimsMenu.BACK_SLOT, ClickKind.LEFT);
        assertEquals("main", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Routing: clicking a public warp teleports through the /visit helper")
    public void testClickTeleports() {
        final Position anchor = ownerClaim.getWarp().getAnchor();
        menu().onClick(user, 0, ClickKind.LEFT); // alpha
        assertEquals(1, user.teleports.size());
        assertEquals(anchor.getBlockX(), user.teleports.get(0).getBlockX());
        assertEquals(anchor.getBlockZ(), user.teleports.get(0).getBlockZ());
    }

    @Test
    @DisplayName("Routing: a warp unset while the menu was open does not teleport")
    public void testWarpUnsetWhileOpenDoesNotTeleport() {
        final PublicClaimsMenu menu = menu();
        ownerClaim.setWarp(null); // warp vanished after open
        menu.onClick(user, 0, ClickKind.LEFT);
        assertTrue(user.teleports.isEmpty(), "no teleport to stale data");
        assertEquals(1, kit.host.refreshed.size(), "menu re-collects and re-renders");
    }

    @Test
    @DisplayName("Routing: clicking with /visit revoked is refused without teleporting")
    public void testVisitPermissionRevokedWhileOpen() {
        final NoVisitUser noVisit = new NoVisitUser(kit.plugin);
        final PublicClaimsMenu menu = new PublicClaimsMenu(kit.controller, noVisit);
        menu.onClick(noVisit, 0, ClickKind.LEFT);
        assertTrue(noVisit.teleports.isEmpty());
    }

    @NotNull
    private MenuIcon iconNamed(String warpName) {
        final String expected = literalName(warpName);
        return menu().icons().values().stream()
                .filter(i -> i.name().equals(expected))
                .findFirst().orElseThrow();
    }

    /** What a warp name renders to through the real text rules. */
    @NotNull
    private static String literalName(@NotNull String value) {
        return net.william278.huskclaims.gui.GuiText.asName(
                net.william278.huskclaims.gui.GuiText.literal(value));
    }

    /** A user the /visit command would refuse. */
    private static final class NoVisitUser extends GuiTestKit.FakeOnlineUser {
        NoVisitUser(@NotNull HuskClaims plugin) {
            super(plugin);
        }

        @Override
        public boolean hasPermission(@NotNull String permission) {
            return false;
        }
    }

}
