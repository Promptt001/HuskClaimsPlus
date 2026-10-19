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
import net.william278.huskclaims.claim.ClaimWarpNames;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.config.Locales;
import net.william278.huskclaims.user.CommandUser;
import net.william278.paginedown.ListOptions;
import net.william278.paginedown.PaginatedList;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Locale;
import java.util.List;

/**
 * Paginated, clickable list of public claim warps: {@code /claimwarp list} / {@code /visits}.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public class ClaimWarpListCommand extends Command {

    private static final int WARPS_PER_PAGE = 10;

    protected ClaimWarpListCommand(@NotNull HuskClaims plugin) {
        super(List.of("visits"), "[page]", plugin);
    }

    @Override
    public void execute(@NotNull CommandUser executor, @NotNull String[] args) {
        // Collect all claims with PUBLIC warps, across all worlds, sorted by warp name
        // (trusted-only warps are usable via the GUI Public Claims menu, not listed here)
        final List<WarpEntry> warps = plugin.getClaimWorlds().entrySet().stream()
                .flatMap(entry -> entry.getValue().getPublicWarpClaims().stream()
                        .map(claim -> new WarpEntry(entry.getKey(), entry.getValue(), claim)))
                .sorted(Comparator.comparing(w -> w.claim().getWarp().getName().toLowerCase(Locale.ROOT)))
                .toList();

        if (warps.isEmpty()) {
            plugin.getLocales().getLocale("claim_warp_list_empty")
                    .ifPresent(executor::sendMessage);
            return;
        }

        executor.sendMessage(PaginatedList.of(
                warps.stream().map(this::getWarpRow).toList(),
                getListOptions(warps.size())
        ).getNearestValidPage(parseIntArg(args, 0).orElse(1)));
    }

    @NotNull
    private String getWarpRow(@NotNull WarpEntry warp) {
        final String name = warp.claim().getWarp().getName();
        final String owner = Locales.escapeText(warp.claim().getOwnerName(warp.world(), plugin));

        // The click action contains the raw name, which is only safe for names that pass
        // today's naming rules. Warps named before those rules are listed without a click.
        if (!ClaimWarpNames.isClickSafe(name)) {
            return plugin.getLocales().getRawLocale("claim_warp_list_item_plain", Locales.escapeText(name), owner)
                    .orElse(Locales.escapeText(name));
        }
        return plugin.getLocales().getRawLocale("claim_warp_list_item", Locales.escapeText(name), owner, name)
                .orElse(Locales.escapeText(name));
    }

    @NotNull
    private ListOptions getListOptions(int warpCount) {
        return plugin.getLocales().getBaseList(WARPS_PER_PAGE)
                .setHeaderFormat(plugin.getLocales().getRawLocale("claim_warp_list_header",
                        Integer.toString(warpCount)).orElse(""))
                .setItemSeparator("\n")
                .setCommand(String.format("/%s", getName()))
                .build();
    }

    /**
     * A claim with a warp, paired with its server world name and claim world
     */
    private record WarpEntry(@NotNull String serverWorld, @NotNull ClaimWorld world, @NotNull Claim claim) {
    }

}
