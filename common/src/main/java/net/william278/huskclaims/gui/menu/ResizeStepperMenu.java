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
import net.william278.huskclaims.claim.ClaimSizeMath;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.IconBuilder;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Resize stepper: a signed-distance stepper for one chosen edge (or all
 * sides) built on the shared {@link StepperView} base. Negative values pull
 * the edge in, positive push it out. Bounds come from {@link ClaimSizeMath}
 * edge/uniform helpers (CAP balance mode, as create). Confirm executes
 * {@code userResizeClaim}/{@code userResizeChildClaim} with the
 * {@code parentRef} resolve-guard kept. The editor still re-checks
 * size/balance/overlap and sends its own chat feedback.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class ResizeStepperMenu extends StepperView {

    @NotNull
    private final ClaimRef ref;
    @NotNull
    private final ResizeDirection direction;
    @NotNull
    private final OnlineUser opener;
    private final long balance;
    private final int width;   // claim X extent at open time
    private final int depth;   // claim Z extent at open time
    private final int minimumSize;

    /**
     * Why the growth cap is what it is: the locale key whose lore names the
     * binding limit for the current claim type / direction.
     */
    private final String limitKey;

    public ResizeStepperMenu(@NotNull GuiController c, @NotNull OnlineUser opener,
                             @NotNull ClaimRef ref, @NotNull ResizeDirection direction) {
        super(c, 0,
                minOf(c, ref, direction),
                maxOf(c, ref, direction, opener),
                c.getPlugin().getSettings().getGui().getCreateClaim().getSteps(),
                u -> c.openResizeMenu(u, ref));
        this.ref = ref;
        this.direction = direction;
        this.opener = opener;
        final Settings.ClaimSettings cs = c.getPlugin().getSettings().getClaims();
        this.minimumSize = cs.getMinimumClaimSize();
        final Region region = ref.resolve(c.getPlugin()).map(r -> r.claim().getRegion()).orElse(null);
        this.width = region == null ? 0 : Math.abs(region.getFarCorner().getBlockX()
                - region.getNearCorner().getBlockX()) + 1;
        this.depth = region == null ? 0 : Math.abs(region.getFarCorner().getBlockZ()
                - region.getNearCorner().getBlockZ()) + 1;
        this.balance = c.getPlugin().getCachedClaimBlocks(opener);
        this.limitKey = growthBoundOf(c, ref, direction, opener).key();
    }

    // Bounds (static so they can feed the super constructor) --------------------

    private static int minOf(@NotNull GuiController c, @NotNull ClaimRef ref,
                             @NotNull ResizeDirection dir) {
        final Region region = ref.resolve(c.getPlugin())
                .map(r -> r.claim().getRegion()).orElse(null);
        final int minSize = c.getPlugin().getSettings().getClaims().getMinimumClaimSize();
        if (region == null) {
            return 0;
        }
        if (dir == ResizeDirection.ALL) {
            return -ClaimSizeMath.maxUniformShrink(width(region), depth(region), minSize);
        }
        final int length = dir.isZAxis() ? depth(region) : width(region);
        return -ClaimSizeMath.maxEdgeShrink(length, minSize);
    }

    private static int maxOf(@NotNull GuiController c, @NotNull ClaimRef ref,
                             @NotNull ResizeDirection dir, @NotNull OnlineUser opener) {
        return growthBoundOf(c, ref, dir, opener).value();
    }

    /**
     * Growth bound + the reason it bound. Mirrors exactly what
     * {@code ClaimEditor.userResizeClaim}/{@code userResizeChildClaim} enforce:
     * the action-size cap ({@code maximumClaimActionSize}, checked for ALL
     * claim types; the editor never checks maximumClaimRadius on resize), the
     * block balance (owned top-level only), and parent enclosure (children
     * only — they cost no blocks).
     */
    private record GrowthBound(int value, @NotNull String key) {
    }

    @NotNull
    private static GrowthBound growthBoundOf(@NotNull GuiController c, @NotNull ClaimRef ref,
                                             @NotNull ResizeDirection dir, @NotNull OnlineUser opener) {
        final ClaimRef.Resolved resolved = ref.resolve(c.getPlugin()).orElse(null);
        if (resolved == null) {
            return new GrowthBound(0, "gui_limit_server");
        }
        final Claim claim = resolved.claim();
        final Region region = claim.getRegion();
        final long actionSize = c.getPlugin().getSettings().getClaims().getMaximumClaimActionSize();
        final int w = width(region), d = depth(region);
        final int actionCapEdge = actionSize <= 0 ? 10_000
                : (int) Math.min(10_000L, actionSize / Math.max(1, dir.isZAxis() ? w : d));

        // Child claims cost nothing: bounded by the parent's edges (and the action cap)
        if (claim.getParent().isPresent()) {
            final Region p = claim.getParent().get().getRegion(), cl = region;
            final int north = cl.getNearCorner().getBlockZ() - p.getNearCorner().getBlockZ();
            final int south = p.getFarCorner().getBlockZ() - cl.getFarCorner().getBlockZ();
            final int west = cl.getNearCorner().getBlockX() - p.getNearCorner().getBlockX();
            final int east = p.getFarCorner().getBlockX() - cl.getFarCorner().getBlockX();
            final int parentRoom = dir == ResizeDirection.ALL
                    ? Math.min(Math.min(north, south), Math.min(west, east))
                    : switch (dir) {
                        case NORTH -> north;
                        case SOUTH -> south;
                        case WEST -> west;
                        case EAST -> east;
                        default -> Integer.MAX_VALUE; // unreachable: ALL handled above
                    };
            final int actionCap = dir == ResizeDirection.ALL
                    ? ClaimSizeMath.maxUniformGrowth(w, d, effectiveActionBlocks(actionSize))
                    : actionCapEdge;
            return actionCap < parentRoom
                    ? new GrowthBound(actionCap, "gui_limit_server")
                    : new GrowthBound(Math.max(0, parentRoom), "gui_limit_parent");
        }

        // Admin claims: no balance, no min-size check in the editor — action cap only
        if (claim.isAdminClaim()) {
            return new GrowthBound(dir == ResizeDirection.ALL
                    ? ClaimSizeMath.maxUniformGrowth(w, d, effectiveActionBlocks(actionSize))
                    : actionCapEdge, "gui_limit_server");
        }

        // Owned top-level: min(balance bound, action cap) — remember which bound
        final long balance = c.getPlugin().getCachedClaimBlocks(opener);
        final int balanceCap = dir == ResizeDirection.ALL
                ? ClaimSizeMath.maxUniformGrowth(w, d, balance)
                : ClaimSizeMath.maxEdgeGrowth(balance, dir.isZAxis() ? w : d);
        final int actionCap = dir == ResizeDirection.ALL
                ? ClaimSizeMath.maxUniformGrowth(w, d, effectiveActionBlocks(actionSize))
                : actionCapEdge;
        return balanceCap <= actionCap
                ? new GrowthBound(balanceCap, "gui_limit_balance")
                : new GrowthBound(actionCap, "gui_limit_server");
    }

    /** The action-size cap as a block budget for the uniform-growth helpers. */
    private static long effectiveActionBlocks(long actionSize) {
        return actionSize <= 0 ? Long.MAX_VALUE / 4 : actionSize;
    }

    private static int width(@NotNull Region region) {
        return Math.abs(region.getFarCorner().getBlockX() - region.getNearCorner().getBlockX()) + 1;
    }

    private static int depth(@NotNull Region region) {
        return Math.abs(region.getFarCorner().getBlockZ() - region.getNearCorner().getBlockZ()) + 1;
    }

    // Menu identity -------------------------------------------------------------

    @NotNull
    @Override
    public String typeName() {
        return "resize_step";
    }

    @NotNull
    @Override
    public Optional<String> title() {
        return Optional.of(c.text("gui_resize_step_title", edgeName()));
    }

    /** Human-facing edge name ("north", "all", …) for the dynamic title. */
    private String edgeName() {
        return c.text("gui_resize_direction_name_" + direction.localeSuffix());
    }

    // Item rendering -------------------------------------------------------------

    @NotNull
    @Override
    protected IconBuilder stepText(@NotNull IconBuilder icon, int delta, boolean enabled) {
        final int n = Math.abs(delta);
        final String nStr = Integer.toString(n);
        if (enabled) {
            return icon.name(delta < 0 ? "gui_resize_step_minus" : "gui_resize_step_plus", nStr)
                    .lore(delta < 0 ? "gui_resize_step_minus_lore" : "gui_resize_step_plus_lore",
                            nStr, Integer.toString(n * 5));
        }
        if (delta < 0) {
            return icon.name("gui_resize_step_minus_off", nStr)
                    .lore("gui_resize_step_minus_off_lore", Integer.toString(minimumSize));
        }
        return icon.name("gui_resize_step_plus_off", nStr)
                .lore("gui_resize_step_plus_off_lore", growthLimitReason());
    }

    /**
     * Why no further growth is allowed: the remembered bound for this claim
     * type / direction — balance, the server action-size cap, or the parent's
     * edge for child claims.
     */
    private String growthLimitReason() {
        final long actionSize = c.getPlugin().getSettings().getClaims().getMaximumClaimActionSize();
        return switch (limitKey) {
            case "gui_limit_balance" -> c.text("gui_limit_balance", Long.toString(balance));
            case "gui_limit_parent" -> c.text("gui_limit_parent");
            default -> c.text("gui_limit_server", Long.toString(Math.max(0, actionSize)));
        };
    }

    /** New (w, d) if the current value were applied. */
    private long[] newSize() {
        final int v = model.value();
        final long w = width + (direction.isXAxis() ? v : direction == ResizeDirection.ALL ? 2L * v : 0);
        final long d = depth + (direction.isZAxis() ? v : direction == ResizeDirection.ALL ? 2L * v : 0);
        return new long[]{Math.max(w, 1), Math.max(d, 1)};
    }

    /** Lore args for the value/confirm items: w', d', area', balance, after. */
    private String[] sizeLoreArgs() {
        final long[] s = newSize();
        final long area = s[0] * s[1];
        final long oldArea = (long) width * depth;
        return new String[]{Long.toString(s[0]), Long.toString(s[1]), Long.toString(area),
                Long.toString(balance), Long.toString(balance - (area - oldArea))};
    }

    @NotNull
    @Override
    protected MenuIcon valueIcon() {
        final int v = model.value();
        final String mat = v > 0 ? "minecraft:piston"
                : v < 0 ? "minecraft:sticky_piston" : "minecraft:light_gray_dye";
        return c.item(mat).amount(Math.max(Math.abs(v), 1))
                .name(v > 0 ? "gui_resize_value_out"
                        : v < 0 ? "gui_resize_value_in" : "gui_resize_value_none",
                        Integer.toString(Math.abs(v)))
                .lore("gui_resize_value_lore", sizeLoreArgs())
                .build();
    }

    @NotNull
    @Override
    protected MenuIcon infoIcon() {
        return c.item("minecraft:oak_sign")
                .name("gui_resize_title")
                .lore("gui_resize_info_lore",
                        Integer.toString(width), Integer.toString(depth),
                        Long.toString((long) width * depth))
                .build();
    }

    @NotNull
    @Override
    protected MenuIcon minIcon() {
        return c.item("minecraft:iron_shovel")
                .name("gui_resize_min")
                .lore("gui_resize_jump_lore", Integer.toString(model.min()))
                .build();
    }

    @NotNull
    @Override
    protected MenuIcon maxIcon() {
        return c.item("minecraft:diamond_shovel")
                .name("gui_resize_max")
                .lore("gui_resize_jump_lore", Integer.toString(model.max()))
                .build();
    }

    @NotNull
    @Override
    protected MenuIcon confirmIcon() {
        if (model.value() == 0) {
            return c.item("minecraft:light_gray_concrete")
                    .name("gui_resize_confirm_none")
                    .lore("gui_resize_confirm_none_lore")
                    .build();
        }
        final String[] a = sizeLoreArgs();
        return c.item("minecraft:lime_concrete")
                .name("gui_resize_confirm")
                .lore("gui_resize_confirm_lore",
                        Integer.toString(width), Integer.toString(depth), a[0], a[1])
                .build();
    }

    @NotNull
    @Override
    protected String backLoreKey() {
        return "gui_back_lore_settings";
    }

    // Execution -----------------------------------------------------------------

    @Override
    protected void onConfirm(@NotNull OnlineUser user) {
        final int v = model.value();
        if (v == 0) {
            c.host().refresh(user, this);
            return;
        }
        // Never trust the render — gate growth on extendclaim, shrink on
        // reduceclaim, and require claim ownership, at confirm time
        final String needed = v > 0 ? "extendclaim" : "reduceclaim";
        if (!c.mayUse(user, needed)) {
            c.getPlugin().getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            c.host().refresh(user, this);
            return;
        }
        final ClaimRef.Resolved resolved = ref.resolve(c.getPlugin()).orElse(null);
        if (resolved == null) {
            c.claimGone(user, ref);
            return;
        }
        if (!c.mayManageSettings(user, resolved.claim())) {
            c.getPlugin().getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            c.host().refresh(user, this);
            return;
        }
        final Claim claim = resolved.claim();
        // Region.getResized(north, south, east, west) — the call convention the
        // extend/reduce commands use; the upstream parameter names do not match it.
        final Region newRegion = switch (direction) {
            case NORTH -> claim.getRegion().getResized(v, 0, 0, 0);
            case SOUTH -> claim.getRegion().getResized(0, v, 0, 0);
            case EAST -> claim.getRegion().getResized(0, 0, v, 0);
            case WEST -> claim.getRegion().getResized(0, 0, 0, v);
            case ALL -> claim.getRegion().getResized(v, v, v, v);
        };
        if (newRegion.getShortestEdge() < minimumSize) {
            c.host().refresh(user, this); // bounds should prevent this; re-render anyway
            return;
        }
        c.host().close(user);
        final boolean isChild = claim.getParent().isPresent();
        c.getPlugin().runAsync(() -> {
            if (isChild) {
                // Keep the parentRef resolve-guard
                final ClaimRef parentRef = ClaimRef.of(resolved.worldName(),
                        claim.getParent().orElseThrow());
                parentRef.resolve(c.getPlugin()).ifPresent(parent ->
                        c.getPlugin().userResizeChildClaim(user, resolved.world(), claim, newRegion));
            } else {
                c.getPlugin().userResizeClaim(user, resolved.world(), claim, newRegion);
            }
        });
    }

}
