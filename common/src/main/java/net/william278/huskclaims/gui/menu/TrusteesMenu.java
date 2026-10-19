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
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.GuiLayout;
import net.william278.huskclaims.gui.GuiText;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.OnlineUser;
import net.william278.huskclaims.user.User;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * "Trustees" sub-menu of the Friends &amp; Trust hub: one player head per
 * trusted user of the selected claim — <b>named, never a raw UUID</b> — plus
 * group, tag and {@code #public} entries, and an "Add player" button (name
 * prompt → level picker). The {@code #public} entry opens the hub; all other
 * user entries route through the picker for an existing trustee.
 *
 * <p>The claim is re-resolved through its {@link ClaimRef} on every render and
 * click, so a menu that outlives its claim says so instead of acting on the
 * wrong land.</p>
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class TrusteesMenu implements MenuView {

    /** Slots 0-44 hold trustee entries; one per page. */
    static final int PER_PAGE = 45;

    private final GuiController c;
    private final ClaimRef ref;
    /** Where Back goes when the hub provided one; null = smart fallback. */
    @Nullable
    private final Consumer<OnlineUser> backTo;
    private int page = 0;

    public TrusteesMenu(@NotNull GuiController c, @NotNull ClaimRef ref) {
        this(c, ref, null);
    }

    /** Trustees list with an explicit Back destination (e.g. the hub). */
    public TrusteesMenu(@NotNull GuiController c, @NotNull ClaimRef ref,
                        @Nullable Consumer<OnlineUser> backTo) {
        this.c = c;
        this.ref = ref;
        this.backTo = backTo;
    }

    @NotNull
    @Override
    public String typeName() {
        return "trustees";
    }

    @Override
    public int rows() {
        return 6; // fixed 6 rows: paging and controls assume 45 entries plus a control row
    }

    @NotNull
    @Override
    public Map<Integer, MenuIcon> icons() {
        final var cfg = settings();
        final Map<Integer, MenuIcon> items = new LinkedHashMap<>();
        final int size = rows() * 9;
        final List<TrusteeEntry> entries = trusteeEntries();
        final List<TrusteeEntry> pageEntries = GuiLayout.page(entries, page, PER_PAGE);
        int slot = 0;
        for (TrusteeEntry entry : pageEntries) {
            if (slot >= Math.min(PER_PAGE, size)) {
                break;
            }
            items.put(slot++, entry.icon(c));
        }
        if (pageEntries.isEmpty()) {
            put(items, size, cfg.getEmptySlot(), c.item("minecraft:oak_sapling")
                    .name("gui_trustees_empty")
                    .lore("gui_trustees_empty_lore").build());
        }
        if (page > 0) {
            put(items, size, cfg.getPrevSlot(), c.item("trustees.prev", "minecraft:arrow")
                    .name("gui_claim_list_prev").build());
        }
        if (GuiLayout.pageCount(entries.size(), PER_PAGE) > page + 1) {
            put(items, size, cfg.getNextSlot(), c.item("trustees.next", "minecraft:arrow")
                    .name("gui_claim_list_next").build());
        }
        put(items, size, cfg.getAddSlot(), c.item("trustees.add", "minecraft:player_head")
                .name("gui_trustee_add")
                .lore("gui_trustee_add_lore")
                .loreBlank()
                .lore("gui_trustee_add_start").build());
        put(items, size, cfg.getBackSlot(), c.item("minecraft:arrow")
                .name("gui_back").lore("gui_back_lore").build());
        return items;
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        final var cfg = settings();
        if (slot == cfg.getBackSlot()) {
            if (backTo != null) {
                backTo.accept(user);
                return;
            }
            // No explicit destination: the most useful menu this player is allowed to see
            final Optional<Claim> claim = ref.resolve(c.getPlugin()).map(ClaimRef.Resolved::claim);
            if (claim.isPresent() && c.mayManageSettings(user, claim.get())) {
                c.openClaimSettings(user, ref);
            } else if (claim.isPresent() && c.mayManageTrustees(user, claim.get())) {
                c.openTrustHub(user, ref);
            } else {
                c.openMainMenu(user);
            }
            return;
        }
        if (slot == cfg.getAddSlot()) {
            c.addTrusteePrompt(user, ref);
            return;
        }
        if (slot == cfg.getPrevSlot() && page > 0) {
            page--;
            c.host().refresh(user, this);
            return;
        }
        if (slot == cfg.getNextSlot()
                && page + 1 < GuiLayout.pageCount(trusteeEntries().size(), PER_PAGE)) {
            page++;
            c.host().refresh(user, this);
            return;
        }
        final List<TrusteeEntry> pageEntries = GuiLayout.page(trusteeEntries(), page, PER_PAGE);
        if (slot >= 0 && slot < pageEntries.size() && slot < PER_PAGE) {
            final TrusteeEntry entry = pageEntries.get(slot);
            switch (entry.kind()) {
                // The #public entry opens the hub ("Change this in Friends & Trust")
                case PUBLIC -> c.openTrustHub(user, ref);
                case USER -> c.openPickerFor(user, ref, UUID.fromString(entry.identifier()));
                // Groups/tags: managed via chat commands (hint in lore)
                case GROUP, TAG -> {
                }
            }
        }
    }

    /**
     * The name to show for a trusted player. Names change, and the claim world
     * remembers the name from when the player was trusted, so the server's own
     * records are asked first.
     */
    @NotNull
    private String userName(@NotNull net.william278.huskclaims.claim.ClaimWorld world, @NotNull UUID uuid) {
        final String serverName = c.host().cachedUserName(uuid);
        if (serverName != null && !serverName.isBlank()) {
            return serverName;
        }
        return world.getUser(uuid).map(User::getName)
                .orElseGet(() -> "Unknown (" + uuid.toString().substring(0, 8) + ")");
    }

    @NotNull
    private List<TrusteeEntry> trusteeEntries() {
        final List<TrusteeEntry> entries = new ArrayList<>();
        ref.resolve(c.getPlugin()).ifPresent(resolved -> {
            final Claim claim = resolved.claim();
            final String publicTag = c.getPlugin().getSettings().getTrustTags().getPublicAccessName();
            // Users, alphabetically by display name
            claim.getTrustedUsers().entrySet().stream()
                    .map(e -> new TrusteeEntry(TrusteeEntry.Kind.USER, e.getKey().toString(),
                            userName(resolved.world(), e.getKey()), e.getValue()))
                    .sorted(Comparator.comparing(TrusteeEntry::label))
                    .forEach(entries::add);
            // Groups, then tags (the #public tag is listed separately, below users)
            claim.getTrustedGroups().forEach((name, levelId) -> entries.add(new TrusteeEntry(
                    TrusteeEntry.Kind.GROUP, name, name, levelId)));
            claim.getTrustedTags().forEach((name, levelId) -> {
                if (name.equalsIgnoreCase(publicTag)) {
                    return; // #public rendered separately as the "Everyone (public)" entry
                }
                entries.add(new TrusteeEntry(TrusteeEntry.Kind.TAG, name, name, levelId));
            });
            // The #public tag entry: emerald block, opens the hub
            claim.getTagTrustLevel(publicTag, c.getPlugin())
                    .ifPresent(level -> entries.add(new TrusteeEntry(
                            TrusteeEntry.Kind.PUBLIC, publicTag, publicTag, level.getId())));
        });
        return entries;
    }

    private void put(@NotNull Map<Integer, MenuIcon> items, int size, int slot, @Nullable MenuIcon item) {
        if (item != null && slot >= 0 && slot < size) {
            items.put(slot, item);
        }
    }

    @NotNull
    private Settings.GuiSettings.TrusteesMenuSettings settings() {
        return c.getPlugin().getSettings().getGui().getTrusteesMenu();
    }

    /** One trustee entry: a user head, group, tag or the #public tag, keyed by its trust level id. */
    record TrusteeEntry(@NotNull Kind kind, @NotNull String identifier, @NotNull String label,
                        @NotNull String levelId) {

        /** The trusted level's display name, coloured with its configured colour. */
        @NotNull
        private String colouredLevel(@NotNull GuiController c) {
            final TrustLevel level = c.getPlugin().getTrustLevel(levelId).orElse(null);
            if (level == null) {
                return levelId;
            }
            return GuiText.fill(GuiText.color(GuiText.hex(level.getColor()) + "%1%"), level.getDisplayName());
        }

        @NotNull
        MenuIcon icon(@NotNull GuiController c) {
            return switch (kind) {
                case USER -> userHead(c);
                case GROUP -> c.item("minecraft:name_tag")
                        .name("gui_trustees_group", label)
                        .lore("gui_trustee_lore_level", colouredLevel(c))
                        .lore("gui_trustees_group_manage").build();
                case TAG -> c.item("trustees.tag", "minecraft:oak_sign")
                        .name("gui_trustees_tag", label)
                        .lore("gui_trustee_lore_level", colouredLevel(c))
                        .lore("gui_trustees_tag_manage").build();
                case PUBLIC -> c.item("minecraft:emerald_block")
                        .name("gui_trustees_public")
                        .lore("gui_trustee_lore_level", colouredLevel(c))
                        .lore("gui_trustees_public_lore").build();
            };
        }

        @NotNull
        private MenuIcon userHead(@NotNull GuiController c) {
            return c.item("minecraft:player_head")
                    .name("gui_trustees_user", label)
                    .lore("gui_trustee_lore_level", colouredLevel(c))
                    .loreBlank()
                    .lore("gui_trustee_click_change")
                    .head(UUID.fromString(identifier)).build();
        }

        enum Kind { USER, GROUP, TAG, PUBLIC }
    }

}
