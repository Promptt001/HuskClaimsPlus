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

import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.gui.ClaimNames;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.IconBuilder;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resize edge picker: a compass layout (N, W-ALL-E, S) around a claim-info
 * item. Clicking an edge opens the {@link ResizeStepperMenu}, a signed-distance
 * stepper on the shared {@link StepperView} base. The resize itself goes
 * through {@code userResizeClaim}/{@code userResizeChildClaim} with the
 * {@code parentRef} guard kept, never through {@code /extendclaim} (its
 * direction comes from the player's yaw — meaningless for a remote claim).
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class ResizeMenu implements MenuView {

    // Compass layout, code-defined
    private static final int INFO_SLOT = 4;
    private static final int NORTH_SLOT = 13;
    private static final int WEST_SLOT = 21;
    private static final int ALL_SLOT = 22;
    private static final int EAST_SLOT = 23;
    private static final int SOUTH_SLOT = 31;
    private static final int BACK_SLOT = 36;

    private final GuiController c;
    @NotNull
    private final ClaimRef ref;
    @Nullable
    private final OnlineUser viewer;

    /**
     * @param viewer the player the menu is opened for, when known; used only
     *               for render-time state (locked edges, facing hint) — every
     *               click re-resolves the claim and re-checks permissions
     */
    public ResizeMenu(@NotNull GuiController c, @NotNull ClaimRef ref, @Nullable OnlineUser viewer) {
        this.c = c;
        this.ref = ref;
        this.viewer = viewer;
    }

    /**
     * Whether the viewer holds the resize command permissions: growth needs
     * {@code extendclaim}, shrink needs {@code reduceclaim}. One of the two is
     * enough to open the stepper (the stepper re-gates per direction at confirm).
     */
    public static boolean mayResize(@NotNull GuiController c, @NotNull OnlineUser user) {
        return c.mayUse(user, "extendclaim") || c.mayUse(user, "reduceclaim");
    }

    @NotNull
    @Override
    public String typeName() {
        return "resize";
    }

    @Override
    public int rows() {
        return 5;
    }

    @NotNull
    @Override
    public Map<Integer, MenuIcon> icons() {
        final Map<Integer, MenuIcon> items = new HashMap<>();
        // Edges render locked when the viewer holds neither extendclaim nor
        // reduceclaim, or may not manage the claim (the stepper re-checks at confirm)
        final boolean mayManage = viewer != null && ref.resolve(c.getPlugin())
                .map(resolved -> c.mayManageSettings(viewer, resolved.claim())).orElse(false);
        if (viewer == null || !mayResize(c, viewer) || !mayManage) {
            items.put(INFO_SLOT, c.item("minecraft:barrier")
                    .name("gui_resize_locked")
                    .lore("gui_resize_locked_lore").build());
            items.put(BACK_SLOT, backIcon());
            return items;
        }
        items.put(INFO_SLOT, infoIcon());
        final ResizeDirection facing = facing(viewer);
        items.put(NORTH_SLOT, edgeIcon(ResizeDirection.NORTH, facing));
        items.put(SOUTH_SLOT, edgeIcon(ResizeDirection.SOUTH, facing));
        items.put(WEST_SLOT, edgeIcon(ResizeDirection.WEST, facing));
        items.put(EAST_SLOT, edgeIcon(ResizeDirection.EAST, facing));
        items.put(ALL_SLOT, edgeIcon(ResizeDirection.ALL, facing));
        items.put(BACK_SLOT, backIcon());
        return items;
    }

    @NotNull
    private MenuIcon backIcon() {
        return c.item("minecraft:arrow").name("gui_back")
                .lore("gui_back_lore_settings").build();
    }

    /** Claim info item: name, current size, "pick an edge". */
    @NotNull
    private MenuIcon infoIcon() {
        return ref.resolve(c.getPlugin()).map(resolved -> {
            final Region region = resolved.claim().getRegion();
            final Region.Point near = region.getNearCorner();
            final Region.Point far = region.getFarCorner();
            return c.item("minecraft:grass_block")
                    .nameLiteral(ClaimNames.displayName(resolved.claim(), resolved.worldName(),
                            c.getPlugin()))
                    .lore("gui_resize_info_lore",
                            widthOf(near, far), depthOf(near, far),
                            Integer.toString(region.getSurfaceArea()))
                    .build();
        }).orElseGet(() -> c.item("minecraft:grass_block")
                .name("gui_resize_title").build());
    }

    /** Edge item with lore: length along the moved axis (+ facing hint). */
    @NotNull
    private MenuIcon edgeIcon(@NotNull ResizeDirection dir, @Nullable ResizeDirection facing) {
        final boolean isAll = dir == ResizeDirection.ALL;
        final IconBuilder item = c.item(isAll ? "minecraft:target" : "minecraft:arrow")
                .name("gui_resize_direction_" + dir.localeSuffix());
        if (isAll) {
            item.lore("gui_resize_direction_all_lore");
        } else {
            final Region region = ref.resolve(c.getPlugin())
                    .map(r -> r.claim().getRegion()).orElse(null);
            final String length = region == null ? "?"
                    : (dir.isZAxis() ? depthOf(region.getNearCorner(), region.getFarCorner())
                    : widthOf(region.getNearCorner(), region.getFarCorner()));
            item.lore("gui_resize_direction_lore", length);
        }
        if (dir == facing) {
            item.loreRaw(List.of(c.text("gui_resize_facing")));
        }
        return item.build();
    }

    /** The edge the player is facing, from yaw (the ExtendClaimCommand bands). */
    @Nullable
    private ResizeDirection facing(@NotNull OnlineUser user) {
        float y = user.getPosition().getYaw() % 360;
        if (y < 0) {
            y += 360.0f;
        }
        if (y >= 315 || y < 45) {
            return ResizeDirection.SOUTH;
        } else if (y < 135) {
            return ResizeDirection.WEST;
        } else if (y < 225) {
            return ResizeDirection.NORTH;
        } else {
            return ResizeDirection.EAST;
        }
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        if (slot == BACK_SLOT) {
            c.openClaimSettings(user, ref);
            return;
        }
        final ResizeDirection dir = switch (slot) {
            case NORTH_SLOT -> ResizeDirection.NORTH;
            case SOUTH_SLOT -> ResizeDirection.SOUTH;
            case WEST_SLOT -> ResizeDirection.WEST;
            case EAST_SLOT -> ResizeDirection.EAST;
            case ALL_SLOT -> ResizeDirection.ALL;
            default -> null;
        };
        if (dir != null) {
            // Never trust the render — re-check command permission and claim
            // ownership at click time, before opening the stepper
            final boolean mayManage = ref.resolve(c.getPlugin())
                    .map(resolved -> c.mayManageSettings(user, resolved.claim()))
                    .orElse(false);
            if (!mayResize(c, user) || !mayManage) {
                c.getPlugin().getLocales().getLocale("gui_error_no_permission")
                        .ifPresent(user::sendMessage);
                c.host().refresh(user, this);
                return;
            }
            c.host().open(user, new ResizeStepperMenu(c, user, ref, dir));
        }
    }

    private static String widthOf(@NotNull Region.Point near, @NotNull Region.Point far) {
        return Integer.toString(Math.abs(far.getBlockX() - near.getBlockX()) + 1);
    }

    private static String depthOf(@NotNull Region.Point near, @NotNull Region.Point far) {
        return Integer.toString(Math.abs(far.getBlockZ() - near.getBlockZ()) + 1);
    }

}
