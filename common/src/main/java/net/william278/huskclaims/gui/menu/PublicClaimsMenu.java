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
import net.william278.huskclaims.claim.ClaimWarp;
import net.william278.huskclaims.claim.ClaimWarpNames;
import net.william278.huskclaims.claim.ClaimWarpVisits;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.GuiLayout;
import net.william278.huskclaims.gui.IconBuilder;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Public Claims": a paged grid with one slot per claim warp the player can
 * use — every <b>public</b> warp on the server, plus any <b>private</b>
 * (trusted-only) warp whose claim the opener owns or can use warps in.
 * Clicking a slot teleports the player through the same checks as
 * {@code /visit} (ban, private-claim navigation, safe-anchor re-validation),
 * re-validated at click time.
 *
 * <p>The menu is the clickable form of {@code /visits}: opening it needs both
 * the {@code /visits} and {@code /visit} commands, and while claim warps are
 * turned off neither exists, so the menu stays closed.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class PublicClaimsMenu implements MenuView {

    /** Warp slots per page (slots 0-44 of a 6-row menu). */
    public static final int PER_PAGE = 45;
    public static final int BACK_SLOT = 45;
    public static final int PREV_SLOT = 47;
    public static final int NEXT_SLOT = 51;
    public static final int EMPTY_SLOT = 22;
    public static final int ROWS = 6;

    private final GuiController c;
    private final OnlineUser opener;
    private List<Entry> entries;

    private int page = 0;

    public PublicClaimsMenu(@NotNull GuiController c, @NotNull OnlineUser opener) {
        this.c = c;
        this.opener = opener;
        this.entries = collectEntries();
    }

    @NotNull
    @Override
    public String typeName() {
        return "public_claims";
    }

    @Override
    public int rows() {
        return ROWS;
    }

    /* Entries ---------------------------------------------------------------- */

    /** Warps this player can use: all public ones + private ones in their own/trusted claims. */
    @NotNull
    private List<Entry> collectEntries() {
        final List<Entry> entries = new ArrayList<>();
        for (final Map.Entry<String, ClaimWorld> worldEntry : c.getPlugin().getClaimWorlds().entrySet()) {
            for (final Claim claim : worldEntry.getValue().getWarpClaims()) {
                final ClaimWarp warp = claim.getWarp();
                if (warp.isPubliclyAccessible() || claim.canUseWarp(opener, c.getPlugin())
                        || ClaimWarpNames.hasWarpOverride(c.getPlugin(), opener)) {
                    entries.add(new Entry(worldEntry.getKey(), worldEntry.getValue(), claim, warp));
                }
            }
        }
        entries.sort(Comparator.comparing(e -> e.warp().getName().toLowerCase(Locale.ROOT)));
        return entries;
    }

    @NotNull
    @Override
    public Map<Integer, MenuIcon> icons() {
        final Map<Integer, MenuIcon> items = new LinkedHashMap<>();
        final List<Entry> pageEntries = GuiLayout.page(entries, page, PER_PAGE);
        int slot = 0;
        for (final Entry entry : pageEntries) {
            if (slot >= PER_PAGE) {
                break;
            }
            items.put(slot++, entry.icon(c));
        }
        if (entries.isEmpty()) {
            items.put(EMPTY_SLOT, c.item("minecraft:oak_sapling")
                    .name("gui_public_claims_empty")
                    .lore("gui_public_claims_empty_lore").build());
        }
        items.put(BACK_SLOT, c.item("minecraft:arrow")
                .name("gui_back").lore("gui_back_lore_main").build());
        if (page > 0) {
            items.put(PREV_SLOT, c.item("claim_list.prev", "minecraft:arrow")
                    .name("gui_claim_list_prev")
                    .lore("gui_claim_list_page_lore",
                            Integer.toString(page), Integer.toString(pageCount())).build());
        }
        if (pageCount() > page + 1) {
            items.put(NEXT_SLOT, c.item("claim_list.next", "minecraft:arrow")
                    .name("gui_claim_list_next")
                    .lore("gui_claim_list_page_lore",
                            Integer.toString(page + 2), Integer.toString(pageCount())).build());
        }
        return items;
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        if (slot == BACK_SLOT) {
            c.openMainMenu(user);
            return;
        }
        if (slot == PREV_SLOT && page > 0) {
            page--;
            c.host().refresh(user, this);
            return;
        }
        if (slot == NEXT_SLOT && page + 1 < pageCount()) {
            page++;
            c.host().refresh(user, this);
            return;
        }
        final List<Entry> pageEntries = GuiLayout.page(entries, page, PER_PAGE);
        if (slot >= 0 && slot < pageEntries.size() && slot < PER_PAGE) {
            final Entry entry = pageEntries.get(slot);
            // The entry holds live references captured at open time — re-validate
            // at click time. A warp unset/renamed/moved or claim deleted while the
            // menu was open must not teleport to stale data.
            if (entry.claim().getWarp() != entry.warp()
                    || !entry.world().getClaims().contains(entry.claim())) {
                c.getPlugin().getLocales().getLocale("claim_warp_not_found", entry.warp().getName())
                        .ifPresent(user::sendMessage);
                entries = collectEntries(); // re-collect and re-render
                c.host().refresh(user, this);
                return;
            }
            // Everything else a visit must pass (cooldown, ban, private-claim
            // navigation, private-warp gate, anchor-inside-claim, safe standing
            // spot, teleport + stamp) runs through the same shared helper as
            // /visit — one rule set, two entry points.
            if (!c.mayUse(user, "visit")) {
                c.getPlugin().getLocales().getLocale("gui_error_no_permission")
                        .ifPresent(user::sendMessage);
                return;
            }
            ClaimWarpVisits.visit(c.getPlugin(), user, entry.world(), entry.claim());
        }
    }

    private int pageCount() {
        return GuiLayout.pageCount(entries.size(), PER_PAGE);
    }

    /* One grid entry: a claim with a usable warp, plus its world ------------ */

    record Entry(@NotNull String worldName, @NotNull ClaimWorld world,
                 @NotNull Claim claim, @NotNull ClaimWarp warp) {

        @NotNull
        MenuIcon icon(@NotNull GuiController c) {
            final String fallback = warp.isPubliclyAccessible()
                    ? "minecraft:ender_eye" : "minecraft:ender_pearl";
            final IconBuilder item = c.item("public_claims.warp", fallback)
                    .nameLiteral(warp.getName())
                    .lore("gui_public_claim_lore_owner",
                            claim.getOwnerName(world, c.getPlugin()));
            if (!warp.isPubliclyAccessible()) {
                item.lore("gui_public_claim_lore_private");
            }
            return item.loreBlank()
                    .lore("gui_public_claim_lore_click")
                    .build();
        }
    }

}
