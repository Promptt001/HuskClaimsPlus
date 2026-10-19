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

import net.william278.cloplib.operation.OperationType;
import net.william278.huskclaims.claim.Claim;
import net.william278.huskclaims.claim.ClaimWarp;
import net.william278.huskclaims.config.Settings;
import net.william278.huskclaims.gui.ClaimRef;
import net.william278.huskclaims.gui.GuiController;
import net.william278.huskclaims.gui.GuiDiagnostics;
import net.william278.huskclaims.gui.IconBuilder;
import net.william278.huskclaims.gui.GuiLayout;
import net.william278.huskclaims.gui.GuiText;
import net.william278.huskclaims.gui.model.ClickKind;
import net.william278.huskclaims.gui.model.MenuIcon;
import net.william278.huskclaims.gui.model.MenuView;
import net.william278.huskclaims.trust.TrustLevel;
import net.william278.huskclaims.user.OnlineUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The Public Permissions menu: lets a claim owner allow single actions for every player,
 * such as using doors or killing monsters, without trusting anyone.
 * <p>
 * Each setting is shown as a pair: an icon describing it, with a status block directly
 * beneath (emerald when everyone may do it, redstone when only trusted players may).
 * Clicking either half of a pair changes the setting. Pairs fill rows 1 and 3 from left to
 * right, seven to a row, and the claim warp's visibility is the last pair, so it sits in
 * line with the actions. A second page appears only if the server allows more than
 * fourteen settings.
 * <p>
 * An action can also be allowed for everyone by the claim's public trust level (set in
 * Friends &amp; Trust). Such an action shows as on, names the level, and can't be switched
 * off here, because switching off the claim's own setting would change nothing.
 *
 * @since HuskClaimsPlus 1.0.0
 */
public final class PublicPermissionsMenu implements MenuView {

    /** Permission for opening and using this menu. Granted to everyone unless revoked. */
    public static final String GUI_PERMISSION = "huskclaims.gui.public_permissions";

    public static final int ROWS = 6;
    public static final int INFO_SLOT = 4;
    public static final int PREV_SLOT = 18;
    public static final int NEXT_SLOT = 26;
    public static final int BACK_SLOT = 45;
    /** Two rows of seven pairs. */
    public static final int PAIRS_PER_PAGE = 14;

    private final GuiController c;
    private final ClaimRef ref;
    private final OnlineUser opener;

    private int page = 0;

    public PublicPermissionsMenu(@NotNull GuiController c, @NotNull ClaimRef ref,
                                 @NotNull OnlineUser opener) {
        this.c = c;
        this.ref = ref;
        this.opener = opener;
    }

    /** One icon and status pair: a public action, or the visibility of the claim's warp. */
    private sealed interface Entry permits Action, WarpVisibility {
    }

    private record Action(@NotNull OperationType op) implements Entry {
    }

    private record WarpVisibility() implements Entry {
    }

    @NotNull
    @Override
    public String typeName() {
        return "public_permissions";
    }

    @Override
    public int rows() {
        return ROWS;
    }

    /**
     * The actions server owners let players open to the public, from
     * {@code gui.public_permissions.allowed}. Unknown names are skipped and logged once each.
     */
    @NotNull
    static List<OperationType> allowedOperations(@NotNull GuiController c) {
        final List<OperationType> ops = new ArrayList<>();
        for (String name : c.getPlugin().getSettings().getGui().getPublicPermissions().getAllowed()) {
            try {
                ops.add(OperationType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                GuiDiagnostics.warnOnce(c.getPlugin(), "unknown-public-action:" + name,
                        "Unknown operation type in gui.public_permissions.allowed: " + name);
            }
        }
        return ops;
    }

    /**
     * Every pair this claim shows, in order. The warp pair is only offered on top-level claims,
     * and only while claim warps are enabled, because child claims can't have a warp.
     */
    @NotNull
    private List<Entry> entries(@Nullable Claim claim) {
        final List<Entry> entries = new ArrayList<>();
        allowedOperations(c).forEach(op -> entries.add(new Action(op)));
        final boolean warpsEnabled = c.getPlugin().getSettings().getClaims().getWarps().isEnabled();
        if (warpsEnabled && claim != null && claim.getParent().isEmpty()) {
            entries.add(new WarpVisibility());
        }
        return entries;
    }

    @NotNull
    @Override
    public java.util.Map<Integer, MenuIcon> icons() {
        final Claim claim = ref.resolve(c.getPlugin()).map(ClaimRef.Resolved::claim).orElse(null);
        final List<Entry> entries = entries(claim);
        final List<Entry> onPage = GuiLayout.page(entries, page, PAIRS_PER_PAGE);
        final java.util.Map<Integer, MenuIcon> items = new java.util.LinkedHashMap<>();

        for (int i = 0; i < onPage.size(); i++) {
            final int[] slots = GuiLayout.publicActionPair(i);
            switch (onPage.get(i)) {
                case Action(OperationType op) -> {
                    items.put(slots[0], actionIcon(op));
                    items.put(slots[1], actionStatus(op, claim));
                }
                case WarpVisibility() -> {
                    items.put(slots[0], warpIcon());
                    items.put(slots[1], warpStatus(claim != null ? claim.getWarp() : null));
                }
            }
        }

        // The header counts actions only; the warp is not an action
        final List<OperationType> actionsOnPage = new ArrayList<>();
        for (Entry entry : onPage) {
            if (entry instanceof Action(OperationType op)) {
                actionsOnPage.add(op);
            }
        }
        items.put(INFO_SLOT, c.item("minecraft:grass_block")
                .name("gui_public_permissions")
                .lore("gui_public_permissions_lore",
                        Long.toString(actionsOnPage.stream().filter(op -> isOn(op, claim)).count()),
                        Integer.toString(actionsOnPage.size()))
                .build());

        if (page > 0) {
            items.put(PREV_SLOT, c.item("minecraft:arrow").name("gui_claim_list_prev").build());
        }
        if (entries.size() > (page + 1) * PAIRS_PER_PAGE) {
            items.put(NEXT_SLOT, c.item("minecraft:arrow").name("gui_claim_list_next").build());
        }
        items.put(BACK_SLOT, c.item("minecraft:arrow")
                .name("gui_back")
                .lore("gui_back_lore")
                .build());
        return items;
    }

    @Override
    public void onClick(@NotNull OnlineUser user, int slot, @NotNull ClickKind click) {
        final Claim claim = ref.resolve(c.getPlugin()).map(ClaimRef.Resolved::claim).orElse(null);
        final List<Entry> entries = entries(claim);
        if (slot == BACK_SLOT) {
            c.openClaimSettings(user, ref);
            return;
        }
        if (slot == PREV_SLOT && page > 0) {
            page--;
            c.host().refresh(user, this);
            return;
        }
        if (slot == NEXT_SLOT && entries.size() > (page + 1) * PAIRS_PER_PAGE) {
            page++;
            c.host().refresh(user, this);
            return;
        }
        final List<Entry> onPage = GuiLayout.page(entries, page, PAIRS_PER_PAGE);
        final int index = GuiLayout.publicActionIndexForSlot(slot, onPage.size());
        if (index < 0) {
            return;
        }
        switch (onPage.get(index)) {
            case Action(OperationType op) -> toggle(user, op);
            case WarpVisibility() -> toggleWarp(user);
        }
    }

    // Public actions ----------------------------------------------------------

    /** Whether everyone may do this here, through the claim's own setting or its public trust level. */
    private boolean isOn(@NotNull OperationType op, @Nullable Claim claim) {
        return claim != null && (claim.getDefaultFlags().contains(op) || viaLevel(op, claim).isPresent());
    }

    /** The claim's public trust level, if that level already allows this action. */
    @NotNull
    private Optional<TrustLevel> viaLevel(@NotNull OperationType op, @NotNull Claim claim) {
        return claim.getTagTrustLevel(
                c.getPlugin().getSettings().getTrustTags().getPublicAccessName(), c.getPlugin())
                .filter(level -> level.getFlags().contains(op));
    }

    /** The upper half of an action's pair. Servers can change it with the icon key {@code public_actions.<action>}. */
    @NotNull
    private MenuIcon actionIcon(@NotNull OperationType op) {
        return c.item("public_actions." + op.asMinimalString(), defaultOpIcon(op))
                .name("gui_flag_" + op.asMinimalString())
                .lore("gui_flag_" + op.asMinimalString() + "_desc")
                .build();
    }

    /** The lower half of an action's pair: on, off, or on because of the public trust level. */
    @NotNull
    private MenuIcon actionStatus(@NotNull OperationType op, @Nullable Claim claim) {
        final Optional<TrustLevel> via = claim != null ? viaLevel(op, claim) : Optional.empty();
        final boolean on = isOn(op, claim);
        final IconBuilder item = c.item(on ? "minecraft:emerald_block" : "minecraft:redstone_block");
        if (via.isPresent()) {
            item.name("gui_public_perm_via", via.get().getDisplayName());
            item.lore("gui_public_perm_via_lore", via.get().getDisplayName());
        } else if (on) {
            item.name("gui_public_perm_on");
            item.lore("gui_public_perm_on_lore");
        } else {
            item.name("gui_public_perm_off");
            item.lore("gui_public_perm_off_lore");
        }
        return item.build();
    }

    private void toggle(@NotNull OnlineUser user, @NotNull OperationType op) {
        // Checked here so a confirm screen is never shown to someone who can't use it; setFlag checks again
        if (!mayToggleFlag(user, op)) {
            return;
        }
        final ClaimRef.Resolved resolved = ref.resolve(c.getPlugin()).orElse(null);
        if (resolved == null) {
            c.claimGone(user, ref);
            return;
        }
        final java.util.Set<OperationType> flags = resolved.claim().getDefaultFlags();
        if (viaLevel(op, resolved.claim()).isPresent() && !flags.contains(op)) {
            c.getPlugin().getLocales().getLocale("gui_public_perm_via_noop", op.asMinimalString())
                    .ifPresent(user::sendMessage);
            return;
        }
        final boolean enable = !flags.contains(op);
        final List<String> needsConfirm = c.getPlugin().getSettings().getGui()
                .getPublicPermissions().getConfirmOnEnable();
        if (enable && needsConfirm.contains(op.name())) {
            c.host().open(user, new ConfirmView(c, warningItem(op),
                    u -> {
                        setFlag(u, op, true);
                        // Confirm closed the inventory, so open this menu again rather than refreshing it
                        openAgain(u);
                    },
                    u -> openAgain(u)));
            return;
        }
        setFlag(user, op, enable);
    }

    /** Reopens this menu for the same claim after a confirm screen. */
    private void openAgain(@NotNull OnlineUser user) {
        c.openPublicPermissionsMenu(user, ref);
    }

    /** The confirm screen's information item: the action's icon with a warning line. */
    @NotNull
    private MenuIcon warningItem(@NotNull OperationType op) {
        return c.item("public_actions." + op.asMinimalString(), defaultOpIcon(op))
                .name("gui_flag_" + op.asMinimalString())
                .lore("gui_flag_" + op.asMinimalString() + "_desc")
                .loreBlank()
                .loreRaw(List.of(GuiText.asLore(
                        c.text("gui_public_perm_warn", op.asMinimalString()))))
                .build();
    }

    /**
     * Every check a change must pass, with a chat message for the first one that fails. It runs
     * again inside {@link #setFlag(OnlineUser, OperationType, boolean)}, so a confirm screen left
     * open can't skip it. The menu permission and the per-action permission have separate
     * messages, so the player learns which one is missing.
     */
    private boolean mayToggleFlag(@NotNull OnlineUser user, @NotNull OperationType op) {
        // The menu, or this action, may have been switched off in config.yml while the screen was open
        final Settings.GuiSettings.PublicPermissionsSettings settings =
                c.getPlugin().getSettings().getGui().getPublicPermissions();
        if (!settings.isEnabled() || !allowedOperations(c).contains(op)) {
            c.getPlugin().getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            return false;
        }
        if (!user.hasPermission(GUI_PERMISSION, true)) {
            c.getPlugin().getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            return false;
        }
        if (!user.hasPermission("huskclaims.flag." + op.asMinimalString(), true)) {
            c.getPlugin().getLocales().getLocale("error_no_permission_flag", op.asMinimalString())
                    .ifPresent(user::sendMessage);
            return false;
        }
        final ClaimRef.Resolved resolved = ref.resolve(c.getPlugin()).orElse(null);
        if (resolved == null) {
            c.claimGone(user, ref);
            return false;
        }
        if (!resolved.claim().isPrivilegeAllowed(TrustLevel.Privilege.MANAGE_OPERATION_GROUPS, user, c.getPlugin())) {
            c.getPlugin().getLocales().getLocale("no_claim_privilege").ifPresent(user::sendMessage);
            return false;
        }
        return true;
    }

    /**
     * Switches one public action on or off. The confirm screen passes the state the player asked
     * for rather than "the opposite of now", so a change made elsewhere while it was open can't
     * turn Confirm into the reverse.
     */
    private void setFlag(@NotNull OnlineUser user, @NotNull OperationType op, boolean enable) {
        if (!mayToggleFlag(user, op)) {
            return;
        }
        ref.resolve(c.getPlugin()).ifPresentOrElse(resolved -> {
            final java.util.Set<OperationType> flags = resolved.claim().getDefaultFlags();
            final boolean changed = enable ? flags.add(op) : flags.remove(op);
            if (changed) {
                c.getPlugin().runQueued(
                        () -> c.getPlugin().getDatabase().updateClaimWorld(resolved.world()));
            }
            c.getPlugin().getLocales().getLocale(
                            enable ? "gui_public_perm_enabled" : "gui_public_perm_disabled",
                            op.asMinimalString())
                    .ifPresent(user::sendMessage);
            c.host().refresh(user, this);
        }, () -> c.claimGone(user, ref));
    }

    // Claim warp visibility ---------------------------------------------------

    /** The upper half of the warp pair. Servers can change it with the icon key {@code public_actions.claim_warp}. */
    @NotNull
    private MenuIcon warpIcon() {
        return c.item("public_actions.claim_warp", "minecraft:ender_eye")
                .name("gui_public_warp_icon")
                .lore("gui_public_warp_icon_lore")
                .build();
    }

    /**
     * The lower half of the warp pair: emerald when every player may use the warp, redstone when
     * only the owner and players trusted to build may. Gray when the claim has no warp yet.
     */
    @NotNull
    private MenuIcon warpStatus(@Nullable ClaimWarp warp) {
        if (warp == null) {
            return c.item("minecraft:gray_dye")
                    .name("gui_public_warp_none")
                    .lore("gui_public_warp_none_lore")
                    .build();
        }
        final String name = GuiText.literal(warp.getName());
        final boolean everyone = warp.isPubliclyAccessible();
        return c.item(everyone ? "minecraft:emerald_block" : "minecraft:redstone_block")
                .name(everyone ? "gui_public_warp_public" : "gui_public_warp_trusted", name)
                .lore(everyone ? "gui_public_warp_public_lore" : "gui_public_warp_trusted_lore", name)
                .lore("gui_public_warp_toggle_hint")
                .build();
    }

    /** Switches the claim's warp between every player and trusted players only, and saves the claim. */
    private void toggleWarp(@NotNull OnlineUser user) {
        if (!c.getPlugin().getSettings().getGui().getPublicPermissions().isEnabled()
                || !user.hasPermission(GUI_PERMISSION, true)) {
            c.getPlugin().getLocales().getLocale("gui_error_no_permission").ifPresent(user::sendMessage);
            return;
        }
        final ClaimRef.Resolved resolved = ref.resolve(c.getPlugin()).orElse(null);
        if (resolved == null) {
            c.claimGone(user, ref);
            return;
        }
        final ClaimWarp warp = resolved.claim().getWarp();
        if (warp == null) {
            // Same hint as the Claim menu's warp button: a warp is set from where the player stands
            c.getPlugin().getLocales().getLocale("gui_claim_warp_hint").ifPresent(user::sendMessage);
            return;
        }
        if (!c.mayManageWarp(user, resolved.claim())) {
            c.getPlugin().getLocales().getLocale("no_claim_privilege").ifPresent(user::sendMessage);
            return;
        }
        final boolean everyone = !warp.isPubliclyAccessible();
        warp.setPubliclyAccessible(everyone);
        c.getPlugin().runQueued(() -> c.getPlugin().getDatabase().updateClaimWorld(resolved.world()));
        c.getPlugin().getLocales().getLocale(
                        everyone ? "gui_public_warp_enabled" : "gui_public_warp_disabled",
                        warp.getName())
                .ifPresent(user::sendMessage);
        c.host().refresh(user, this);
    }

    /**
     * The icon each action shows unless the server sets {@code public_actions.<action>}.
     * {@link OperationType} is a class of constants rather than an enum, so this compares
     * references instead of using a switch.
     */
    @NotNull
    static String defaultOpIcon(@NotNull OperationType op) {
        if (op == OperationType.PLAYER_DAMAGE_MONSTER) {
            return "minecraft:zombie_head";
        } else if (op == OperationType.PLAYER_DAMAGE_ENTITY) {
            return "minecraft:pig_spawn_egg";
        } else if (op == OperationType.PLAYER_DAMAGE_PLAYER) {
            return "minecraft:iron_sword";
        } else if (op == OperationType.BLOCK_INTERACT) {
            return "minecraft:oak_door";
        } else if (op == OperationType.REDSTONE_INTERACT) {
            return "minecraft:lever";
        } else if (op == OperationType.CONTAINER_OPEN) {
            return "minecraft:chest";
        } else if (op == OperationType.ENTITY_INTERACT) {
            return "minecraft:villager_spawn_egg";
        } else if (op == OperationType.FARM_BLOCK_INTERACT) {
            return "minecraft:wooden_hoe";
        } else if (op == OperationType.FARM_BLOCK_BREAK) {
            return "minecraft:wheat";
        } else if (op == OperationType.FARM_BLOCK_PLACE) {
            return "minecraft:wheat_seeds";
        } else if (op == OperationType.ENDER_PEARL_TELEPORT) {
            return "minecraft:ender_pearl";
        } else if (op == OperationType.PLACE_VEHICLE) {
            return "minecraft:oak_boat";
        } else if (op == OperationType.BREAK_VEHICLE) {
            return "minecraft:minecart";
        }
        return "minecraft:book";
    }

}
