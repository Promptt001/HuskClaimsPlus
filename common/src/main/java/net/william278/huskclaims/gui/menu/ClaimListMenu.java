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
import net.william278.huskclaims.claim.ClaimNaming;
import net.william278.huskclaims.claim.ClaimSizeMath;
import net.william278.huskclaims.claim.ClaimWorld;
import net.william278.huskclaims.claim.Region;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.gui.ClaimNames;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.GuiLayout;
import net.william278.huskclaims.gui.GuiText;
import net.william278.huskclaims.gui.IconBuilder;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * "My Claims": a paged grid of clickable claim icons, one per claim the player
 * owns on this server (in-memory claim worlds only — never a blocking
 * cross-server database read on the player's thread). Clicking a claim icon
 * opens Claim Settings for that claim.
 *
 * <p>The same browser doubles as the claim picker for the Friends &amp; Trust
 * hub (pick mode): the title says "Choose a claim", each claim's click line is
 * trust-flavored, Create and Sort are hidden, and a claim click opens the trust
 * hub bound to that claim instead of Claim Settings.</p>
 *
 * <p>With a parent claim given, the list shows that claim's children instead
 * of the player's top-level claims.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class ClaimListMenu implements MenuView {

    /** Claims shown per page (45 = slots 0-44 of a 6-row menu). */
    public static final int PER_PAGE = 45;

    private final GuiController controller;
    private final OnlineUser opener;
    private final Mode mode;
    @Nullable
    private final ClaimRef parent;
    private final List<Entry> entries;

    private int page = 0;
    private Sort sort = Sort.WORLD_NAME;

    public ClaimListMenu(@NotNull GuiController controller, @NotNull OnlineUser opener,
                         @Nullable ClaimRef parent) {
        this(controller, opener, parent, Mode.BROWSE);
    }

    public ClaimListMenu(@NotNull GuiController controller, @NotNull OnlineUser opener,
                         @Nullable ClaimRef parent, @NotNull Mode mode) {
        this.controller = controller;
        this.opener = opener;
        this.parent = parent;
        this.mode = mode;
        this.entries = collectClaims();
    }

    /** Browser mode: normal manage browsing, or picking a claim for the trust hub. */
    public enum Mode { BROWSE, PICK_FOR_TRUST }

    /** Sort cycle for the bottom-row button. */
    public enum Sort { WORLD_NAME, NAME, SIZE, NEWEST }

    @NotNull
    @Override
    public String typeName() {
        return "claim_list";
    }

    @Override
    public int rows() {
        return 6; // fixed 6 rows: paging and controls assume 45 entries plus a control row
    }

    @NotNull
    @Override
    public Optional<String> title() {
        if (parent != null) {
            return Optional.of(c().text("gui_children_title"));
        }
        if (mode == Mode.PICK_FOR_TRUST) {
            return Optional.of(c().text("gui_claim_list_pick_title"));
        }
        return Optional.empty(); // locale default: gui_claim_list_title
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        final Settings.GuiSettings.ClaimListMenuSettings cfg = settings();
        if (slot == cfg.getBackSlot()) {
            if (parent == null) {
                c().openMainMenu(user);
            } else {
                c().openClaimSettings(user, parent);
            }
            return;
        }
        if (slot == cfg.getPrevSlot() && page > 0) {
            page--;
            c().host().refresh(user, this);
            return;
        }
        if (slot == cfg.getNextSlot() && page + 1 < GuiLayout.pageCount(entries.size(), PER_PAGE)) {
            page++;
            c().host().refresh(user, this);
            return;
        }
        if (mode == Mode.BROWSE && slot == cfg.getSortSlot()) {
            sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
            sortEntries(entries);
            page = 0;
            c().host().refresh(user, this);
            return;
        }
        if (mode == Mode.BROWSE && slot == cfg.getCreateSlot()) {
            c().openCreateClaimMenu(user);
            return;
        }
        // Claim icons occupy slots 0..PER_PAGE-1 in page order
        final List<Entry> pageEntries = GuiLayout.page(entries, page, PER_PAGE);
        if (slot >= 0 && slot < pageEntries.size() && slot < PER_PAGE) {
            if (mode == Mode.PICK_FOR_TRUST) {
                c().openTrustHub(user, pageEntries.get(slot).ref());
            } else {
                c().openClaimSettings(user, pageEntries.get(slot).ref());
            }
        }
    }

    @NotNull
    @Override
    public Map<Integer, MenuIcon> icons() {
        final Settings.GuiSettings.ClaimListMenuSettings cfg = settings();
        final Map<Integer, MenuIcon> items = new LinkedHashMap<>();
        final int size = rows() * 9;
        final List<Entry> pageEntries = GuiLayout.page(entries, page, PER_PAGE);
        int slot = 0;
        for (Entry entry : pageEntries) {
            if (slot >= Math.min(PER_PAGE, size)) {
                break;
            }
            items.put(slot++, entry.icon(c()));
        }
        if (pageEntries.isEmpty() && cfg.getEmptySlot() >= 0 && cfg.getEmptySlot() < size) {
            items.put(cfg.getEmptySlot(), c().item("minecraft:oak_sapling")
                    .name("gui_claim_list_empty")
                    .lore("gui_claim_list_empty_lore").build());
        }
        // Bottom row controls (only when the layout has room; pick mode shows only Back + paging)
        putIfSlot(items, size, cfg.getBackSlot(), c().item("minecraft:arrow")
                .name("gui_back").lore("gui_back_lore_main").build());
        if (page > 0) {
            putIfSlot(items, size, cfg.getPrevSlot(), c().item("claim_list.prev", "minecraft:arrow")
                    .name("gui_claim_list_prev")
                    .lore("gui_claim_list_page_lore",
                            Integer.toString(page), Integer.toString(pageCount())).build());
        }
        if (GuiLayout.pageCount(entries.size(), PER_PAGE) > page + 1) {
            putIfSlot(items, size, cfg.getNextSlot(), c().item("claim_list.next", "minecraft:arrow")
                    .name("gui_claim_list_next")
                    .lore("gui_claim_list_page_lore",
                            Integer.toString(page + 2), Integer.toString(pageCount())).build());
        }
        if (mode == Mode.BROWSE) {
            putIfSlot(items, size, cfg.getCreateSlot(), createItem());
            putIfSlot(items, size, cfg.getSortSlot(), sortItem());
        }
        return items;
    }

    /* Entries ---------------------------------------------------------------- */

    /**
     * The player's claims (this server, in-memory worlds only), sorted, or the
     * children of the given parent claim. When listing top-level claims, this
     * also names the player's pre-existing unnamed claims, oldest first, the
     * first time they open My Claims.
     */
    @NotNull
    private List<Entry> collectClaims() {
        final List<Entry> entries = new ArrayList<>();
        if (parent == null) {
            backfillDefaultNames();
            // Top-level claims owned by the player across all claim worlds (in-memory only)
            for (Map.Entry<String, ClaimWorld> e : c().getPlugin().getClaimWorlds().entrySet()) {
                for (Claim claim : e.getValue().getClaimsByUser(opener.getUuid())) {
                    entries.add(toEntry(e.getKey(), claim));
                }
            }
        } else {
            // Children of the given parent claim
            parent.resolve(c().getPlugin()).ifPresent(resolved ->
                    resolved.claim().getChildren().forEach(child ->
                            entries.add(toEntry(parent.worldName(), child))));
        }
        sortEntries(entries);
        return entries;
    }

    /**
     * Assign default names to the player's unnamed top-level claims, oldest
     * first (creation time, then near-corner), persisting each dirty world
     * once. A set keyed by identity, because claim world equality compares ids
     * only, which would collapse distinct worlds that share an id.
     */
    private void backfillDefaultNames() {
        final Map<ClaimWorld, Boolean> dirty = new IdentityHashMap<>();
        c().getPlugin().getClaimWorlds().values().stream()
                .flatMap(w -> w.getClaimsByUser(opener.getUuid()).stream().map(cl -> Map.entry(w, cl)))
                .filter(e -> !e.getValue().getMetadata().containsKey(ClaimNaming.NAME_KEY))
                .sorted(Comparator
                        .comparing((Map.Entry<ClaimWorld, Claim> e) ->
                                        e.getValue().getCreationTime().orElse(null),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(e -> e.getValue().getRegion().getNearCorner().getBlockX())
                        .thenComparingInt(e -> e.getValue().getRegion().getNearCorner().getBlockZ()))
                .forEach(e -> {
                    if (ClaimNaming.assignDefaultIfMissing(c().getPlugin(), e.getValue(), opener.getName())) {
                        dirty.put(e.getKey(), true);
                    }
                });
        dirty.keySet().forEach(w -> c().getPlugin().runQueued(
                () -> c().getPlugin().getDatabase().updateClaimWorld(w)));
    }


    @NotNull
    private Entry toEntry(@NotNull String worldName, @NotNull Claim claim) {
        return new Entry(
                ClaimRef.of(worldName, claim),
                worldName,
                ClaimNames.displayName(claim, worldName, c().getPlugin()),
                claim.getRegion().getSurfaceArea(),
                claim.getCreationTime().map(Object::toString).orElse(null)
        );
    }

    private void sortEntries(@NotNull List<Entry> entries) {
        entries.sort(switch (sort) {
            case WORLD_NAME -> Comparator.comparing(Entry::worldName).thenComparing(Entry::label);
            case NAME -> Comparator.comparing(Entry::label);
            case SIZE -> Comparator.comparingLong(Entry::area).reversed();
            case NEWEST -> Comparator.comparing(Entry::creationTime,
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        });
    }

    /* Bottom-row buttons ------------------------------------------------------ */

    /** Create Claim: balance + biggest-affordable lore. */
    @NotNull
    private MenuIcon createItem() {
        final long balance = c().getPlugin().getCachedClaimBlocks(opener);
        final int maxR = Math.max(0, ClaimSizeMath.maxRadiusFor(balance));
        return c().item("claim_list.create", "minecraft:piston")
                .name("gui_claim_list_create")
                .lore("gui_claim_list_create_lore",
                        Long.toString(balance), Long.toString(ClaimSizeMath.side(maxR)))
                .build();
    }

    /** Sort cycle button: current state + all options with the ▶ marker. */
    @NotNull
    private MenuIcon sortItem() {
        final IconBuilder item = c().item("claim_list.sort", "minecraft:hopper")
                .name("gui_claim_list_sort", sortName(sort));
        for (Sort option : Sort.values()) {
            item.lore(option == sort
                    ? "gui_claim_list_sort_option_on" : "gui_claim_list_sort_option_off",
                    sortName(option));
        }
        return item.lore("gui_claim_list_sort_hint").build();
    }

    @NotNull
    private String sortName(@NotNull Sort option) {
        return c().text("gui_claim_list_sort_" + option.name().toLowerCase(Locale.ROOT));
    }

    private int pageCount() {
        return GuiLayout.pageCount(entries.size(), PER_PAGE);
    }

    private void putIfSlot(@NotNull Map<Integer, MenuIcon> items, int size, int slot,
                           @Nullable MenuIcon item) {
        if (slot >= 0 && slot < size) {
            items.put(slot, item);
        }
    }

    @NotNull
    private Settings.GuiSettings.ClaimListMenuSettings settings() {
        return c().getPlugin().getSettings().getGui().getClaimListMenu();
    }

    @NotNull
    private GuiController c() {
        return controller;
    }

    /* One grid entry: the claim ref plus cached render data -------------------- */

    /**
     * One grid entry: the claim reference plus cached render data. If the
     * claim no longer resolves when the icon renders (deleted, resized or
     * transferred while the menu was open), the icon says so and the click
     * falls through to the claim-gone path.
     */
    record Entry(@NotNull ClaimRef ref, @NotNull String worldName, @NotNull String label,
                 long area, @Nullable String creationTime) {

        @NotNull
        MenuIcon icon(@NotNull GuiController c) {
            final IconBuilder item = c.item("claim_list.claim", "minecraft:grass_block")
                    .nameLiteral(label);
            ref.resolve(c.getPlugin()).ifPresentOrElse(resolved -> {
                final Claim claim = resolved.claim();
                final Region region = claim.getRegion();
                final int width = Math.abs(region.getFarCorner().getBlockX()
                        - region.getNearCorner().getBlockX()) + 1;
                final int depth = Math.abs(region.getFarCorner().getBlockZ()
                        - region.getNearCorner().getBlockZ()) + 1;
                item.lore("gui_claim_lore_world", worldName);
                item.lore("gui_claim_lore_size",
                        Integer.toString(width), Integer.toString(depth), Long.toString(area));
                item.lore("gui_claim_lore_status", claimStatus(c, claim));
                final Optional<TrustLevel> pub = publicLevel(c, claim);
                item.lore("gui_claim_lore_public",
                        pub.map(TrustLevel::getDisplayName)
                                .orElseGet(() -> c.text("gui_claim_lore_public_none")));
                item.lore("gui_claim_lore_trusted",
                        Integer.toString(trusteeCount(claim)));
                if (claim.getWarp() != null) {
                    item.lore("gui_claim_lore_warp", GuiText.literal(claim.getWarp().getName()));
                }
                if (!claim.getChildren().isEmpty()) {
                    item.lore("gui_claim_lore_children",
                            Integer.toString(claim.getChildren().size()));
                }
                        item.loreBlank();
            }, () -> item.lore("gui_claim_gone"));
            return item.build();
        }

        private static int trusteeCount(@NotNull Claim claim) {
            return claim.getTrustedUsers().size() + claim.getTrustedGroups().size()
                    + claim.getTrustedTags().size();
        }

        @NotNull
        private static Optional<TrustLevel> publicLevel(@NotNull GuiController c, @NotNull Claim claim) {
            final var tags = c.getPlugin().getSettings().getTrustTags();
            return claim.getTagTrustLevel(tags.getPublicAccessName(), c.getPlugin());
        }

        /** Status line value: Private / Normal / Public. */
        @NotNull
        private static String claimStatus(@NotNull GuiController c, @NotNull Claim claim) {
            if (publicLevel(c, claim).isPresent()) {
                return c.text("gui_claim_status_public");
            }
            return claim.isPrivateClaim() ? c.text("gui_claim_status_private")
                    : c.text("gui_claim_status_normal");
        }
    }

}
