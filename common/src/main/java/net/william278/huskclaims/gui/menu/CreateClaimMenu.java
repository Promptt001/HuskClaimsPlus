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

import net.william278.huskclaims.claim.ClaimSizeMath;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.IconBuilder;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Create Claim stepper: a mirror-symmetric radius row around a stack-value
 * grass block, live cost/balance lore on every step, MIN/MAX jumps with their
 * limiting reason, and a Confirm that renders disabled states WITH reasons from
 * one {@link #blocker} evaluation (render and click share it; the click
 * re-evaluates — never trust the render).
 *
 * <p>Balance mode CAP: the stepper never exceeds the player's
 * claim-block balance — the max is {@code min(hardMax, maxRadiusFor(balance))}.
 * The {@code gui.create_claim.bypass_tool_requirement} default-true option
 * lets creating from the menu work without the claim shovel (players opening a
 * menu rarely hold one): the bypassed path preflights
 * permission/world/radius/overlap itself and calls
 * {@code userCreateClaim} directly. The {@code claim} command permission is
 * always preflighted, including on the bypass path.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class CreateClaimMenu extends StepperView {

    private final long balance;
    private final boolean bypassTool;
    @NotNull
    private final OnlineUser opener;

    public CreateClaimMenu(@NotNull GuiController c, @NotNull OnlineUser user) {
        super(c, 5,
                ClaimSizeMath.minRadius(c.getPlugin().getSettings().getClaims().getMinimumClaimSize()),
                maxRadius(c, user),
                c.getPlugin().getSettings().getGui().getCreateClaim().getSteps(),
                u -> c.openClaimList(u, null));
        this.opener = user;
        this.balance = c.getPlugin().getCachedClaimBlocks(user);
        this.bypassTool = c.getPlugin().getSettings().getGui().getCreateClaim().isBypassToolRequirement();
    }

    private static int maxRadius(@NotNull GuiController c, @NotNull OnlineUser user) {
        final Settings.ClaimSettings cs = c.getPlugin().getSettings().getClaims();
        final int min = ClaimSizeMath.minRadius(cs.getMinimumClaimSize());
        final int hard = ClaimSizeMath.hardMaxRadius(cs.getMaximumClaimRadius(), cs.getMaximumClaimActionSize());
        final long balance = c.getPlugin().getCachedClaimBlocks(user);
        // Keep a size the player can't afford visible; the FUNDS blocker renders
        // "Not enough claim blocks" on Confirm instead of hiding it.
        return Math.max(min, Math.min(hard, ClaimSizeMath.maxRadiusFor(balance)));
    }

    @NotNull
    @Override
    public String typeName() {
        return "create_claim";
    }

    /** Why Confirm is disabled (render AND click use this). */
    private enum Blocker { PERMISSION, WORLD, FUNDS, OVERLAP, TOOL }

    /**
     * One place that decides whether Confirm is enabled and why. Render and
     * click-time re-check both call it (never trust the render).
     */
    @NotNull
    private Optional<Blocker> blocker(@NotNull OnlineUser user) {
        // The GUI must not bypass the claim command permission
        if (!c.mayUse(user, "claim")) {
            return Optional.of(Blocker.PERMISSION);
        }
        if (c.getPlugin().getClaimWorld(user.getWorld()).isEmpty()) {
            return Optional.of(Blocker.WORLD);
        }
        if (ClaimSizeMath.area(model.value()) > balance) {
            return Optional.of(Blocker.FUNDS);
        }
        final Optional<ClaimWorld> world = c.getPlugin().getClaimWorld(user.getWorld());
        if (world.isPresent() && world.get().isRegionClaimed(Region.around(user.getPosition(), model.value()))) {
            return Optional.of(Blocker.OVERLAP);
        }
        final Settings.ClaimSettings cs = c.getPlugin().getSettings().getClaims();
        if (!bypassTool && cs.isRequireToolForCommands() && !user.isHolding(cs.getClaimToolData())) {
            return Optional.of(Blocker.TOOL);
        }
        return Optional.empty();
    }

    /* Item rendering -------------------------------------------------------- */

    @NotNull
    @Override
    protected IconBuilder stepText(@NotNull IconBuilder icon, int delta, boolean enabled) {
        final int n = Math.abs(delta);
        final int target = model.value() + delta;
        final long side = ClaimSizeMath.side(target);
        final long area = ClaimSizeMath.area(target);
        final String nStr = Integer.toString(n);
        if (enabled) {
            return icon.name(delta < 0 ? "gui_create_step_minus" : "gui_create_step_plus", nStr)
                    .lore(delta < 0 ? "gui_create_step_minus_lore" : "gui_create_step_plus_lore",
                            nStr, Long.toString(side), Long.toString(area), Integer.toString(n * 5));
        }
        if (delta < 0) {
            return icon.name("gui_create_step_minus_off", nStr)
                    .lore("gui_create_step_minus_off_lore", Integer.toString(model.min()));
        }
        final long hard = hardMax();
        final boolean balanceBound = ClaimSizeMath.maxRadiusFor(balance) < hard;
        final String reason = c.text(balanceBound ? "gui_limit_balance" : "gui_limit_server",
                balanceBound ? Long.toString(balance) : Long.toString(hard));
        return icon.name("gui_create_step_plus_off", nStr)
                .loreRaw(java.util.List.of(reason));
    }

    @NotNull
    @Override
    protected MenuIcon valueIcon() {
        final int r = model.value();
        final long cost = ClaimSizeMath.area(r);
        return c.item("minecraft:grass_block").amount(Math.max(r, 1))
                .name("gui_create_radius", Integer.toString(r))
                .lore("gui_create_radius_lore", Long.toString(ClaimSizeMath.side(r)),
                        Long.toString(cost), Long.toString(balance), Long.toString(balance - cost))
                .build();
    }

    @NotNull
    @Override
    protected MenuIcon infoIcon() {
        return c.item("minecraft:oak_sign")
                .name("gui_create_info")
                .lore("gui_create_info_lore")
                .build();
    }

    @NotNull
    @Override
    protected MenuIcon minIcon() {
        final int min = model.min();
        return c.item("minecraft:iron_shovel")
                .name("gui_create_min")
                .lore("gui_create_min_lore", Integer.toString(min),
                        Long.toString(ClaimSizeMath.side(min)), Long.toString(ClaimSizeMath.area(min)))
                .build();
    }

    @NotNull
    @Override
    protected MenuIcon maxIcon() {
        final int max = model.max();
        final long hard = hardMax();
        final boolean balanceBound = ClaimSizeMath.maxRadiusFor(balance) < hard;
        return c.item("minecraft:diamond_shovel")
                .name("gui_create_max")
                .lore("gui_create_max_lore", Integer.toString(max),
                        Long.toString(ClaimSizeMath.side(max)), Long.toString(ClaimSizeMath.area(max)),
                        balanceBound
                                ? c.text("gui_limit_balance", Long.toString(balance))
                                : c.text("gui_limit_server", Long.toString(hard)))
                .build();
    }

    @NotNull
    @Override
    protected MenuIcon confirmIcon() {
        final Optional<Blocker> b = blocker(opener);
        final String mat = switch (b.orElse(null)) {
            case null -> "minecraft:lime_concrete";
            case OVERLAP, FUNDS, PERMISSION -> "minecraft:red_concrete";
            case WORLD -> "minecraft:barrier";
            case TOOL -> "minecraft:golden_shovel";
        };
        final IconBuilder item = c.item(mat);
        if (b.isEmpty()) {
            final int r = model.value();
            return item.name("gui_create_confirm")
                    .lore("gui_create_confirm_lore", Long.toString(ClaimSizeMath.side(r)),
                            Long.toString(ClaimSizeMath.area(r)))
                    .build();
        }
        return switch (b.get()) {
            case OVERLAP -> item.name("gui_create_blocked_overlap")
                    .lore("gui_create_blocked_overlap_lore").build();
            case FUNDS -> item.name("gui_create_blocked_funds")
                    .lore("gui_create_blocked_funds_lore",
                            Long.toString(ClaimSizeMath.area(model.value())), Long.toString(balance))
                    .build();
            case WORLD -> item.name("gui_create_blocked_world")
                    .lore("gui_create_blocked_world_lore").build();
            case PERMISSION -> item.name("gui_create_blocked_permission")
                    .lore("gui_create_blocked_permission_lore").build();
            case TOOL -> item.name("gui_create_blocked_tool")
                    .lore("gui_create_blocked_tool_lore").build();
        };
    }

    private long hardMax() {
        final Settings.ClaimSettings cs = c.getPlugin().getSettings().getClaims();
        return ClaimSizeMath.hardMaxRadius(cs.getMaximumClaimRadius(), cs.getMaximumClaimActionSize());
    }

    @NotNull
    @Override
    protected String backLoreKey() {
        return "gui_back_lore_claims";
    }

    /* Execution ------------------------------------------------------------- */

    @Override
    protected void onConfirm(@NotNull OnlineUser user) {
        // Re-evaluate every check at click time; never trust the render
        final Optional<Blocker> b = blocker(user);
        if (b.isPresent()) {
            if (b.get() == Blocker.OVERLAP) {
                c.getPlugin().getLocales().getLocale("gui_create_overlap").ifPresent(user::sendMessage);
            }
            c.host().refresh(user, this); // show the reason item
            return;
        }
        final ClaimWorld world = c.getPlugin().getClaimWorld(user.getWorld()).orElseThrow();
        final Region region = Region.around(user.getPosition(), model.value());
        c.host().close(user);
        if (bypassTool || !c.getPlugin().getSettings().getClaims().isRequireToolForCommands()
                || user.isHolding(c.getPlugin().getSettings().getClaims().getClaimToolData())) {
            // Bypassed path: blocker() above IS the preflight; create directly
            c.getPlugin().runAsync(() -> c.getPlugin().userCreateClaim(user, world, region));
            return;
        }
        // Tool required and not held: the real command path enforces everything
        c.executeCommand(user, "claim", new String[]{Integer.toString(model.value())});
    }

}
