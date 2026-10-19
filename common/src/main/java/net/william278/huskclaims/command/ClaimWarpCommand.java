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

package net.william278.huskclaims.command;

import net.william278.huskclaims.HuskClaims;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimNaming;
import net.william278.huskclaims.claim.ClaimWarp;
import net.william278.huskclaims.claim.ClaimWarpNames;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.position.Position;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.CommandUser;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Owner-side management of claim warps: {@code /claimwarp set|unset|rename|list}.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public class ClaimWarpCommand extends InClaimCommand implements TabCompletable {

    protected ClaimWarpCommand(@NotNull HuskClaims plugin) {
        super(
                List.of("claimwarp"),
                "[set|unset|rename <name>|list]",
                TrustLevel.Privilege.MANAGE_WARP,
                plugin
        );
    }

    @Override
    public void execute(@NotNull OnlineUser executor, @NotNull ClaimWorld world, @NotNull Claim claim,
                        @NotNull String[] args) {
        // a warp set while standing in a child claim must land on the
        // PARENT (getClaims()/getWarpClaims() only see top-level claims) — and child
        // trust can differ, so re-check MANAGE_WARP on the parent.
        final Claim target = claim.getParent().orElse(claim);
        if (target != claim && !target.isPrivilegeAllowed(TrustLevel.Privilege.MANAGE_WARP, executor, plugin)
                && !hasPermission(executor, "other")) {
            plugin.getLocales().getLocale("no_claim_privilege")
                    .ifPresent(executor::sendMessage);
            return;
        }
        claim = target;
        final String sub = parseStringArg(args, 0)
                .map(a -> a.toLowerCase(Locale.ENGLISH)).orElse("");
        switch (sub) {
            case "set" -> setWarp(executor, world, claim, removeFirstArg(args));
            case "unset" -> unsetWarp(executor, world, claim);
            case "rename" -> renameWarp(executor, world, claim, removeFirstArg(args));
            case "list", "info" -> plugin.getLocales().getLocale("claim_warp_list_hint")
                    .ifPresent(executor::sendMessage);
            default -> plugin.getLocales().getLocale("error_invalid_syntax", getUsage())
                    .ifPresent(executor::sendMessage);
        }
    }

    // Set the claim warp, anchored to the executor's exact standing position
    private void setWarp(@NotNull OnlineUser executor, @NotNull ClaimWorld world, @NotNull Claim claim,
                         @NotNull String[] args) {
        final Optional<String> name = parseStringArg(args, 0);
        if (name.isEmpty()) {
            plugin.getLocales().getLocale("error_invalid_syntax", "/claimwarp set <name>")
                    .ifPresent(executor::sendMessage);
            return;
        }

        // Validate the warp name: strict whitelist — raw names end up in
        // clickable run_command URLs, so anything off the whitelist is rejected)
        final String warpName = name.get();
        if (!ClaimWarpNames.isValid(warpName, warpNameMaxLength())) {
            plugin.getLocales().getLocale("claim_warp_name_invalid", Integer.toString(warpNameMaxLength()))
                    .ifPresent(executor::sendMessage);
            return;
        }

        // Names are unique across every claim world on this server
        if (ClaimWarpNames.isTaken(plugin, claim, warpName)) {
            plugin.getLocales().getLocale("claim_warp_name_taken", warpName)
                    .ifPresent(executor::sendMessage);
            return;
        }

        // The anchor is the executor's position, so it must be in this claim
        final boolean inThisClaim = plugin.getClaimWorld(executor.getWorld()).filter(w -> w == world).isPresent()
                && claim.getRegion().contains(executor.getPosition());
        if (!inThisClaim) {
            plugin.getLocales().getLocale("gui_claim_warp_not_inside").ifPresent(executor::sendMessage);
            return;
        }

        // Anti-trap: validate the anchor position is safe before accepting it
        final ClaimWarp warp = ClaimWarp.of(warpName, executor, executor.getPosition());
        // keep the existing warp's visibility — re-setting a private warp
        // (moving it) must not silently make it public
        if (claim.getWarp() != null) {
            warp.setPubliclyAccessible(claim.getWarp().isPubliclyAccessible());
        }
        final boolean wasAuto = ClaimNaming.isAuto(claim);
        validateSafeAnchor(executor, warp, () ->
                commitWarp(executor, world, claim, warp, false, null, null, wasAuto));
    }

    // Rename the claim warp
    private void renameWarp(@NotNull OnlineUser executor, @NotNull ClaimWorld world, @NotNull Claim claim,
                            @NotNull String[] args) {
        if (claim.getWarp() == null) {
            plugin.getLocales().getLocale("claim_warp_not_set")
                    .ifPresent(executor::sendMessage);
            return;
        }
        final Optional<String> name = parseStringArg(args, 0);
        if (name.isEmpty()) {
            plugin.getLocales().getLocale("error_invalid_syntax", "/claimwarp rename <name>")
                    .ifPresent(executor::sendMessage);
            return;
        }
        if (!ClaimWarpNames.isValid(name.get(), warpNameMaxLength())) {
            plugin.getLocales().getLocale("claim_warp_name_invalid", Integer.toString(warpNameMaxLength()))
                    .ifPresent(executor::sendMessage);
            return;
        }
        if (ClaimWarpNames.isTaken(plugin, claim, name.get())) {
            plugin.getLocales().getLocale("claim_warp_name_taken", name.get())
                    .ifPresent(executor::sendMessage);
            return;
        }
        // Captured before the rename overwrites them; used to keep the claim and warp names in step
        final String oldWarpName = claim.getWarp().getName();
        final String oldClaimName = claim.getMetadata().get(ClaimNaming.NAME_KEY);
        final boolean wasAuto = ClaimNaming.isAuto(claim);
        claim.getWarp().setName(name.get());
        commitWarp(executor, world, claim, claim.getWarp(), true, oldWarpName,
                oldClaimName != null && !oldClaimName.isBlank() ? oldClaimName : null, wasAuto);
    }

    // Remove the claim warp
    private void unsetWarp(@NotNull OnlineUser executor, @NotNull ClaimWorld world, @NotNull Claim claim) {
        if (claim.getWarp() == null) {
            plugin.getLocales().getLocale("claim_warp_not_set")
                    .ifPresent(executor::sendMessage);
            return;
        }
        final String oldName = claim.getWarp().getName();
        claim.setWarp(null);
        plugin.runQueued(() -> plugin.getDatabase().updateClaimWorld(world));
        plugin.getLocales().getLocale("claim_warp_unset", oldName)
                .ifPresent(executor::sendMessage);
    }

    private void commitWarp(@NotNull OnlineUser executor, @NotNull ClaimWorld world, @NotNull Claim claim,
                            @NotNull ClaimWarp warp, boolean renamed, @Nullable String oldWarpName,
                            @Nullable String oldClaimName, boolean claimNameWasAuto) {
        // The safety check ran asynchronously, so the name may have been taken meanwhile
        if (ClaimWarpNames.isTaken(plugin, claim, warp.getName())) {
            plugin.getLocales().getLocale("claim_warp_name_taken", warp.getName()).ifPresent(executor::sendMessage);
            return;
        }
        claim.setWarp(warp);
        // Setting or renaming a warp also names the claim while the claim name is
        // auto-generated, missing or in sync; a name the owner chose in the GUI is
        // never overwritten.
        final String current = claim.getMetadata().get(ClaimNaming.NAME_KEY);
        // "In sync" is the ONE shared rule (ClaimWarpNames.isInSync): plain
        // string equality would treat "My Base" + "My_Base" as NOT in sync here,
        // but as in sync in the GUI — two different behaviors for one concept.
        final int max = warpNameMaxLength();
        if (current == null || current.isBlank() || claimNameWasAuto
                || (oldWarpName != null && ClaimWarpNames.isInSync(current, oldWarpName, max))) {
            ClaimNaming.setChosenName(claim, warp.getName());
        }
        plugin.runQueued(() -> plugin.getDatabase().updateClaimWorld(world)); // ONE write after both mutations
        final Position anchor = warp.getAnchor();
        plugin.getLocales().getLocale(
                        renamed ? "claim_warp_renamed" : "claim_warp_set",
                        warp.getName(),
                        formatCoordinate(anchor.getX()), formatCoordinate(anchor.getY()),
                        formatCoordinate(anchor.getZ()),
                        anchor.getWorld().getName()
                )
                .ifPresent(executor::sendMessage);
        plugin.getLocales().getLocale("claim_warp_set_position_notice", warp.getName())
                .ifPresent(executor::sendMessage);
    }

    // Anti-trap: only accept a warp anchor if a safe landing spot exists at the anchor
    private void validateSafeAnchor(@NotNull OnlineUser executor, @NotNull ClaimWarp warp,
                                    @NotNull Runnable onSuccess) {
        // An exceptional completion (world unloaded, provider failure) counts as unsafe:
        // a silent success would anchor a warp nobody can safely use
        plugin.isSafeStandingSpot(warp.getAnchor()).whenComplete((safe, error) -> {
            if (error != null) {
                plugin.getLocales().getLocale("claim_warp_anchor_unsafe")
                        .ifPresent(executor::sendMessage);
                return;
            }
            if (safe) {
                plugin.runSync(() -> onSuccess.run());
                return;
            }
            plugin.getLocales().getLocale("claim_warp_anchor_unsafe")
                    .ifPresent(executor::sendMessage);
        });
    }

    private int warpNameMaxLength() {
        return plugin.getSettings().getClaims().getWarps().getMaxNameLength();
    }

    @NotNull
    private String formatCoordinate(double value) {
        return String.format(Locale.ENGLISH, "%.1f", value);
    }

    @Nullable
    @Override
    public List<String> suggest(@NotNull CommandUser user, @NotNull String[] args) {
        if (args.length <= 1) {
            return List.of("set", "unset", "rename", "list");
        }
        if (args[0].equalsIgnoreCase("rename") && args.length == 2) {
            return List.of("<name>");
        }
        return List.of();
    }

}
