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
import net.william278.huskclaims.HuskClaims;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stepper base, Create Claim and Resize render/routing tests: the stepper
 * geometry and shift-×5 clamp behaviour, create's blocker states (the
 * permission preflight refusal included), the resize compass layout with the
 * yaw facing hint, and click-time re-checks routing to the editor calls.
 *
 * @since HuskClaimsPlus 1.0.0
 */
@DisplayName("Stepper, Create Claim and Resize menus (shared model)")
public class StepperMenuTests {

    private GuiTestKit kit;
    private GuiTestKit.FakeOnlineUser user;
    private Claim claim;
    private ClaimRef ref;

    @BeforeEach
    public void setUp() {
        kit = new GuiTestKit();
        user = kit.user();
        // The create/resize preflights consult the real registered commands
        kit.command("claim");
        kit.command("extendclaim");
        kit.command("reduceclaim");
        // The default fixture position is far away (no claim world): put the
        // user in a claim world so the WORLD blocker does not dominate
        kit.claimWorlds.computeIfAbsent(GuiTestKit.WORLD.getName(),
                k -> net.william278.huskclaims.claim.ClaimWorld.convert(
                        new java.util.HashSet<>(), Map.of(), java.util.Set.of()));
        claim = kit.claimAt(0, 0, 10, 10);
        ref = ClaimRef.of("world", claim);
        // A balance the user can afford a mid-size claim with
        kit.savedUser(user.getUuid(), "Tester").user();
        kit.database.byUuid.get(user.getUuid()).setClaimBlocks(10_000);
    }

    /* Stepper base ----------------------------------------------------------- */

    @Test
    @DisplayName("Stepper base: shift ×5 clamps to the bound instead of doing nothing")
    public void testShiftClamp() {
        final CreateClaimMenu menu = new CreateClaimMenu(kit.controller, user);
        // Jump to the max first, then shift-click the smallest plus: ×5 would
        // overshoot by 4, so the value must land ON the max
        menu.onClick(user, 25, ClickKind.LEFT); // MAX jump
        final int max = menu.model.max();
        menu.onClick(user, 14, ClickKind.SHIFT_LEFT); // +1 ×5 from the max
        assertEquals(max, menu.model.value());
        // And a normal click at the max does not move the value
        menu.onClick(user, 14, ClickKind.LEFT);
        assertEquals(max, menu.model.value());
    }

    @Test
    @DisplayName("Stepper base: MIN/MAX jumps refresh the view")
    public void testMinMaxRefresh() {
        final CreateClaimMenu menu = new CreateClaimMenu(kit.controller, user);
        menu.onClick(user, 19, ClickKind.LEFT); // MIN
        assertEquals(menu.model.min(), menu.model.value());
        assertEquals(1, kit.host.refreshed.size());
        menu.onClick(user, 25, ClickKind.LEFT); // MAX
        assertEquals(menu.model.max(), menu.model.value());
        assertEquals(2, kit.host.refreshed.size());
    }

    @Test
    @DisplayName("Stepper base: steps that would overshoot render disabled (light gray)")
    public void testDisabledStepRender() {
        final CreateClaimMenu menu = new CreateClaimMenu(kit.controller, user);
        menu.onClick(user, 25, ClickKind.LEFT); // sit at the max
        final Map<Integer, MenuIcon> icons = menu.icons();
        assertEquals("minecraft:light_gray_stained_glass_pane", icons.get(14).itemKey());
        // the nearest minus is still enabled (red)
        assertEquals("minecraft:red_stained_glass_pane", icons.get(12).itemKey());
        // at the max the biggest plus panes are gray too
        assertEquals("minecraft:light_gray_stained_glass_pane", icons.get(16).itemKey());
    }

    @Test
    @DisplayName("Stepper base: Confirm calls the flow exactly once per click")
    public void testConfirmRoutes() {
        final CreateClaimMenu menu = new CreateClaimMenu(kit.controller, user);
        final Map<Integer, MenuIcon> icons = menu.icons();
        assertNotNull(icons.get(40));
        menu.onClick(user, 40, ClickKind.LEFT);
        assertEquals(1, kit.createdClaims.size(), "one userCreateClaim dispatch");
    }

    /* Create Claim ------------------------------------------------------------ */

    @Test
    @DisplayName("Create: renders the full stepper layout with value/lore keys")
    public void testCreateRender() {
        final CreateClaimMenu menu = new CreateClaimMenu(kit.controller, user);
        assertEquals("create_claim", menu.typeName());
        assertEquals(5, menu.rows());
        final Map<Integer, MenuIcon> icons = menu.icons();
        assertEquals("minecraft:grass_block", icons.get(13).itemKey());
        assertEquals(GuiTestKit.name("gui_create_radius", "5"), icons.get(13).name());
        assertEquals("minecraft:oak_sign", icons.get(4).itemKey());
        assertEquals("minecraft:iron_shovel", icons.get(19).itemKey());
        assertEquals("minecraft:diamond_shovel", icons.get(25).itemKey());
        assertEquals("minecraft:arrow", icons.get(36).itemKey());
        // Enabled at radius 5 with a fat balance: lime concrete confirm
        assertEquals("minecraft:lime_concrete", icons.get(40).itemKey());
    }

    @Test
    @DisplayName("Create: without the claim permission the bypass path refuses to dispatch")
    public void testCreatePermissionPreflightRefusal() {
        final NoPermissionUser denied = new NoPermissionUser(kit.plugin);
        final CreateClaimMenu menu = new CreateClaimMenu(kit.controller, denied);
        // The confirm renders the PERMISSION blocker
        assertEquals("minecraft:red_concrete", menu.icons().get(40).itemKey());
        assertEquals(GuiTestKit.name("gui_create_blocked_permission"), menu.icons().get(40).name());
        // And the click refuses: no editor call, just a re-render
        menu.onClick(denied, 40, ClickKind.LEFT);
        assertTrue(kit.createdClaims.isEmpty(), "bypass path must not dispatch without permission");
        assertEquals(1, kit.host.refreshed.size());
    }

    @Test
    @DisplayName("Create: not enough claim blocks renders the FUNDS blocker")
    public void testCreateFundsBlocker() {
        kit.database.byUuid.get(user.getUuid()).setClaimBlocks(20); // afford only radius 2
        final CreateClaimMenu menu = new CreateClaimMenu(kit.controller, user);
        // Max stays >= min (a size the player can't afford stays visible)
        assertTrue(menu.model.max() >= menu.model.min());
        // At the opening radius 5, confirm shows the funds blocker
        assertEquals("minecraft:red_concrete", menu.icons().get(40).itemKey());
        assertEquals(GuiTestKit.name("gui_create_blocked_funds"), menu.icons().get(40).name());
    }

    @Test
    @DisplayName("Create: an overlapping region renders the OVERLAP blocker")
    public void testCreateOverlapBlocker() {
        // Stand inside the existing claim
        user.position = net.william278.huskclaims.position.Position.at(5, 64, 5, GuiTestKit.WORLD);
        final CreateClaimMenu menu = new CreateClaimMenu(kit.controller, user);
        assertEquals("minecraft:red_concrete", menu.icons().get(40).itemKey());
        assertEquals(GuiTestKit.name("gui_create_blocked_overlap"), menu.icons().get(40).name());
    }

    /* Resize picker ----------------------------------------------------------- */

    @Test
    @DisplayName("Resize: compass layout renders with the facing hint from yaw")
    public void testResizeRender() {
        final ResizeMenu menu = new ResizeMenu(kit.controller, ref, user);
        assertEquals("resize", menu.typeName());
        assertEquals(5, menu.rows());
        final Map<Integer, MenuIcon> icons = menu.icons();
        assertEquals("minecraft:grass_block", icons.get(4).itemKey());
        assertEquals("minecraft:arrow", icons.get(13).itemKey()); // NORTH
        assertEquals("minecraft:target", icons.get(22).itemKey()); // ALL
        assertEquals("minecraft:arrow", icons.get(36).itemKey()); // Back
        // Default yaw 0 = facing SOUTH: that edge carries the facing line
        final String facing = GuiTestKit.lore("gui_resize_facing");
        assertTrue(icons.get(31).lore().contains(facing), "south edge shows the facing hint");
        assertFalse(icons.get(13).lore().contains(facing));
    }

    @Test
    @DisplayName("Resize: locked barrier without the extend/reduce permissions")
    public void testResizeLocked() {
        final NoPermissionUser denied = new NoPermissionUser(kit.plugin);
        final ResizeMenu menu = new ResizeMenu(kit.controller, ref, denied);
        final Map<Integer, MenuIcon> icons = menu.icons();
        assertEquals("minecraft:barrier", icons.get(4).itemKey());
        assertEquals(2, icons.size());
    }

    @Test
    @DisplayName("Resize: an edge click opens the stepper for that direction")
    public void testResizeEdgeOpensStepper() {
        final ResizeMenu menu = new ResizeMenu(kit.controller, ref, user);
        menu.onClick(user, 13, ClickKind.LEFT); // NORTH
        assertEquals(1, kit.host.opened.size());
        final MenuView stepper = kit.host.last();
        assertEquals("resize_step", stepper.typeName());
        assertTrue(stepper.title().isPresent());
        assertTrue(stepper.title().get().contains("gui_resize_direction_name_north"));
    }

    @Test
    @DisplayName("Resize: Back returns to Claim Settings")
    public void testResizeBack() {
        final ResizeMenu menu = new ResizeMenu(kit.controller, ref, user);
        menu.onClick(user, 36, ClickKind.LEFT);
        assertEquals("claim_settings", kit.host.last().typeName());
    }

    /* Resize stepper ----------------------------------------------------------- */

    private ResizeStepperMenu stepper(ResizeDirection dir) {
        return new ResizeStepperMenu(kit.controller, user, ref, dir);
    }

    @Test
    @DisplayName("Resize stepper: bounds from balance; positive value dispatches userResizeClaim")
    public void testResizeDispatch() {
        final ResizeStepperMenu menu = stepper(ResizeDirection.EAST);
        assertEquals(0, menu.model.value());
        assertEquals("resize_step", menu.typeName());
        // +1: enable and click confirm
        menu.onClick(user, 14, ClickKind.LEFT); // +1
        assertEquals(1, menu.model.value());
        menu.onClick(user, 40, ClickKind.LEFT); // confirm
        assertEquals(1, kit.resizedClaims.size(), "one userResizeClaim dispatch");
        final GuiTestKit.EditorCall call = kit.resizedClaims.get(0);
        assertEquals(user.getUuid(), call.user().getUuid());
        // East edge +1: the new far corner X moves out by one block
        assertEquals(11, call.region().getFarCorner().getBlockX());
        assertEquals(10, call.region().getFarCorner().getBlockZ());
        assertEquals(0, call.region().getNearCorner().getBlockX());
        assertEquals(0, call.region().getNearCorner().getBlockZ());
    }

    @Test
    @DisplayName("Resize stepper: value 0 does not dispatch")
    public void testResizeZeroNoDispatch() {
        final ResizeStepperMenu menu = stepper(ResizeDirection.NORTH);
        assertEquals("minecraft:light_gray_concrete", menu.icons().get(40).itemKey());
        menu.onClick(user, 40, ClickKind.LEFT);
        assertTrue(kit.resizedClaims.isEmpty());
        assertEquals(1, kit.host.refreshed.size());
    }

    @Test
    @DisplayName("Resize stepper: child claim dispatches userResizeChildClaim with the parentRef guard")
    public void testResizeChildDispatch() {
        final Claim child = claim.createAndAddChild(
                net.william278.huskclaims.claim.Region.from(
                        net.william278.huskclaims.claim.Region.Point.at(2, 2),
                        net.william278.huskclaims.claim.Region.Point.at(6, 6)),
                kit.plugin);
        final ClaimRef childRef = ClaimRef.of("world", child);
        final ResizeStepperMenu menu =
                new ResizeStepperMenu(kit.controller, user, childRef, ResizeDirection.NORTH);
        menu.onClick(user, 14, ClickKind.LEFT); // +1 north
        menu.onClick(user, 40, ClickKind.LEFT); // confirm
        assertEquals(1, kit.resizedChildren.size(), "child resizes go through userResizeChildClaim");
        assertTrue(kit.resizedClaims.isEmpty());
        // North edge pulled/ pushed: near corner Z moves by the value
        assertEquals(1, kit.resizedChildren.get(0).region().getNearCorner().getBlockZ());
    }

    @Test
    @DisplayName("Resize stepper: claim gone while open redirects instead of acting")
    public void testResizeClaimGone() {
        final ResizeStepperMenu menu = stepper(ResizeDirection.EAST);
        menu.onClick(user, 14, ClickKind.LEFT); // +1
        kit.claimWorlds.clear(); // the claim no longer resolves
        menu.onClick(user, 40, ClickKind.LEFT); // confirm
        assertTrue(kit.resizedClaims.isEmpty(), "a gone claim must not be resized");
        assertEquals("claim_list", kit.host.last().typeName());
    }

    @Test
    @DisplayName("Resize stepper: growth needs extendclaim, shrink needs reduceclaim (click-time)")
    public void testResizeDirectionPermission() {
        // The kit user holds both; a user with only reduceclaim must not grow
        final ReduceOnlyUser u = new ReduceOnlyUser(kit.plugin);
        kit.savedUser(u.getUuid(), "Reducer");
        final ResizeStepperMenu menu = new ResizeStepperMenu(kit.controller, u, ref, ResizeDirection.EAST);
        menu.onClick(u, 14, ClickKind.LEFT); // +1 (growth)
        menu.onClick(u, 40, ClickKind.LEFT); // confirm
        assertTrue(kit.resizedClaims.isEmpty(), "growth must be denied without extendclaim");
        // Shrink is allowed with reduceclaim
        final ResizeStepperMenu shrink = new ResizeStepperMenu(kit.controller, u, ref, ResizeDirection.EAST);
        shrink.onClick(u, 12, ClickKind.LEFT); // −1 (shrink)
        shrink.onClick(u, 40, ClickKind.LEFT); // confirm
        assertEquals(1, kit.resizedClaims.size());
    }

    /** A viewer the registered commands refuse (no permissions at all). */
    private static final class NoPermissionUser extends GuiTestKit.FakeOnlineUser {
        NoPermissionUser(@NotNull HuskClaims plugin) {
            super(plugin);
        }

        @Override
        public boolean hasPermission(@NotNull String permission) {
            return false;
        }
    }

    /** A viewer who may shrink but not grow (reduceclaim only). */
    private static final class ReduceOnlyUser extends GuiTestKit.FakeOnlineUser {
        ReduceOnlyUser(@NotNull HuskClaims plugin) {
            super(plugin);
        }

        @Override
        public boolean hasPermission(@NotNull String permission) {
            return !permission.contains("extendclaim");
        }
    }

}
